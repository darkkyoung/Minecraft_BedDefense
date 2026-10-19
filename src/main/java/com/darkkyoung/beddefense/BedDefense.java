package com.darkkyoung.beddefense;

import com.darkkyoung.beddefense.network.ClockSyncPayload;
import com.darkkyoung.beddefense.network.BedStatusPayload;
import com.darkkyoung.beddefense.network.HudActionPayload;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
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
        PayloadTypeRegistry.clientboundPlay().register(HudActionPayload.TYPE,HudActionPayload.CODEC);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, selection) ->
            dispatcher.register(Commands.literal("beddefense")
                .executes(ctx -> {
                    ChallengeMenu.show(ctx.getSource().getPlayerOrException(), ctx.getSource().getServer());
                    return 1;
                })
                .then(Commands.literal("prepare").executes(ctx -> {
                    ChallengeSetupManager.open(ctx.getSource().getPlayerOrException(),ctx.getSource().getServer());
                    return 1;
                }))
                .then(Commands.literal("setup")
                    .then(Commands.literal("difficulty")
                        .then(Commands.literal("easy").executes(ctx -> changeDifficulty(ctx,"easy")))
                        .then(Commands.literal("normal").executes(ctx -> changeDifficulty(ctx,"normal")))
                        .then(Commands.literal("hard").executes(ctx -> changeDifficulty(ctx,"hard"))))
                    .then(Commands.literal("clock")
                        .then(Commands.literal("keep").executes(ctx -> changeClock(ctx,false)))
                        .then(Commands.literal("reset").executes(ctx -> changeClock(ctx,true))))
                    .then(Commands.literal("stride")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1,10))
                            .executes(ctx -> changeStride(ctx,IntegerArgumentType.getInteger(ctx,"value"),false))))
                    .then(Commands.literal("adjust")
                        .then(Commands.argument("value", IntegerArgumentType.integer(-10,10))
                            .executes(ctx -> changeStride(ctx,IntegerArgumentType.getInteger(ctx,"value"),true)))))
                .then(Commands.literal("confirm").executes(ctx -> {
                    ChallengeSetupManager.confirm(ctx.getSource().getPlayerOrException(),ctx.getSource().getServer());
                    return 1;
                }))
                .then(Commands.literal("back").executes(ctx -> {
                    ChallengeSetupManager.cancel(ctx.getSource().getPlayerOrException(),ctx.getSource().getServer());
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ChallengeMenu.status(ctx.getSource().getPlayerOrException(),ctx.getSource().getServer());
                    return 1;
                }))
                .then(Commands.literal("hud").executes(ctx -> {
                    ServerPlayNetworking.send(ctx.getSource().getPlayerOrException(), new HudActionPayload(0));
                    return 1;
                }))
                .then(Commands.literal("debug")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("hudpreview").executes(ctx -> {
                        ServerPlayNetworking.send(ctx.getSource().getPlayerOrException(), new HudActionPayload(1));
                        return 1;
                    }))
                    .then(Commands.literal("cracks").executes(ctx -> {
                        ServerPlayer player=ctx.getSource().getPlayerOrException();
                        HitResult hit=player.pick(6.0,0.0F,false);
                        if(hit.getType()!=HitResult.Type.BLOCK) {
                            ctx.getSource().sendFailure(Component.literal("6블록 이내의 조약돌을 바라보세요."));
                            return 0;
                        }
                        BlockPos target=((BlockHitResult)hit).getBlockPos();
                        ServerLevel world=(ServerLevel)player.level();
                        if(!world.getBlockState(target).is(Blocks.COBBLESTONE)
                            || !BlockCrackDemo.begin(world,target)) {
                            ctx.getSource().sendFailure(Component.literal("조약돌만 시험할 수 있으며 중복 진행할 수 없습니다."));
                            return 0;
                        }
                        return 1;
                    })))
            )
        );

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            BlockCrackDemo.tick(server);
            SpawnBedPrototype.tick(server);
            ChallengeSetupManager.tick(server);
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

    private static int changeDifficulty(CommandContext<CommandSourceStack> ctx,String key) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ChallengeSetupManager.difficulty(ctx.getSource().getPlayerOrException(),ctx.getSource().getServer(),key);
        return 1;
    }
    private static int changeClock(CommandContext<CommandSourceStack> ctx,boolean reset) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ChallengeSetupManager.clock(ctx.getSource().getPlayerOrException(),ctx.getSource().getServer(),reset);
        return 1;
    }
    private static int changeStride(CommandContext<CommandSourceStack> ctx,int value,boolean relative) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ChallengeSetupManager.stride(ctx.getSource().getPlayerOrException(),ctx.getSource().getServer(),value,relative);
        return 1;
    }
}
