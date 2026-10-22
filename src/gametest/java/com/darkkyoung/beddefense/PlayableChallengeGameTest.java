package com.darkkyoung.beddefense;

import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.world.entity.item.ItemEntity;
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
    @GameTest(maxTicks=800,dimension="minecraft:overworld")
    public void startBedsRaidDeathAndDragon(GameTestHelper h){
        var server=h.getLevel().getServer();ServerLevel world=server.overworld();
        world.getDataStorage().set(ChallengeData.TYPE,new ChallengeData());
        world.getDataStorage().set(BedVitalData.TYPE,new BedVitalData());
        world.getDataStorage().set(RaidStateData.TYPE,new RaidStateData());
        ServerPlayer p=survivalPlayer(world);p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=true;
        // Neighboring GameTests clear entities in/around their structures on completion.
        // Keep the full raid arena outside those structures so their cleanup cannot kill a raider.
        BlockPos foot=h.absolutePos(new BlockPos(258,2,258));
        for(int x=(foot.getX()-24)>>4;x<=(foot.getX()+24)>>4;x++)
            for(int z=(foot.getZ()-24)>>4;z<=(foot.getZ()+24)>>4;z++)world.setChunkForced(x,z,true);
        // A loaded flat arena makes spawn retries and navigation deterministic without touching a user world.
        for(int x=-24;x<=24;x++)for(int z=-24;z<=24;z++){
            BlockPos ground=foot.offset(x,-1,z);world.setBlock(ground,Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<4;y++)world.setBlock(ground.above(y+1),Blocks.AIR.defaultBlockState(),2);
        }
        p.teleportTo(world,foot.getX()+.5,foot.getY(),foot.getZ()+.5,Set.of(),0,0,false);
        ChallengeMenu.show(p,server);
        h.assertTrue(ChallengeSetupWizard.begin(server,p),"setup opens");
        ChallengeSetupWizard.setDifficulty(server,p,"쉬움");ChallengeSetupWizard.setTime(server,p,true);ChallengeSetupWizard.setInterval(server,p,2);
        h.assertTrue(ChallengeOptionsData.get(world).raidStride()==2,"settings saved before countdown");
        ChallengeSetupWizard.cancel(server,p);ChallengeSetupWizard.begin(server,p);
        // Inventory must survive the countdown and disappear only on successful challenge start.
        p.getInventory().add(new ItemStack(net.minecraft.world.item.Items.STONE, 17));
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(net.minecraft.world.item.Items.SHIELD));
        p.containerMenu.setCarried(new ItemStack(net.minecraft.world.item.Items.DIAMOND,3));
        h.assertTrue(ChallengeSetupWizard.confirm(server,p),"countdown confirmation starts");
        h.assertTrue(p.gameMode.getGameModeForPlayer()==GameType.ADVENTURE,
                "participant enters Adventure silently at countdown start");
        h.assertTrue(!p.getInventory().isEmpty() && !p.containerMenu.getCarried().isEmpty(),
                "inventory and cursor untouched before countdown completes");
        h.assertTrue(!ChallengeSetupWizard.begin(server,p),"cannot bypass countdown with another setup");
        h.runAfterDelay(58,()->{
            h.assertTrue(!ChallengeData.get(world).running(),"must not start before 60 ticks");
            h.assertTrue(p.gameMode.getGameModeForPlayer()==GameType.ADVENTURE,
                    "adventure lock remains active during countdown");
            h.assertTrue(!p.getInventory().isEmpty(),
                    "a still-running countdown must not wipe items");
        });
        h.runAfterDelay(62,()->{
            h.assertTrue(ChallengeData.get(world).running(),"countdown actually starts world challenge");
            h.assertTrue(p.gameMode.getGameModeForPlayer()==GameType.SURVIVAL,
                    "confirmed challenge begins in Survival");
            h.assertTrue(p.getInventory().isEmpty() && p.getOffhandItem().isEmpty()
                    && p.containerMenu.getCarried().isEmpty(),
                    "completed start clears hotbar, armor, offhand, inventory and cursor");
            // Preserve this test scenario's invulnerability fixture after mode transitions.
            p.getAbilities().invulnerable=true;
            h.assertTrue(ChallengeData.get(world).raidProfile()==ChallengeData.PROFILE_EASY,
                    "Easy acceptance scenario preserves the original five-zombie first night");
            h.assertTrue(ChallengeOptionsData.get(world).raidStride()==2,"reopened settings retained multiplier");
            h.assertTrue(ChallengeMenu.overworldClock(server)<100,"reset selected: day one morning");
            h.assertTrue(BedVitalData.get(world).state(p.getUUID())==0,"no bed before first interaction");
            RaidManager.run(server,"time of minecraft:overworld set 12542");
            waitForFirstRaid(h,world,p.getUUID(),()->{
                RaidStateData raid=RaidStateData.get(world);
                long spawned=raid.raiders().values().stream().filter(r->r.owner().equals(p.getUUID().toString())&&r.wave()==1).count();
                h.assertTrue(raid.active()&&spawned==5,"daily first-night raid: active="+raid.active()+" playerWaveSpawned="+spawned+" queued="+raid.pending()+" player="+p.position()+" arena="+foot+" difficulty="+world.getDifficulty());
                bed(world,foot);use(p,world,foot);
                h.assertTrue(BedVitalData.get(world).state(p.getUUID())==1,"day/night right-click registers bed");
                h.assertTrue(p.getRespawnConfig()!=null,"real respawn config established");
                BlockPos other=foot.offset(5,0,0);bed(world,other);
                h.assertTrue(use(p,world,other)==InteractionResult.FAIL,"ordinary replacement bed blocked");
                h.assertTrue(!p.startSleeping(other),"direct ordinary sleep also blocked by server mixin");
                h.assertTrue(!p.startSleeping(foot),"raid blocks registered bed sleeping");
                // Both precious materials restore exactly ONE HP each; iron no longer repairs.
                p.setItemInHand(InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
                SpawnBedPrototype.damage(server,p.getUUID(),1,"test");
                use(p,world,foot);
                h.assertTrue(BedVitalData.get(world).hp(p.getUUID())==99,"iron ingot must NOT repair");
                p.setItemInHand(InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD));
                use(p,world,foot);
                h.assertTrue(BedVitalData.get(world).hp(p.getUUID())==100 &&
                        p.getMainHandItem().isEmpty(),"emerald consumed for exactly 1 HP");
                SpawnBedPrototype.damage(server,p.getUUID(),1,"test");
                p.setItemInHand(InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
                use(p,world,foot);
                h.assertTrue(BedVitalData.get(world).hp(p.getUUID())==100 &&
                        p.getMainHandItem().isEmpty(),"diamond consumed for exactly 1 HP");
                SpawnBedPrototype.damage(server,p.getUUID(),37,"test");
                // Exercise the REAL Fabric before-block-break callback, not only
                // direct pickup(); both halves must disappear and spawn ONE special bed.
                int beforeInventory=portableInventoryCount(p);
                h.assertTrue(!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(
                        world,p,foot,world.getBlockState(foot),null),
                        "special-bed break must cancel vanilla block destruction");
                ItemEntity dropped=soleBedDrop(h,world,foot);
                h.assertTrue(dropped!=null,"one uniquely tagged portable bed item on ground");
                h.assertTrue(world.getBlockState(foot).isAir()
                        && world.getBlockState(foot.south()).isAir(),
                        "both bed halves removed before vanilla neighbor drops");
                h.assertTrue(portableInventoryCount(p)==beforeInventory,
                        "no unique Spawn Bed teleports directly into inventory");
                ItemStack portable=dropped.getItem().copy();
                dropped.discard(); // Simulates physically collecting the dropped item.
                h.assertTrue(!portable.isEmpty()&&SpawnBedPrototype.isPortable(portable),
                        "picked-up ground drop retains owner token and HP");
                ItemStack duplicate=portable.copy();
                BlockPos relocated=foot.offset(0,0,5);p.setYRot(0);
                h.assertTrue(SpawnBedPrototype.place(p,world,portable,hit(relocated.below())),"portable reinstallation");
                h.assertTrue(BedVitalData.get(world).hp(p.getUUID())==63,"HP preserved across relocation");
                h.assertTrue(!SpawnBedPrototype.place(p,world,duplicate,hit(relocated.offset(4,-1,0))),"copied token rejected");
                // Head-side mining must have the same one-drop behavior as foot-side mining.
                BlockPos head=relocated.relative(p.getDirection());
                h.assertTrue(!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(
                        world,p,head,world.getBlockState(head),null),
                        "bed HEAD break must cancel vanilla destruction");
                ItemEntity headDrop=soleBedDrop(h,world,relocated);
                h.assertTrue(headDrop!=null && SpawnBedPrototype.isPortable(headDrop.getItem()),
                        "breaking the head emits exactly one tagged bed and no regular duplicate");
                ItemStack secondPortable=headDrop.getItem().copy();
                headDrop.discard();
                h.assertTrue(SpawnBedPrototype.place(p,world,secondPortable,hit(relocated.below())),
                        "head-side ground drop can restore the same unique bed");
                h.assertTrue(BedVitalData.get(world).hp(p.getUUID())==63,
                        "second pickup/reinstall still preserves HP 63");
                h.assertTrue(world.getBlockState(other).is(BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:white_bed"))),"unrelated bed preserved");
                // Persist/reload codec snapshots exactly as world storage does.
                var saved=ChallengeData.CODEC.encodeStart(JsonOps.INSTANCE,ChallengeData.get(world)).getOrThrow();
                world.getDataStorage().set(ChallengeData.TYPE,ChallengeData.CODEC.parse(JsonOps.INSTANCE,saved).getOrThrow());
                var health=BedVitalData.CODEC.encodeStart(JsonOps.INSTANCE,BedVitalData.get(world)).getOrThrow();
                world.getDataStorage().set(BedVitalData.TYPE,BedVitalData.CODEC.parse(JsonOps.INSTANCE,health).getOrThrow());
                h.assertTrue(BedVitalData.get(world).hp(p.getUUID())==63,"bed persisted after reload");
                ServerLevel nether=server.getLevel(Level.NETHER);
                Mob persistent=mob(nether,"zombie",new BlockPos(0,100,0));persistent.setNoAi(true);
                raid.track(persistent.getUUID(),new RaidStateData.Raider("minecraft:the_nether",p.getUUID().toString(),1,1));
                Mob endPersistent=mob(server.getLevel(Level.END),"zombie",new BlockPos(0,100,0));endPersistent.setNoAi(true);
                raid.track(endPersistent.getUUID(),new RaidStateData.Raider("minecraft:the_end",p.getUUID().toString(),1,1));
                RaidManager.run(server,"time of minecraft:overworld set 17000");
                h.runAfterDelay(21,()->{
                    // Vanilla combat/removal may occur before dawn. Snapshot survivors at settlement,
                    // not the initial spawn list; unloaded records still count toward the penalty.
                    Set<String> overworldIds=new HashSet<>();int loaded=0;
                    for(var entry:raid.raiders().entrySet())if(entry.getValue().dimension().equals(RaidStateData.OVERWORLD)){
                        overworldIds.add(entry.getKey());Entity entity=world.getEntity(UUID.fromString(entry.getKey()));
                        if(entity!=null){loaded++;h.assertTrue(entity.isCurrentlyGlowing(),"remaining loaded raiders glow five minutes before sunrise");}
                    }
                    h.assertTrue(loaded>0,"glow check exercised actual living raiders");
                    int survivors=overworldIds.size();
                    int dawnHp=BedVitalData.get(world).hp(p.getUUID());
                    RaidManager.run(server,"time of minecraft:overworld set 23000");
                h.runAfterDelay(2,()->{
                    h.assertTrue(BedVitalData.get(world).hp(p.getUUID())==dawnHp-survivors,"dawn penalty fixed before deaths");
                    for(String id:overworldIds)h.assertTrue(world.getEntity(UUID.fromString(id))==null,"dawn removes raiders immediately");
                    h.assertTrue(!persistent.isRemoved()&&raid.isRaider(persistent.getUUID()),"Nether mobs survive sunrise");
                    h.assertTrue(!endPersistent.isRemoved()&&raid.isRaider(endPersistent.getUUID()),"End mobs survive sunrise");
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
                    ServerPlayer respawned=respawn(p);
                    h.assertTrue(ChallengeData.get(world).playerState(p.getUUID())==ChallengeData.ALIVE&&!respawned.isSpectator(),"normal respawn consumed once");
                    BedVitalData.get(world).debugHealth(respawned.getUUID(),20);
                    respawned.teleportTo(nether,0,105,0,Set.of(),0,0,false);acknowledgeLoaded(respawned);respawned.kill(nether);
                    h.assertTrue(BedVitalData.get(world).state(respawned.getUUID())==2,"dimension death: bed="+BedVitalData.get(world).state(respawned.getUUID())+" hp="+BedVitalData.get(world).hp(respawned.getUUID())+" dead="+respawned.isDeadOrDying()+" loaded="+respawned.connection.hasClientLoaded()+" participant="+ChallengeData.get(world).playerState(respawned.getUUID()));
                    h.assertTrue(ChallengeData.get(world).playerState(respawned.getUUID())==ChallengeData.FINAL_RESPAWN,"this death gets final respawn");
                    ChallengeLifecycle.death(respawned);h.assertTrue(ChallengeData.get(world).playerState(respawned.getUUID())==ChallengeData.FINAL_RESPAWN,"duplicate death cannot add allowance");
                    ServerPlayer last=respawn(respawned);
                    h.assertTrue(last.level()==world&&!last.isSpectator(),"final respawn returns to Overworld once");
                    acknowledgeLoaded(last);last.kill(world);h.assertTrue(ChallengeData.get(world).outcome()==ChallengeData.LOST,"next death actually loses");
                    ServerPlayer spectator=respawn(last);
                    h.assertTrue(spectator.isSpectator(),"eliminated player cannot resume by respawning");
                    server.getPlayerList().remove(spectator);
                    for(boolean carried:List.of(false,true)){
                        world.getDataStorage().set(ChallengeData.TYPE,new ChallengeData());
                        ChallengeData.get(world).start(world.getGameTime(),ChallengeMenu.overworldClock(server));
                        ServerPlayer unsafe=survivalPlayer(world);unsafe.setGameMode(GameType.SURVIVAL);ChallengeData.get(world).join(unsafe.getUUID());
                        if(carried){BlockPos temp=foot.offset(12,0,0);bed(world,temp);use(unsafe,world,temp);SpawnBedPrototype.pickup(unsafe,world);}
                        unsafe.kill(world);h.assertTrue(ChallengeData.get(world).outcome()==ChallengeData.LOST,"missing/carried bed death loses on real event");
                        ServerPlayer out=respawn(unsafe);h.assertTrue(out.isSpectator(),"unsafe death cannot respawn into survival");server.getPlayerList().remove(out);
                    }
                    // A fresh test-only run verifies the actual dragon death event independently of prior defeat.
                    world.getDataStorage().set(ChallengeData.TYPE,new ChallengeData());
                    ChallengeData.get(world).start(world.getGameTime(),ChallengeMenu.overworldClock(server));
                    var dragon=BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:ender_dragon")).create(server.getLevel(Level.END),EntitySpawnReason.COMMAND);
                    server.getLevel(Level.END).addFreshEntity(dragon);dragon.kill(server.getLevel(Level.END));
                    h.assertTrue(ChallengeData.get(world).outcome()==ChallengeData.WON,"real Ender Dragon death wins challenge");
                    long frozen=ChallengeData.get(world).elapsedTicks(world.getGameTime());
                    h.assertTrue(ChallengeData.get(world).elapsedTicks(world.getGameTime()+2000)==frozen,"result freezes timer");
                    // A completed run can be restarted safely in the same world.
                    h.assertTrue(ChallengeLifecycle.resetFinishedRun(server),"completed run can reset on explicit confirmation");
                    h.assertTrue(!ChallengeData.get(world).isStarted(),"old outcome and timer cleared");
                    h.assertTrue(RaidStateData.get(world).lastRaidDay()==0,"old raid day no longer blocks day-one waves");
                    h.assertTrue(SpawnBedData.get(world).positions().isEmpty(),"old owner-bed positions cleared");
                    h.assertTrue(ChallengeData.get(world).start(world.getGameTime(),ChallengeMenu.overworldClock(server)),
                        "new run begins again at day 1");
                    h.assertTrue(ChallengeData.get(world).day(ChallengeMenu.overworldClock(server))==1,
                        "new challenge starts at first day");
                    // Serialize this test after sunrise/raid state checks because the
                    // generated GameTests share authoritative Overworld saved data.
                    raidZombiesAndHusksStayTrackedUnderWater(h);
                });
                });
            },200);
        });
    }
    private static int portableInventoryCount(ServerPlayer player) {
        int amount=0;
        for(int i=0;i<player.getInventory().getContainerSize();i++)
            if(SpawnBedPrototype.isPortable(player.getInventory().getItem(i)))
                amount+=player.getInventory().getItem(i).getCount();
        return amount;
    }

    private static ItemEntity soleBedDrop(GameTestHelper test,ServerLevel world,BlockPos position){
        ItemEntity single=null;
        int all=0;
        for(Entity entity:world.getAllEntities()){
            if(entity instanceof ItemEntity drop && drop.getItem().is(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:white_bed")))
                && entity.distanceToSqr(position.getX()+0.5,position.getY()+0.5,position.getZ()+0.5)<9){
                all++;
                if(SpawnBedPrototype.isPortable(drop.getItem()))single=drop;
            }
        }
        test.assertTrue(all==1,"exactly ONE bed item in the world, no vanilla duplicate: "+all);
        return single;
    }

    private static void waitForFirstRaid(GameTestHelper h,ServerLevel world,UUID owner,Runnable assertions,int remainingTicks){
        // Count only this test player's first-night raiders: independent GameTests
        // may intentionally add other tracked mobs to the same world ledger.
        var raid=RaidStateData.get(world);
        long playerWave=raid.raiders().values().stream()
                .filter(r->r.owner().equals(owner.toString())&&r.wave()==1).count();
        if(playerWave==5||remainingTicks<=0){assertions.run();return;}
        h.runAfterDelay(10,()->waitForFirstRaid(h,world,owner,assertions,remainingTicks-10));
    }
    private void raidZombiesAndHusksStayTrackedUnderWater(GameTestHelper h){
        ServerLevel world=h.getLevel();
        RaidStateData raid=RaidStateData.get(world.getServer().overworld());
        BlockPos raidZombieSpot=h.absolutePos(new BlockPos(1,2,1));
        BlockPos raidHuskSpot=h.absolutePos(new BlockPos(5,2,1));
        BlockPos wildZombieSpot=h.absolutePos(new BlockPos(9,2,1));
        waterCell(world,raidZombieSpot);
        waterCell(world,raidHuskSpot);
        waterCell(world,wildZombieSpot);

        var raidZombie=(net.minecraft.world.entity.monster.zombie.Zombie)
                mob(world,"zombie",raidZombieSpot);
        var raidHusk=(net.minecraft.world.entity.monster.zombie.Zombie)
                mob(world,"husk",raidHuskSpot);
        var wildZombie=(net.minecraft.world.entity.monster.zombie.Zombie)
                mob(world,"zombie",wildZombieSpot);
        for(var z:java.util.List.of(raidZombie,raidHusk,wildZombie)){
            z.setNoGravity(true);
        }
        String owner=UUID.randomUUID().toString();
        raid.track(raidZombie.getUUID(),new RaidStateData.Raider("minecraft:overworld",owner,1,1));
        raid.track(raidHusk.getUUID(),new RaidStateData.Raider("minecraft:overworld",owner,1,1));
        h.assertTrue(RaidManager.isTrackedRaider(raidZombie) &&
                RaidManager.isTrackedRaider(raidHusk) && !RaidManager.isTrackedRaider(wildZombie),
                "only actual raid UUIDs are protected from water conversion");
        h.assertTrue(!convertsInWater(raidZombie) && !convertsInWater(raidHusk),
                "tracked Zombie and overridden Husk vanilla conversion predicates are disabled");
        h.assertTrue(convertsInWater(wildZombie),
                "vanilla untracked Zombie is still allowed to convert in water");

        // Advance the conversion conditions while the entities remain submerged.
        // The wild control uses an actual reflected vanilla predicate; a forced
        // conversion-time setter alone does not guarantee transformation in a
        // truncated GameTest window (the ConversionTracker manages both stages).
        raidZombie.setInWaterTime(599);
        raidHusk.setInWaterTime(599);
        UUID zombieId=raidZombie.getUUID(),huskId=raidHusk.getUUID();
        h.runAfterDelay(120,()->{
            h.assertTrue(world.getEntity(zombieId)==raidZombie && !raidZombie.isRemoved()
                            && raid.isRaider(zombieId),
                    "underwater raid zombie must keep its original entity and UUID");
            h.assertTrue(world.getEntity(huskId)==raidHusk && !raidHusk.isRemoved()
                            && raid.isRaider(huskId),
                    "underwater raid husk must keep its original entity and UUID");
            h.assertTrue(!raidZombie.isUnderWaterConverting() &&
                            !raidHusk.isUnderWaterConverting(),
                    "raid undead must never enter vanilla water-conversion countdown");
            h.assertTrue(convertsInWater(wildZombie) && !RaidManager.isTrackedRaider(wildZombie),
                    "untracked vanilla zombie retains its normal conversion eligibility");
            raid.remove(zombieId);raid.remove(huskId);
            raidZombie.discard();raidHusk.discard();wildZombie.discard();
            testRecoveryBeaconLifecycle(h);
        });
    }

    /** Serialized after real raid/dawn tests to avoid overwriting shared saved data. */
    private void testRecoveryBeaconLifecycle(GameTestHelper h){
        ServerLevel world=h.getLevel(),overworld=world.getServer().overworld();
        ServerPlayer player=survivalPlayer(world);
        player.setGameMode(GameType.SURVIVAL);
        ChallengeData.get(overworld).join(player.getUUID());
        BlockPos foot=h.absolutePos(new BlockPos(270,2,270));
        world.setChunkForced(foot.getX()>>4,foot.getZ()>>4,true);
        world.setBlock(foot.below(),Blocks.STONE.defaultBlockState(),3);
        world.setBlock(foot.south().below(),Blocks.STONE.defaultBlockState(),3);
        world.setBlock(foot,Blocks.AIR.defaultBlockState(),3);
        world.setBlock(foot.south(),Blocks.AIR.defaultBlockState(),3);
        bed(world,foot);
        use(player,world,foot);
        h.assertTrue(BedVitalData.get(world).hp(player.getUUID())==100,"initial bed HP100");
        SpawnBedPrototype.damage(world.getServer(),player.getUUID(),100,"raid");
        h.assertTrue(RecoveryBeaconRegistry.isRecoveryBlock(world,foot),
                "one custom recovery beacon replaces the destroyed bed FOOT");
        h.assertTrue(world.getBlockState(foot.south()).isAir(),
                "beacon is a single block without extra visible supports");
        h.assertTrue(world.getBlockEntity(foot) instanceof RecoveryBeaconBlockEntity be
                        && !be.getBeamSections().isEmpty(),
                "real beacon beam renderer has a section despite NO pyramid");
        h.assertTrue(RecoveryStateData.get(world).used(player.getUUID())==0,"zero recoveries consumed before payment");
        h.assertTrue(RecoveryBeaconManager.open(player,world,foot),"owner can use recovery signal");
        h.assertTrue(player.containerMenu instanceof RecoveryDispenserMenu,"actual 3x3 deposit menu opens");
        RecoveryDispenserMenu temporary=(RecoveryDispenserMenu)player.containerMenu;
        temporary.getSlot(0).set(new ItemStack(net.minecraft.world.item.Items.EMERALD));
        player.closeContainer();
        h.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD)>=1,
                "closing incomplete recovery returns deposited items instead of consuming them");
        h.assertTrue(BedVitalData.get(world).state(player.getUUID())==2,"closing incomplete recipe does not revive bed");

        h.assertTrue(RecoveryBeaconManager.open(player,world,foot),"first recovery menu reopens");
        RecoveryDispenserMenu first=(RecoveryDispenserMenu)player.containerMenu;
        deposit(first,false);
        h.runAfterDelay(15,()->{
            h.assertTrue(BedVitalData.get(world).hp(player.getUUID())==25,
                    "first recovery returns HP25, not free HP100");
            h.assertTrue(RecoveryStateData.get(world).used(player.getUUID())==1,
                    "first recovery consumed exactly one durable allowance");
            h.assertTrue(!RecoveryBeaconRegistry.isRecoveryBlock(world,foot)
                            &&world.getBlockState(foot).getBlock() instanceof BedBlock,
                    "beacon disappears and bed is built in its old location");
            h.assertTrue(player.getRespawnConfig()!=null,"restoration writes actual Overworld respawn");
            SpawnBedPrototype.damage(world.getServer(),player.getUUID(),25,"raid");
            h.assertTrue(RecoveryBeaconRegistry.isRecoveryBlock(world,foot),
                    "beacon returns after second destruction");
            h.assertTrue(RecoveryBeaconManager.open(player,world,foot),"second recovery opens");
            RecoveryDispenserMenu second=(RecoveryDispenserMenu)player.containerMenu;
            deposit(second,true);
            h.runAfterDelay(15,()->{
                h.assertTrue(BedVitalData.get(world).hp(player.getUUID())==50,"second recovery HP50");
                h.assertTrue(RecoveryStateData.get(world).used(player.getUUID())==2,"exactly two regenerations consumed");
                SpawnBedPrototype.damage(world.getServer(),player.getUUID(),50,"raid");
                h.assertTrue(!RecoveryBeaconRegistry.isRecoveryBlock(world,foot),
                        "third permanent destruction never creates a recovery beacon");
                h.assertTrue(!RecoveryStateData.get(world).canRecover(player.getUUID()));
                player.closeContainer();
                world.getServer().getPlayerList().remove(player);
                h.succeed();
            });
        });
    }
    private static void deposit(RecoveryDispenserMenu menu,boolean block){
        for(int i=0;i<9;i++)
            menu.getSlot(i).set(new ItemStack(i==4?net.minecraft.world.item.Items.RED_BED:
                    block?net.minecraft.world.item.Items.EMERALD_BLOCK:
                          net.minecraft.world.item.Items.EMERALD));
    }

    private static boolean convertsInWater(net.minecraft.world.entity.monster.zombie.Zombie entity){
        try {
            Class<?> owner=entity instanceof net.minecraft.world.entity.monster.zombie.Husk
                    ?net.minecraft.world.entity.monster.zombie.Husk.class
                    :net.minecraft.world.entity.monster.zombie.Zombie.class;
            var method=owner.getDeclaredMethod("convertsInWater");
            method.setAccessible(true);
            return (boolean)method.invoke(entity);
        } catch(ReflectiveOperationException exception) {
            throw new AssertionError("Could not inspect the actual Minecraft 26.3 zombie conversion",exception);
        }
    }

    private static void waterCell(ServerLevel world,BlockPos center){
        world.setBlock(center.below(),Blocks.GLASS.defaultBlockState(),3);
        for(int dy=0;dy<3;dy++){
            for(Direction facing:Direction.Plane.HORIZONTAL)
                world.setBlock(center.above(dy).relative(facing),
                        Blocks.GLASS.defaultBlockState(),3);
            world.setBlock(center.above(dy),Blocks.WATER.defaultBlockState(),3);
        }
        world.setBlock(center.above(3),Blocks.GLASS.defaultBlockState(),3);
    }

    @GameTest(maxTicks=120,dimension="minecraft:overworld")
    public void hardRaidStage10BabySpeedAndMining(GameTestHelper h){
        ServerLevel world=h.getLevel();
        BlockPos feet=h.absolutePos(new BlockPos(1,2,1));
        world.setBlock(feet.below(),Blocks.STONE.defaultBlockState(),3);
        Mob baby=mob(world,"zombie",feet);
        RaidManager.configureRaider(baby,10,1,HardRaidBalance.BABY_ZOMBIE,true);
        h.assertTrue(baby instanceof net.minecraft.world.entity.monster.zombie.Zombie zombie && zombie.isBaby(),
                "hard day 10 baby zombie must have actual baby hitbox");
        h.assertTrue(baby.getEffect(MobEffects.SPEED)==null,
                "baby zombies must retain high natural speed without stacking Speed III");

        int index=-1;
        for(int i=0;i<58;i++){
            if(HardRaidBalance.speedAmplifier("minecraft:zombie",10,i)==2){index=i;break;}
        }
        h.assertTrue(index>=0,"day 10 hard stage has Speed III eligible mobs");
        Mob fast=mob(world,"zombie",feet.west(2));
        RaidManager.configureRaider(fast,10,index,"minecraft:zombie",true);
        h.assertTrue(fast.getEffect(MobEffects.SPEED)!=null &&
                fast.getEffect(MobEffects.SPEED).getAmplifier()==2,
                "hard day 10 Speed III is applied to actual raiders");

        BlockPos target=feet.east(3);
        world.setBlock(target,Blocks.DIRT.defaultBlockState(),3);
        h.assertTrue(SiegeController.durationSeconds(world,target,1,ChallengeData.PROFILE_EASY)==2.0,
                "easy mining is 50 percent faster than previous 4 second baseline");
        h.assertTrue(SiegeController.durationSeconds(world,target,1,ChallengeData.PROFILE_HARD)==0.5,
                "hard dirt mining takes half a second");
        world.setBlock(target,Blocks.OBSIDIAN.defaultBlockState(),3);
        h.assertTrue(SiegeController.durationSeconds(world,target,1,ChallengeData.PROFILE_HARD)==10.0,
                "existing alpha.7 hard-save obsidian still takes 10 seconds");
        h.assertTrue(SiegeController.durationSeconds(world,target,1,ChallengeData.PROFILE_HARD_V2)==20.0,
                "balanced hard obsidian takes 20 seconds");
        world.setBlock(target,Blocks.DIRT.defaultBlockState(),3);
        h.assertTrue(SiegeController.durationSeconds(world,target,1,ChallengeData.PROFILE_HARD_V2)==1.0,
                "balanced hard dirt takes 1 second");
        world.setBlock(target,Blocks.OAK_PLANKS.defaultBlockState(),3);
        h.assertTrue(SiegeController.durationSeconds(world,target,1,ChallengeData.PROFILE_HARD_V2)==2.0,
                "balanced hard planks take 2 seconds");
        world.setBlock(target,Blocks.COBBLESTONE.defaultBlockState(),3);
        h.assertTrue(SiegeController.durationSeconds(world,target,1,ChallengeData.PROFILE_HARD_V2)==5.0,
                "balanced hard cobble takes 5 seconds");
        baby.discard();fast.discard();h.succeed();
    }
    @GameTest(maxTicks=120,dimension="minecraft:overworld")
    public void normalV1RealBabySpeedAndIntermediateMining(GameTestHelper h){
        ServerLevel world=h.getLevel();
        BlockPos feet=h.absolutePos(new BlockPos(1,2,1));
        world.setBlock(feet.below(),Blocks.STONE.defaultBlockState(),3);
        Mob baby=mob(world,"zombie",feet);
        RaidManager.configureRaider(baby,20,1,HardRaidBalance.BABY_ZOMBIE,false,
                NormalRaidBalance.maxForDay(20),true);
        h.assertTrue(baby instanceof net.minecraft.world.entity.monster.zombie.Zombie zombie&&zombie.isBaby(),
                "Normal day-20 baby zombie must have actual smaller hitbox");
        h.assertTrue(baby.getEffect(MobEffects.SPEED)==null,
                "Normal baby zombie never gets a stacking Speed-III potion");
        int index=-1;
        for(int i=0;i<NormalRaidBalance.maxForDay(25);i++)
            if(NormalRaidBalance.speedAmplifier("minecraft:zombie",25,i)==2){index=i;break;}
        h.assertTrue(index>=0,"Normal begins eligible Speed III at effective roster day 25");
        Mob fast=mob(world,"zombie",feet.west(2));
        RaidManager.configureRaider(fast,25,index,"minecraft:zombie",false,
                NormalRaidBalance.maxForDay(25),true);
        h.assertTrue(fast.getEffect(MobEffects.SPEED)!=null
                &&fast.getEffect(MobEffects.SPEED).getAmplifier()==2,
                "Normal Speed III is an actual server-side mob effect");
        BlockPos target=feet.east(3);
        world.setBlock(target,Blocks.DIRT.defaultBlockState(),3);
        h.assertTrue(SiegeController.durationSeconds(world,target,1,ChallengeData.PROFILE_NORMAL_V2)==1.5,
                "Normal first-band dirt takes 1.5s");
        world.setBlock(target,Blocks.OAK_PLANKS.defaultBlockState(),3);
        h.assertTrue(Math.abs(SiegeController.durationSeconds(world,target,1,ChallengeData.PROFILE_NORMAL_V2)-3.4)<.0001,
                "Normal first-band planks take 3.4s");
        world.setBlock(target,Blocks.COBBLESTONE.defaultBlockState(),3);
        h.assertTrue(SiegeController.durationSeconds(world,target,1,ChallengeData.PROFILE_NORMAL_V2)==8.5,
                "Normal first-band cobble takes 8.5s");
        world.setBlock(target,Blocks.OBSIDIAN.defaultBlockState(),3);
        h.assertTrue(SiegeController.durationSeconds(world,target,1,ChallengeData.PROFILE_NORMAL_V2)==34.0,
                "Normal first-band obsidian takes 34s");
        h.assertTrue(SiegeController.durationSeconds(world,target,1,ChallengeData.PROFILE_LEGACY)==96.0,
                "Existing active pre-update Normal retains original 96s mining");
        baby.discard();fast.discard();h.succeed();
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
    @GameTest(dimension="minecraft:overworld")
    public void externalBedReplacementIsPermanent(GameTestHelper h){
        ServerLevel world=h.getLevel();UUID owner=UUID.randomUUID();BlockPos foot=h.absolutePos(new BlockPos(1,2,1));
        world.setBlock(foot.below(),Blocks.STONE.defaultBlockState(),2);world.setBlock(foot.south().below(),Blocks.STONE.defaultBlockState(),2);bed(world,foot);
        SpawnBedData.get(world).registerFirst(owner,foot);BedVitalData.get(world).register(owner);
        world.setBlock(foot.south(),Blocks.AIR.defaultBlockState(),3);bed(world,foot);
        h.assertTrue(BedVitalData.get(world).state(owner)==2&&BedVitalData.get(world).hp(owner)==0,"breaking head and replacing ordinary bed cannot revive it");
        h.succeed();
    }
    private static ServerPlayer respawn(ServerPlayer old){
        old.connection.handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
            net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        ServerPlayer replacement=old.connection.player;acknowledgeLoaded(replacement);return replacement;
    }
    private static void acknowledgeLoaded(ServerPlayer player){
        player.hasChangedDimension();
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
    }
    private static ServerPlayer survivalPlayer(ServerLevel world){
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"R1-test-player");
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false);
        var player=new ServerPlayer(world.getServer(),world,profile,cookie.clientInformation());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        world.getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
        acknowledgeLoaded(player);return player;
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
