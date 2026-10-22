package com.darkkyoung.beddefense;

import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;

/** Server-owned bed lifecycle; the old name is kept to preserve integrations from PR #1. */
public final class SpawnBedPrototype {
    private static boolean changingBed;
    private SpawnBedPrototype(){}
    public static void init(){
        UseBlockCallback.EVENT.register(SpawnBedPrototype::onUse);
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,entity)->{
            if(!(world instanceof ServerLevel level)||!(player instanceof ServerPlayer sp)||!level.dimension().equals(Level.OVERWORLD))return true;
            UUID owner=ownerAt(level,pos,state);
            if(owner==null||BedVitalData.get(level).state(owner)!=1)return true;
            if(!owner.equals(sp.getUUID())){sp.sendSystemMessage(Component.literal("§c다른 플레이어의 스폰 침대는 회수할 수 없습니다."));return false;}
            pickup(sp,level);return false; // Controlled removal, never a vanilla bed drop.
        });
    }
    public static UUID ownerAt(ServerLevel level,BlockPos pos,BlockState state){
        if(!(state.getBlock() instanceof BedBlock))return null;
        BlockPos foot=footPosition(pos,state);
        for(var entry:SpawnBedData.get(level.getServer().overworld()).positions().entrySet())
            if(entry.getValue().equals(foot)&&BedVitalData.get(level.getServer().overworld()).state(entry.getKey())==1)return entry.getKey();
        return null;
    }
    public static boolean pickup(ServerPlayer player,ServerLevel world){
        UUID owner=player.getUUID();BedVitalData health=BedVitalData.get(world);
        BlockPos foot=SpawnBedData.get(world).getPosition(owner);
        if(foot==null||health.state(owner)!=1)return false;
        BlockState state=world.getBlockState(foot);
        if(!(state.getBlock() instanceof BedBlock))return false;
        if(!health.unplace(owner))return false;
        ItemStack item=new ItemStack(state.getBlock().asItem());
        CompoundTag tag=new CompoundTag();tag.putString("beddefense_owner",owner.toString());tag.putString("beddefense_token",health.token(owner));
        item.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));item.set(DataComponents.CUSTOM_NAME,Component.literal("§a스폰 침대"));
        // One authoritative dropped entity. Never insert directly into player inventory,
        // nor allow the vanilla two-part BedBlock to emit its own ordinary bed.
        ItemEntity drop = new ItemEntity(world,foot.getX()+0.5,foot.getY()+0.45,foot.getZ()+0.5,item);
        drop.setDefaultPickUpDelay();
        if(!world.addFreshEntity(drop)){
            // Do not lose the only unique bed if an entity-insertion hook rejects it.
            health.place(owner,health.token(owner));
            player.sendSystemMessage(Component.literal("§c스폰 침대 아이템을 떨어뜨리지 못해 회수가 취소되었습니다."));
            return false;
        }
        SpawnBedGlow.clear(world,foot);
        removeBlocks(world,foot);
        player.sendSystemMessage(Component.literal("§e스폰 침대 회수 · HP "+health.hp(owner)+" 유지 · 아이템을 바닥에 떨어뜨렸습니다."));
        // Manual pickup is a temporary unsafe state, NOT an enemy victory.
        return true;
    }
    public static boolean isPortable(ItemStack item){return item.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().contains("beddefense_token");}
    public static InteractionResult onUse(net.minecraft.world.entity.player.Player player,Level world,InteractionHand hand,BlockHitResult hit){
        if(world.isClientSide()||!(player instanceof ServerPlayer sp)||player.isSpectator())return InteractionResult.PASS;
        ServerLevel level=(ServerLevel)world,overworld=level.getServer().overworld();
        ItemStack held=player.getItemInHand(hand);
        if(isPortable(held)){
            var challenge=ChallengeData.get(overworld);
            if(!challenge.isStarted()||challenge.outcome()==ChallengeData.LOST||challenge.playerState(sp.getUUID())==ChallengeData.ELIMINATED)return InteractionResult.FAIL;
            return place(sp,level,held,hit)?InteractionResult.SUCCESS:InteractionResult.FAIL;
        }
        BlockState state=world.getBlockState(hit.getBlockPos());
        if(!(state.getBlock() instanceof BedBlock)||!ChallengeData.get(overworld).running())return InteractionResult.PASS;
        if(!world.dimension().equals(Level.OVERWORLD)){
            sp.sendSystemMessage(Component.literal("§c스폰 침대는 오버월드에서만 사용할 수 있습니다."));return InteractionResult.FAIL;
        }
        BlockPos foot=footPosition(hit.getBlockPos(),state);UUID owner=ownerAt(overworld,foot,world.getBlockState(foot));
        BedVitalData health=BedVitalData.get(overworld);SpawnBedData positions=SpawnBedData.get(overworld);
        if(owner!=null&&!owner.equals(sp.getUUID()))return denyBedChoice(sp,overworld,foot,"다른 플레이어의 스폰 침대입니다.");
        if(positions.isRegistered(sp.getUUID())){
            if(health.state(sp.getUUID())==0)health.register(sp.getUUID());
            if(health.state(sp.getUUID())!=1||!foot.equals(positions.getPosition(sp.getUUID())))
                return denyBedChoice(sp,overworld,foot,"이 침대는 사용할 수 없습니다. 지정한 스폰 침대만 사용할 수 있습니다.");
        }else{
            positions.registerFirst(sp.getUUID(),foot);health.register(sp.getUUID());
            setRespawn(sp,overworld,foot);particles(overworld,foot,true);
            RecoveryStateData.get(overworld).rememberFacing(sp.getUUID(),world.getBlockState(foot).getValue(BedBlock.FACING));
            SpawnBedGlow.ensure(overworld,foot);
            sp.sendSystemMessage(Component.literal("§a스폰 침대가 지정되었습니다. HP 100 · 에메랄드 1개로 HP 1 회복"));
        }
        setRespawn(sp,overworld,foot); // Daytime registration does not depend on vanilla sleep success.
        if(held.is(Items.EMERALD)
                &&health.repair(sp.getUUID(),ChallengeBalance.REPAIR_HP_PER_GEM)>0){
            held.shrink(1);particles(overworld,foot,true);return InteractionResult.SUCCESS;
        }
        if(RaidManager.isActive(level.getServer()))return deny(sp,"레이드 중에는 잠을 잘 수 없습니다.");
        return InteractionResult.PASS;
    }
    private static InteractionResult deny(ServerPlayer player,String message){player.sendSystemMessage(Component.literal("§c"+message));return InteractionResult.FAIL;}
    private static InteractionResult denyBedChoice(ServerPlayer player,ServerLevel world,BlockPos foot,String reason){
        // Subtle dark smoke at the rejected bed: feedback without placing or setting spawn.
        world.sendParticles(ParticleTypes.SMOKE,foot.getX()+0.5,foot.getY()+0.65,foot.getZ()+0.5,
                6,0.28,0.13,0.28,0.008);
        return deny(player,reason);
    }
    /** Only a raider's direct HP attack can play the large survival title/sound. */
    public static boolean isRaiderBedKill(String cause){return "raid".equals(cause);}
    public static boolean place(ServerPlayer player,ServerLevel world,ItemStack item,BlockHitResult hit){
        ServerLevel overworld=world.getServer().overworld();BedVitalData health=BedVitalData.get(overworld);
        CompoundTag tag=item.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        String token=tag.getStringOr("beddefense_token","");UUID owner=player.getUUID();
        if(!world.dimension().equals(Level.OVERWORLD)||!owner.toString().equals(tag.getStringOr("beddefense_owner",""))||!health.validToken(owner,token)){
            deny(player,"유효한 본인 스폰 침대만 오버월드에 재설치할 수 있습니다.");return false;
        }
        if(!(Block.byItem(item.getItem()) instanceof BedBlock block))return false;
        BlockPos foot=world.getBlockState(hit.getBlockPos()).canBeReplaced()?hit.getBlockPos():hit.getBlockPos().relative(hit.getDirection());
        Direction facing=player.getDirection();BlockPos head=foot.relative(facing);
        if(!world.isInWorldBounds(foot)||!world.isInWorldBounds(head)||!world.getWorldBorder().isWithinBounds(foot)||!world.getWorldBorder().isWithinBounds(head)
            ||!world.getBlockState(foot).canBeReplaced()||!world.getBlockState(head).canBeReplaced()
            ||!world.getBlockState(foot.below()).isSolid()||!world.getBlockState(head.below()).isSolid())return false;
        BlockState originalFoot=world.getBlockState(foot),originalHead=world.getBlockState(head);
        BlockState bed=block.defaultBlockState().setValue(BedBlock.FACING,facing).setValue(BedBlock.PART,BedPart.FOOT);
        changingBed=true;
        try{
            // No neighbor update until both halves exist. Roll back if either write is rejected.
            if(!world.setBlock(foot,bed,2)||!world.setBlock(head,bed.setValue(BedBlock.PART,BedPart.HEAD),2)){
                world.setBlock(foot,originalFoot,3);world.setBlock(head,originalHead,3);return false;
            }
            if(!health.place(owner,token)){world.setBlock(foot,originalFoot,3);world.setBlock(head,originalHead,3);return false;}
            SpawnBedData.get(overworld).relocate(owner,foot);
            RecoveryStateData.get(overworld).rememberFacing(owner,facing);
            item.shrink(1);
            world.updateNeighborsAt(foot,block);world.updateNeighborsAt(head,block);
        }finally{changingBed=false;}
        setRespawn(player,overworld,foot);particles(world,foot,true);
        SpawnBedGlow.ensure(world,foot);
        player.sendSystemMessage(Component.literal("§a스폰 침대 재설치 · HP "+health.hp(owner)));return true;
    }
    /**
     * Restores the actual unique bed at the beacon's exact original position.
     * World writes are guarded from the normal bed-destruction observer;
     * no regeneration item can be moved elsewhere or cloned.
     */
    public static boolean reviveFromBeacon(ServerPlayer player,ServerLevel world,BlockPos foot,int healthPoints){
        UUID owner=player.getUUID();
        RecoveryStateData recoveries=RecoveryStateData.get(world);
        BedVitalData health=BedVitalData.get(world);
        if(!RecoveryBeaconManager.canInteract(world,foot,owner) || !recoveries.canRecover(owner)
                || health.state(owner)!=2 || (healthPoints!=25&&healthPoints!=50))return false;
        Direction facing=recoveries.facing(owner);
        BlockPos head=foot.relative(facing);
        if(!world.isInWorldBounds(foot)||!world.isInWorldBounds(head)
                ||!world.getWorldBorder().isWithinBounds(foot)||!world.getWorldBorder().isWithinBounds(head)
                ||!world.isLoaded(head)
                ||!world.getBlockState(head).canBeReplaced()
                ||!world.getBlockState(foot.below()).isSolid()
                ||!world.getBlockState(head.below()).isSolid())return false;

        BlockState formerBeacon=world.getBlockState(foot),formerHead=world.getBlockState(head);
        BlockState redBed=Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,facing)
                .setValue(BedBlock.PART,BedPart.FOOT);
        changingBed=true;
        try {
            if(!world.setBlock(foot,redBed,2)
                    ||!world.setBlock(head,redBed.setValue(BedBlock.PART,BedPart.HEAD),2)){
                world.setBlock(foot,formerBeacon,2);
                world.setBlock(head,formerHead,2);
                return false;
            }
            if(!health.revive(owner,healthPoints)){
                world.setBlock(foot,formerBeacon,2);
                world.setBlock(head,formerHead,2);
                return false;
            }
            if(!recoveries.consumeRecovery(owner))
                throw new IllegalStateException("Recovery count changed after owner/state checks");
            world.updateNeighborsAt(foot,Blocks.RED_BED);
            world.updateNeighborsAt(head,Blocks.RED_BED);
        }finally{changingBed=false;}
        setRespawn(player,world,foot);
        particles(world,foot,true);
        SpawnBedGlow.ensure(world,foot);
        player.sendSystemMessage(Component.literal("§a스폰 침대 복구 완료! · HP "+healthPoints
                +" · 이번 도전 남은 복구 "+(RecoveryStateData.MAX_RECOVERIES-recoveries.used(owner))+"회"));
        return true;
    }

    public static void setRespawn(ServerPlayer player,ServerLevel level,BlockPos foot){
        // Force is false: regular deaths still use the actual registered bed's safe spawn rules.
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(new net.minecraft.world.level.storage.LevelData.RespawnData(
            net.minecraft.core.GlobalPos.of(Level.OVERWORLD,foot),player.getYRot(),0),false),false);
    }
    public static boolean allowedRespawn(ServerPlayer player,ServerPlayer.RespawnConfig config){
        ServerLevel world=((ServerLevel)player.level()).getServer().overworld();
        if(!ChallengeData.get(world).running()||!SpawnBedData.get(world).isRegistered(player.getUUID()))return true;
        if(config==null)return false;
        var location=config.respawnData().globalPos();
        BlockPos foot=SpawnBedData.get(world).getPosition(player.getUUID());
        if(!location.dimension().equals(Level.OVERWORLD)||BedVitalData.get(world).state(player.getUUID())!=1)return false;
        BlockState block=world.getBlockState(location.pos());
        return block.getBlock() instanceof BedBlock&&footPosition(location.pos(),block).equals(foot);
    }
    /** Called after a successful chunk mutation, including explosions, pistons and commands. */
    public static void blockChanged(ServerLevel world,BlockPos pos,BlockState old,BlockState next){
        if(changingBed||!world.dimension().equals(Level.OVERWORLD)||old==null||!(old.getBlock() instanceof BedBlock))return;
        if(old.getBlock()==next.getBlock()&&old.getValue(BedBlock.FACING)==next.getValue(BedBlock.FACING)&&old.getValue(BedBlock.PART)==next.getValue(BedBlock.PART))return;
        UUID owner=ownerAt(world,pos,old);if(owner==null)return;
        RecoveryStateData.get(world).rememberFacing(owner,old.getValue(BedBlock.FACING));
        BedVitalData.get(world).destroy(owner,"external_block_removal");
        SpawnBedGlow.clear(world,footPosition(pos,old));
        // Explosions, commands and external block changes do not use the raid-kill title.
        removeBlocks(world,footPosition(pos,old));
        RecoveryBeaconManager.ensure(world,owner);
    }
    public static int damage(MinecraftServer server,UUID owner,int amount,String cause){
        BedVitalData health=BedVitalData.get(server.overworld());int before=health.hp(owner);
        int remaining=health.damage(owner,amount,cause);
        if(before>0&&remaining==0){
            BlockPos foot=SpawnBedData.get(server.overworld()).getPosition(owner);
            if(foot!=null && server.overworld().getBlockState(foot).getBlock() instanceof BedBlock bed){
                RecoveryStateData.get(server.overworld()).rememberFacing(owner,
                        server.overworld().getBlockState(foot).getValue(BedBlock.FACING));
            }
            SpawnBedGlow.clear(server.overworld(),foot);
            removeBlocks(server.overworld(),foot);
            RecoveryBeaconManager.ensure(server.overworld(),owner);
            if(isRaiderBedKill(cause))warnOwner(server,owner);
        }
        return remaining;
    }
    private static void warnOwner(MinecraftServer server,UUID owner){ServerPlayer p=server.getPlayerList().getPlayer(owner);if(p!=null)RaidManager.survivalWarning(p,server);}
    public static void removeBlocks(ServerLevel world,BlockPos foot){
        if(foot==null)return;
        SpawnBedGlow.clear(world,foot);
        boolean previous=changingBed;changingBed=true;
        try{
            BlockState state=world.getBlockState(foot);
            if(state.getBlock() instanceof BedBlock){
                BlockPos head=foot.relative(state.getValue(BedBlock.FACING));
                // BedBlock has TWO linked halves. Remove both without neighbor
                // notifications first; otherwise an orphan half may emit a second
                // ordinary bed when a vanilla neighbor update runs mid-removal.
                if(world.getBlockState(head).getBlock() instanceof BedBlock)
                    world.setBlock(head,Blocks.AIR.defaultBlockState(),2);
                world.setBlock(foot,Blocks.AIR.defaultBlockState(),2);
                // Let surrounding blocks see the final, consistent two-air state.
                world.updateNeighborsAt(foot,Blocks.AIR);
                world.updateNeighborsAt(head,Blocks.AIR);
            }
        }finally{changingBed=previous;}
    }
    public static void tick(MinecraftServer server){
        if(server.overworld().getGameTime()%12!=0)return;
        ServerLevel world=server.overworld();
        for(var entry:SpawnBedData.get(world).positions().entrySet()){
            BlockPos foot=entry.getValue();
            if(!world.isLoaded(foot))continue;
            if(BedVitalData.get(world).state(entry.getKey())!=1){
                SpawnBedGlow.clear(world,foot);
                continue;
            }
            if(!(world.getBlockState(foot).getBlock() instanceof BedBlock)){
                BedVitalData.get(world).destroy(entry.getKey());
                SpawnBedGlow.clear(world,foot);
            }else{
                particles(world,foot,false);
                SpawnBedGlow.ensure(world,foot);
            }
        }
    }
    public static int[] status(ServerPlayer player,ServerLevel world){
        BedVitalData health=BedVitalData.get(world);UUID owner=player.getUUID();BlockPos foot=SpawnBedData.get(world).getPosition(owner);
        if(foot!=null&&health.state(owner)==0)health.register(owner);
        if(health.state(owner)==1&&foot!=null&&world.isLoaded(foot)&&!(world.getBlockState(foot).getBlock() instanceof BedBlock))health.destroy(owner);
        return new int[]{health.state(owner),health.hp(owner)};
    }
    public static boolean hasPlacedBed(ServerPlayer player,ServerLevel world){return status(player,world)[0]==1;}
    public static BlockPos registeredPosition(ServerPlayer player,ServerLevel world){return hasPlacedBed(player,world)?SpawnBedData.get(world).getPosition(player.getUUID()):null;}
    public static BlockPos footPosition(BlockPos clicked,BlockState state){return state.getValue(BedBlock.PART)==BedPart.HEAD?clicked.relative(state.getValue(BedBlock.FACING).getOpposite()):clicked;}
    public static BlockPos safeRespawn(ServerLevel world,BlockPos foot){
        for(int radius=1;radius<=4;radius++)for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)for(int y=1;y>=-1;y--){
            BlockPos pos=foot.offset(x,y,z);
            if(world.getBlockState(pos.below()).isSolid()&&world.isEmptyBlock(pos)&&world.isEmptyBlock(pos.above()))return pos;
        }
        return foot.above();
    }
    private static void particles(ServerLevel world,BlockPos foot,boolean burst){world.sendParticles(ParticleTypes.HAPPY_VILLAGER,foot.getX()+.5,foot.getY()+.75,foot.getZ()+.5,burst?12:1,.35,.12,.35,.02);}
}
