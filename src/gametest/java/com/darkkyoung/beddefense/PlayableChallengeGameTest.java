package com.darkkyoung.beddefense;

import com.mojang.serialization.JsonOps;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Runs against actual Minecraft/Fabric objects and injected events, exclusively in generated test worlds. */
public class PlayableChallengeGameTest {
    @GameTest(maxTicks=600,dimension="minecraft:overworld")
    public void startBedsRaidDeathAndDragon(GameTestHelper h){
        var server=h.getLevel().getServer();ServerLevel world=server.overworld();
        world.getDataStorage().set(ChallengeData.TYPE,new ChallengeData());
        world.getDataStorage().set(BedVitalData.TYPE,new BedVitalData());
        world.getDataStorage().set(RaidStateData.TYPE,new RaidStateData());
        ServerPlayer p=survivalPlayer(world);p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=true;
        BlockPos foot=h.absolutePos(new BlockPos(2,2,2));
        // A loaded flat arena makes spawn retries and navigation deterministic without touching a user world.
        for(int x=-24;x<=24;x++)for(int z=-24;z<=24;z++){
            BlockPos ground=foot.offset(x,-1,z);world.setBlock(ground,Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<4;y++)world.setBlock(ground.above(y+1),Blocks.AIR.defaultBlockState(),2);
        }
        p.teleportTo(world,foot.getX()+.5,foot.getY(),foot.getZ()+.5,Set.of(),0,0,false);
        ChallengeMenu.show(p,server);
        h.assertTrue(ChallengeSetupWizard.begin(server,p),"setup opens");
        ChallengeSetupWizard.setDifficulty(server,p,"어려움");ChallengeSetupWizard.setTime(server,p,true);ChallengeSetupWizard.setInterval(server,p,2);
        h.assertTrue(ChallengeOptionsData.get(world).raidStride()==2,"settings saved before countdown");
        ChallengeSetupWizard.cancel(server,p);ChallengeSetupWizard.begin(server,p);
        ChallengeSetupWizard.confirm(server,p);
        h.runAfterDelay(58,()->h.assertTrue(!ChallengeData.get(world).running(),"must not start before 60 ticks"));
        h.runAfterDelay(62,()->{
            h.assertTrue(ChallengeData.get(world).running(),"countdown actually starts world challenge");
            h.assertTrue(ChallengeOptionsData.get(world).raidStride()==2,"reopened settings retained multiplier");
            h.assertTrue(ChallengeMenu.overworldClock(server)<100,"reset selected: day one morning");
            h.assertTrue(BedVitalData.get(world).state(p.getUUID())==0,"no bed before first interaction");
            RaidManager.run(server,"time of minecraft:overworld set 12542");
            h.runAfterDelay(23,()->{
                RaidStateData raid=RaidStateData.get(world);
                h.assertTrue(raid.active()&&raid.raiders().size()==5,"daily first-night raid spawns five zombies without bed");
                bed(world,foot);use(p,world,foot);
                h.assertTrue(BedVitalData.get(world).state(p.getUUID())==1,"day/night right-click registers bed");
                h.assertTrue(p.getRespawnConfig()!=null,"real respawn config established");
                BlockPos other=foot.offset(5,0,0);bed(world,other);
                h.assertTrue(use(p,world,other)==InteractionResult.FAIL,"ordinary replacement bed blocked");
                h.assertTrue(!p.startSleeping(other),"direct ordinary sleep also blocked by server mixin");
                h.assertTrue(!p.startSleeping(foot),"raid blocks registered bed sleeping");
                SpawnBedPrototype.damage(server,p.getUUID(),37,"test");
                h.assertTrue(SpawnBedPrototype.pickup(p,world),"owner pickup succeeds");
                ItemStack portable=ItemStack.EMPTY;
                for(int i=0;i<p.getInventory().getContainerSize();i++)if(SpawnBedPrototype.isPortable(p.getInventory().getItem(i)))portable=p.getInventory().getItem(i);
                h.assertTrue(!portable.isEmpty(),"pickup gives real portable item");ItemStack duplicate=portable.copy();
                BlockPos relocated=foot.offset(0,0,5);p.setYRot(0);
                h.assertTrue(SpawnBedPrototype.place(p,world,portable,hit(relocated.below())),"portable reinstallation");
                h.assertTrue(BedVitalData.get(world).hp(p.getUUID())==63,"HP preserved across relocation");
                h.assertTrue(!SpawnBedPrototype.place(p,world,duplicate,hit(relocated.offset(4,-1,0))),"copied token rejected");
                h.assertTrue(world.getBlockState(other).is(BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:white_bed"))),"unrelated bed preserved");
                // Persist/reload codec snapshots exactly as world storage does.
                var saved=ChallengeData.CODEC.encodeStart(JsonOps.INSTANCE,ChallengeData.get(world)).getOrThrow();
                world.getDataStorage().set(ChallengeData.TYPE,ChallengeData.CODEC.parse(JsonOps.INSTANCE,saved).getOrThrow());
                var health=BedVitalData.CODEC.encodeStart(JsonOps.INSTANCE,BedVitalData.get(world)).getOrThrow();
                world.getDataStorage().set(BedVitalData.TYPE,BedVitalData.CODEC.parse(JsonOps.INSTANCE,health).getOrThrow());
                h.assertTrue(BedVitalData.get(world).hp(p.getUUID())==63,"bed persisted after reload");
                Set<String> overworldIds=raid.raiders().keySet();int survivors=raid.remaining();
                ServerLevel nether=server.getLevel(Level.NETHER);
                Mob persistent=mob(nether,"zombie",new BlockPos(0,100,0));persistent.setNoAi(true);
                raid.track(persistent.getUUID(),new RaidStateData.Raider("minecraft:the_nether",p.getUUID().toString(),1,1));
                RaidManager.run(server,"time of minecraft:overworld set 23000");
                h.runAfterDelay(2,()->{
                    h.assertTrue(BedVitalData.get(world).hp(p.getUUID())==63-survivors,"dawn penalty fixed before deaths");
                    for(String id:overworldIds)h.assertTrue(world.getEntity(UUID.fromString(id))==null,"dawn removes raiders immediately");
                    h.assertTrue(!persistent.isRemoved()&&raid.isRaider(persistent.getUUID()),"Nether mobs survive sunrise");
                    p.getAbilities().invulnerable=false;
                    Mob speed=mob(world,"zombie",relocated.offset(8,0,0));speed.setNoAi(true);
                    RaidManager.configureRaider(speed,51,1,"minecraft:zombie");
                    h.assertTrue(speed.getEffect(MobEffects.SPEED)!=null&&speed.getEffect(MobEffects.SPEED).getAmplifier()==2,"real Speed III effect");
                    raid.track(speed.getUUID(),new RaidStateData.Raider("minecraft:overworld",p.getUUID().toString(),51,2));RaidManager.onLoad(speed,world);
                    p.teleportTo(relocated.getX()+22,relocated.getY(),relocated.getZ());
                    RaidManager.moveRaiders(server,raid);h.assertTrue(RaidManager.bedTarget(speed.getUUID())!=null,"mob chooses nearer bed");
                    p.teleportTo(speed.getX()+1,speed.getY(),speed.getZ());RaidManager.moveRaiders(server,raid);
                    h.assertTrue(RaidManager.bedTarget(speed.getUUID())==null&&speed.getTarget()==p,"same mob switches to nearer player");
                    RaidManager.stop(server);
                    // Actual death callback + vanilla player replacement + Fabric AFTER_RESPAWN.
                    p.getAbilities().invulnerable=false;p.kill(world);
                    h.assertTrue(ChallengeData.get(world).playerState(p.getUUID())==ChallengeData.RESPAWNING,"placed-bed death grants normal respawn");
                    ServerPlayer respawned=server.getPlayerList().respawn(p,false,Entity.RemovalReason.KILLED);
                    h.assertTrue(ChallengeData.get(world).playerState(p.getUUID())==ChallengeData.ALIVE&&!respawned.isSpectator(),"normal respawn consumed once");
                    BedVitalData.get(world).debugHealth(respawned.getUUID(),20);
                    respawned.teleportTo(nether,0,105,0,Set.of(),0,0,false);respawned.kill(nether);
                    h.assertTrue(BedVitalData.get(world).state(respawned.getUUID())==2,"dimension death permanently destroys 20 HP bed");
                    h.assertTrue(ChallengeData.get(world).playerState(respawned.getUUID())==ChallengeData.FINAL_RESPAWN,"this death gets final respawn");
                    ChallengeLifecycle.death(respawned);h.assertTrue(ChallengeData.get(world).playerState(respawned.getUUID())==ChallengeData.FINAL_RESPAWN,"duplicate death cannot add allowance");
                    ServerPlayer last=server.getPlayerList().respawn(respawned,false,Entity.RemovalReason.KILLED);
                    h.assertTrue(last.level()==world&&!last.isSpectator(),"final respawn returns to Overworld once");
                    last.kill(world);h.assertTrue(ChallengeData.get(world).outcome()==ChallengeData.LOST,"next death actually loses");
                    ServerPlayer spectator=server.getPlayerList().respawn(last,false,Entity.RemovalReason.KILLED);
                    h.assertTrue(spectator.isSpectator(),"eliminated player cannot resume by respawning");
                    // A fresh test-only run verifies the actual dragon death event independently of prior defeat.
                    world.getDataStorage().set(ChallengeData.TYPE,new ChallengeData());
                    ChallengeData.get(world).start(world.getGameTime(),ChallengeMenu.overworldClock(server));
                    var dragon=BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:ender_dragon")).create(server.getLevel(Level.END),EntitySpawnReason.COMMAND);
                    server.getLevel(Level.END).addFreshEntity(dragon);dragon.kill(server.getLevel(Level.END));
                    h.assertTrue(ChallengeData.get(world).outcome()==ChallengeData.WON,"real Ender Dragon death wins challenge");
                    long frozen=ChallengeData.get(world).elapsedTicks(world.getGameTime());
                    h.assertTrue(ChallengeData.get(world).elapsedTicks(world.getGameTime()+2000)==frozen,"result freezes timer");
                    server.getPlayerList().remove(spectator);h.succeed();
                });
            });
        });
    }
    @GameTest(maxTicks=180,dimension="minecraft:overworld")
    public void siegeMiningUsesRealBlocks(GameTestHelper h){
        ServerLevel world=h.getLevel();BlockPos feet=h.absolutePos(new BlockPos(1,2,1));
        world.setBlock(feet.below(),Blocks.STONE.defaultBlockState(),3);world.setBlock(feet,Blocks.AIR.defaultBlockState(),3);world.setBlock(feet.above(),Blocks.AIR.defaultBlockState(),3);
        BlockPos dirt=feet.east();world.setBlock(dirt,Blocks.DIRT.defaultBlockState(),3);
        h.assertTrue(SiegeController.durationSeconds(world,dirt,1)==4,"day1 dirt = 4 seconds");
        BlockPos wood=feet.west();world.setBlock(wood,Blocks.OAK_PLANKS.defaultBlockState(),3);
        h.assertTrue(Math.abs(SiegeController.durationSeconds(world,wood,1)-9.6)<.0001,"day1 planks = 9.6 seconds");
        world.setBlock(wood,Blocks.COBBLESTONE.defaultBlockState(),3);h.assertTrue(SiegeController.durationSeconds(world,wood,1)==24,"day1 cobble = 24 seconds");
        world.setBlock(wood,Blocks.OBSIDIAN.defaultBlockState(),3);h.assertTrue(SiegeController.durationSeconds(world,wood,1)==96,"day1 obsidian = 96 seconds");
        Mob miner=mob(world,"zombie",feet);miner.setNoAi(true);miner.setNoGravity(true);
        for(int i=0;i<3;i++)SiegeController.attempt(miner,world,feet.east(4),1);
        for(int tick=20;tick<=100;tick+=20){final int age=tick;h.runAfterDelay(tick,()->{
            SiegeController.attempt(miner,world,feet.east(4),1);
            if(age<80)h.assertTrue(world.getBlockState(dirt).is(Blocks.DIRT),"must not break dirt early");
            if(age==80){h.assertTrue(world.getBlockState(dirt).isAir(),"dirt breaks after 80 server ticks");SiegeController.clear(miner.getUUID());miner.discard();h.succeed();}
        });}
    }
    private static ServerPlayer survivalPlayer(ServerLevel world){
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"R1-test-player");
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false);
        var player=new ServerPlayer(world.getServer(),world,profile,cookie.clientInformation());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        world.getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
        return player;
    }
    private static Mob mob(ServerLevel world,String name,BlockPos pos){
        Mob mob=(Mob)BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:"+name)).create(world,EntitySpawnReason.COMMAND);
        mob.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);mob.setPersistenceRequired();world.addFreshEntity(mob);return mob;
    }
    private static void bed(ServerLevel world,BlockPos foot){
        var state=BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:white_bed")).defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH);
        world.setBlock(foot,state.setValue(BedBlock.PART,BedPart.FOOT),2);world.setBlock(foot.south(),state.setValue(BedBlock.PART,BedPart.HEAD),2);
    }
    private static BlockHitResult hit(BlockPos pos){return new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);}
    private static InteractionResult use(ServerPlayer p,ServerLevel w,BlockPos pos){return SpawnBedPrototype.onUse(p,w,InteractionHand.MAIN_HAND,hit(pos));}
}
