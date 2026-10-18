package com.darkkyoung.beddefense;

import com.darkkyoung.beddefense.network.ClockSyncPayload;
import com.darkkyoung.beddefense.network.BedStatusPayload;
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

/** Technical prototype only: a Korean HUD and the existing cobblestone crack animation. */
public final class BedDefense implements ModInitializer {
    public static final String MOD_ID = "beddefense";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static int hudClockSyncCounter;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(ClockSyncPayload.TYPE, ClockSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BedStatusPayload.TYPE, BedStatusPayload.CODEC);
        SpawnBedPrototype.init();
        ChallengeMenu.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                dispatcher.register(Commands.literal("beddefense")
                        .executes(ctx -> {
                            ChallengeMenu.show(ctx.getSource().getPlayerOrException(), ctx.getSource().getServer());
                            return 1;
                        })
                        .then(Commands.literal("menu").executes(ctx -> {
                            ChallengeMenu.show(ctx.getSource().getPlayerOrException(), ctx.getSource().getServer());
                            return 1;
                        }))
                        .then(Commands.literal("start").executes(ctx -> {
                            return ChallengeMenu.start(ctx.getSource().getPlayerOrException(), ctx.getSource().getServer()) ? 1 : 0;
                        }))
                        .then(Commands.literal("rules").executes(ctx -> {
                            ChallengeMenu.rules(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(ctx -> {
                            ChallengeMenu.status(ctx.getSource().getPlayerOrException(), ctx.getSource().getServer());
                            return 1;
                        }))
                        .then(Commands.literal("debug")
                                .then(Commands.literal("cracks")
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            HitResult hit = player.pick(6.0D, 0.0F, false);
                                            if (hit.getType() != HitResult.Type.BLOCK) {
                                                ctx.getSource().sendFailure(Component.literal("6블록 이내의 조약돌을 바라보세요."));
                                                return 0;
                                            }

                                            BlockPos target = ((BlockHitResult) hit).getBlockPos();
                                            ServerLevel level = (ServerLevel) player.level();
                                            if (!level.getBlockState(target).is(Blocks.COBBLESTONE)) {
                                                ctx.getSource().sendFailure(Component.literal("시험판에서는 조약돌만 파괴할 수 있습니다."));
                                                return 0;
                                            }
                                            if (!BlockCrackDemo.begin(level, target)) {
                                                ctx.getSource().sendFailure(Component.literal("이미 파괴 진행 중인 블록입니다."));
                                                return 0;
                                            }
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("4초 동안 바닐라 균열 애니메이션 시험을 시작합니다."),
                                                    false
                                            );
                                            return 1;
                                        })))
                )
        );

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            BlockCrackDemo.tick(server);
            SpawnBedPrototype.tick(server);
            if (++hudClockSyncCounter < 20) {
                return;
            }
            hudClockSyncCounter = 0;
            long overworldTime = ChallengeMenu.overworldClock(server);
            ChallengeData challenge = ChallengeData.get(server.overworld());
            long elapsed = challenge.elapsedTicks(server.overworld().getGameTime());
            long day = challenge.day(overworldTime);
            ClockSyncPayload payload = new ClockSyncPayload(overworldTime, elapsed, day);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (ServerPlayNetworking.canSend(player, ClockSyncPayload.TYPE)) {
                    ServerPlayNetworking.send(player, payload);
                    if (ServerPlayNetworking.canSend(player, BedStatusPayload.TYPE)) {
                        ServerPlayNetworking.send(player,
                            new BedStatusPayload(SpawnBedPrototype.hasPlacedBed(player, server.overworld()) ? 1 : 0));
                    }
                }
            }
        });
        LOGGER.info("Bed Defense 26.3 Korean HUD v2 prototype initialized; real raids and bed HP are not implemented.");
    }
}
