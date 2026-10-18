package com.darkkyoung.beddefense;

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

/** First-bed registration and green visual marker. No custom Bed item/HP/respawn logic yet. */
public final class SpawnBedPrototype {
    private static int particleTimer;
    private SpawnBedPrototype() {}

    public static void init() {
        UseBlockCallback.EVENT.register(SpawnBedPrototype::onUse);
    }

    private static InteractionResult onUse(net.minecraft.world.entity.player.Player player,
                                            Level world, InteractionHand hand, BlockHitResult hit) {
        if (world.isClientSide() || hand != InteractionHand.MAIN_HAND
                || !(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) {
            return InteractionResult.PASS;
        }
        if (!world.dimension().equals(Level.OVERWORLD)) {
            return InteractionResult.PASS;
        }
        BlockPos clicked = hit.getBlockPos();
        BlockState state = world.getBlockState(clicked);
        if (!(state.getBlock() instanceof BedBlock)) {
            return InteractionResult.PASS;
        }
        BlockPos foot = footPosition(clicked, state);
        // Beds only enter the challenge after the explicit [게임 시작] click.
        if (!ChallengeData.get((ServerLevel) world).isStarted()) return InteractionResult.PASS;
        SpawnBedData data = SpawnBedData.get((ServerLevel) world);
        if (data.registerFirst(serverPlayer.getUUID(), foot)) {
            serverPlayer.sendSystemMessage(Component.literal("§a스폰 침대가 등록되었습니다! §7(HP·리스폰 규칙은 개발 중)"));
            particles((ServerLevel) world, foot, true);
        }
        return InteractionResult.PASS;
    }

    public static void tick(MinecraftServer server) {
        if (++particleTimer < 12) {
            return;
        }
        particleTimer = 0;
        ServerLevel overworld = server.overworld();
        SpawnBedData data = SpawnBedData.get(overworld);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            BlockPos foot = data.getPosition(player.getUUID());
            if (foot != null && overworld.isLoaded(foot)
                    && overworld.getBlockState(foot).getBlock() instanceof BedBlock
                    && foot.equals(footPosition(foot, overworld.getBlockState(foot)))) {
                particles(overworld, foot, false);
            }
        }
    }

    public static boolean hasPlacedBed(ServerPlayer player, ServerLevel world) {
        BlockPos foot = SpawnBedData.get(world).getPosition(player.getUUID());
        // Unloaded chunks are unknown, not proof that the registered bed was destroyed.
        return foot != null && (!world.isLoaded(foot)
                || world.getBlockState(foot).getBlock() instanceof BedBlock);
    }

    private static BlockPos footPosition(BlockPos clicked, BlockState bedState) {
        if (bedState.getValue(BedBlock.PART) == BedPart.HEAD) {
            return clicked.relative(bedState.getValue(BedBlock.FACING).getOpposite());
        }
        return clicked;
    }

    private static void particles(ServerLevel level, BlockPos foot, boolean burst) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                foot.getX() + 0.5, foot.getY() + 0.75, foot.getZ() + 0.5,
                burst ? 12 : 1, 0.35, 0.12, 0.35, 0.02);
    }
}
