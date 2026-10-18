package com.darkkyoung.beddefense;

import com.darkkyoung.beddefense.network.ClockSyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 26.3 technical prototype: server Overworld time sync, cobblestone cracks.
 * Actual game raids / Spawn Bed rules will be implemented in later phases.
 */
public final class BedDefense implements ModInitializer {
    public static final String MOD_ID = "beddefense";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static int hudClockSyncCounter;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(ClockSyncPayload.TYPE, ClockSyncPayload.CODEC);

        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                dispatcher.register(Commands.literal("beddefense")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("[Bed Defense] 26.3 HUD prototype. /bdhud preview (client); /beddefense debug cracks (server)."),
                                    false
                            );
                            return 1;
                        })
                        .then(Commands.literal("debug")
                                .then(Commands.literal("cracks")
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            HitResult hit = player.pick(6.0D, 0.0F, false);
                                            if (hit.getType() != HitResult.Type.BLOCK) {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("Look at a cobblestone block within 6 blocks.")
                                                );
                                                return 0;
                                            }

                                            BlockPos target = ((BlockHitResult) hit).getBlockPos();
                                            ServerLevel level = (ServerLevel) player.level();
                                            if (!level.getBlockState(target).is(Blocks.COBBLESTONE)) {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("Only cobblestone is breakable in this safe debug test.")
                                                );
                                                return 0;
                                            }

                                            if (!BlockCrackDemo.begin(level, target)) {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("This block is already being broken in the demo.")
                                                );
                                                return 0;
                                            }
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Bed Defense: native break cracks 0-9 (4 seconds) started!"),
                                                    false
                                            );
                                            return 1;
                                        })))
                )
        );

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            BlockCrackDemo.tick(server);
            if (++hudClockSyncCounter < 20) {
                return;
            }
            hudClockSyncCounter = 0;
            long overworldTime = server.overworld().getDayTime();
            ClockSyncPayload payload = new ClockSyncPayload(overworldTime);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (ServerPlayNetworking.canSend(player, ClockSyncPayload.TYPE)) {
                    ServerPlayNetworking.send(player, payload);
                }
            }
        });
        LOGGER.info("Bed Defense 26.3 HUD prototype initialized; no raid mechanics are currently active.");
    }
}
