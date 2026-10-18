package com.darkkyoung.beddefense;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
 * A technical smoke test, not the finished raid/challenge implementation.
 * All block mutation is restricted to an explicit debug command.
 */
public final class BedDefense implements ModInitializer {
    public static final String MOD_ID = "beddefense";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                dispatcher.register(Commands.literal("beddefense")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("[Bed Defense] 26.3 crack-overlay prototype. Try /beddefense debug cracks."),
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

        ServerTickEvents.END_SERVER_TICK.register(BlockCrackDemo::tick);
        LOGGER.info("Bed Defense 26.3 technical prototype initialized; real raid gameplay is not active.");
    }
}
