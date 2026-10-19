package com.darkkyoung.beddefense;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;

/**
 * First bed registration + one-respawn-bed-only restriction.
 *
 * Unique portable item reinstallation and true bed respawn semantics are separate tasks.
 * Do not silently convert a different ordinary bed to a new Spawn Bed.
 */
public final class SpawnBedPrototype {
    private static int particleTimer;
    private SpawnBedPrototype() { }

    public static void init() {
        UseBlockCallback.EVENT.register(SpawnBedPrototype::onUse);
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,blockEntity) -> {
            if(world.isClientSide() || !(player instanceof ServerPlayer sp)
                    || !(state.getBlock() instanceof BedBlock)
                    || !world.dimension().equals(Level.OVERWORLD)) return true;
            BlockPos foot=footPosition(pos,state);
            if(foot.equals(SpawnBedData.get((ServerLevel)world).getPosition(sp.getUUID()))
                    && RaidManager.isActive(((ServerLevel)world).getServer())) {
                sp.sendSystemMessage(Component.literal("§c레이드 중에는 스폰 침대를 회수할 수 없습니다."));
                return false;
            }
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer sp) || world.isClientSide() || !(state.getBlock() instanceof BedBlock)
                    || !world.dimension().equals(Level.OVERWORLD)) return;
            BlockPos foot = footPosition(pos,state);
            ServerLevel overworld=(ServerLevel)world;
            SpawnBedData data=SpawnBedData.get(overworld);
            if (foot.equals(data.getPosition(sp.getUUID())) &&
                    BedVitalData.get(overworld).unplace(sp.getUUID())) {
                sp.sendSystemMessage(Component.literal("§e스폰 침대를 회수했습니다. 설치되지 않은 동안 사망하면 패배합니다."));
            }
        });
    }

    private static InteractionResult onUse(net.minecraft.world.entity.player.Player player,
                                            Level world, InteractionHand hand, BlockHitResult hit) {
        if(world.isClientSide()||hand!=InteractionHand.MAIN_HAND
                ||!(player instanceof ServerPlayer sp)||player.isSpectator()) return InteractionResult.PASS;
        if(!world.dimension().equals(Level.OVERWORLD))return InteractionResult.PASS;
        BlockState state=world.getBlockState(hit.getBlockPos());
        if(!(state.getBlock() instanceof BedBlock))return InteractionResult.PASS;
        ServerLevel overworld=(ServerLevel)world;
        if(!ChallengeData.get(overworld).isStarted())return InteractionResult.PASS;
        BlockPos foot=footPosition(hit.getBlockPos(),state);
        SpawnBedData registered=SpawnBedData.get(overworld);
        BedVitalData bed=BedVitalData.get(overworld);
        if(registered.isRegistered(sp.getUUID())) {
            // Preserve alpha.4 saves (first bed recorded before vitality data existed).
            if(bed.state(sp.getUUID())==0)bed.register(sp.getUUID());
            if(!foot.equals(registered.getPosition(sp.getUUID()))||bed.state(sp.getUUID())!=1){
                sp.sendSystemMessage(Component.literal("§c다른 일반 침대는 수면 및 리스폰 지정이 불가능합니다. 스폰 침대만 사용할 수 있습니다."));
                return InteractionResult.FAIL;
            }
            if(RaidManager.isActive(overworld.getServer()))return InteractionResult.FAIL;
            return InteractionResult.PASS;
        }
        if(registered.registerFirst(sp.getUUID(),foot)) {
            bed.register(sp.getUUID());
            sp.sendSystemMessage(Component.literal("§a스폰 침대가 지정되었습니다. §7(침대 HP·재설치·사망 규칙은 개발 중)"));
            particles(overworld,foot,true);
        }
        if(RaidManager.isActive(overworld.getServer())) {
            sp.sendSystemMessage(Component.literal("§c레이드 도중에는 잠을 잘 수 없습니다."));
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    public static void tick(MinecraftServer server) {
        if(++particleTimer<12)return;
        particleTimer=0;
        ServerLevel overworld=server.overworld();
        SpawnBedData bedPositions=SpawnBedData.get(overworld);
        BedVitalData vitals=BedVitalData.get(overworld);
        for(ServerPlayer player:server.getPlayerList().getPlayers()){
            BlockPos foot=bedPositions.getPosition(player.getUUID());
            if(foot==null||vitals.state(player.getUUID())!=1||!overworld.isLoaded(foot))continue;
            BlockState block=overworld.getBlockState(foot);
            if(block.getBlock() instanceof BedBlock&&foot.equals(footPosition(foot,block))) {
                particles(overworld,foot,false);
            }
        }
    }

    public static int[] status(ServerPlayer player, ServerLevel overworld) {
        BedVitalData vitals=BedVitalData.get(overworld);
        UUIDHolder holder=new UUIDHolder(player.getUUID());
        SpawnBedData data=SpawnBedData.get(overworld);
        if (data.isRegistered(holder.id) && vitals.state(holder.id)==0) vitals.register(holder.id);
        int state=vitals.state(holder.id);
        BlockPos pos=data.getPosition(holder.id);
        if(state==1 && pos!=null&&overworld.isLoaded(pos)&&!(overworld.getBlockState(pos).getBlock() instanceof BedBlock)) {
            // Non-player destruction (explosion/world modification): treat as permanent.
            vitals.destroy(holder.id);
            state=2;
        }
        return new int[]{state,vitals.hp(holder.id)};
    }

    public static boolean hasPlacedBed(ServerPlayer player, ServerLevel overworld) {
        return status(player,overworld)[0]==1;
    }

    public static BlockPos registeredPosition(ServerPlayer player, ServerLevel world) {
        if(!hasPlacedBed(player,world))return null;
        return SpawnBedData.get(world).getPosition(player.getUUID());
    }

    public static BlockPos footPosition(BlockPos clicked, BlockState bedState) {
        if(bedState.getValue(BedBlock.PART)==BedPart.HEAD)
            return clicked.relative(bedState.getValue(BedBlock.FACING).getOpposite());
        return clicked;
    }

    private static void particles(ServerLevel level,BlockPos foot,boolean burst) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                foot.getX()+0.5,foot.getY()+0.75,foot.getZ()+0.5,
                burst?12:1,0.35,0.12,0.35,0.02);
    }

    private record UUIDHolder(java.util.UUID id) { }
}
