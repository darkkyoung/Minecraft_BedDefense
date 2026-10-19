package com.darkkyoung.beddefense;

import com.darkkyoung.beddefense.network.ClockSyncPayload;
import com.darkkyoung.beddefense.network.BedStatusPayload;
import com.darkkyoung.beddefense.network.HudControlPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
        PayloadTypeRegistry.clientboundPlay().register(HudControlPayload.TYPE, HudControlPayload.CODEC);
        SpawnBedPrototype.init();
        ChallengeMenu.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                dispatcher.register(Commands.literal("beddefense")
                        .executes(ctx -> {
                            ChallengeMenu.show(ctx.getSource().getPlayerOrException(),ctx.getSource().getServer());
                            return 1;
                        })
                        .then(Commands.literal("start")
                                .executes(ctx -> setup(ctx.getSource().getServer(), ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("setup")
                                .executes(ctx -> setup(ctx.getSource().getServer(),ctx.getSource().getPlayerOrException()))
                                .then(Commands.literal("difficulty")
                                        .then(Commands.literal("easy").executes(ctx ->
                                                bool(ChallengeSetupWizard.setDifficulty(ctx.getSource().getServer(),ctx.getSource().getPlayerOrException(),"쉬움"))))
                                        .then(Commands.literal("normal").executes(ctx ->
                                                bool(ChallengeSetupWizard.setDifficulty(ctx.getSource().getServer(),ctx.getSource().getPlayerOrException(),"보통"))))
                                        .then(Commands.literal("hard").executes(ctx ->
                                                bool(ChallengeSetupWizard.setDifficulty(ctx.getSource().getServer(),ctx.getSource().getPlayerOrException(),"어려움")))))
                                .then(Commands.literal("time")
                                        .then(Commands.literal("keep").executes(ctx ->
                                                bool(ChallengeSetupWizard.setTime(ctx.getSource().getServer(),ctx.getSource().getPlayerOrException(),false))))
                                        .then(Commands.literal("reset").executes(ctx ->
                                                bool(ChallengeSetupWizard.setTime(ctx.getSource().getServer(),ctx.getSource().getPlayerOrException(),true)))))
                                .then(Commands.literal("interval")
                                        .then(Commands.argument("days",IntegerArgumentType.integer(1,20)).executes(ctx ->
                                                bool(ChallengeSetupWizard.setInterval(ctx.getSource().getServer(),ctx.getSource().getPlayerOrException(),
                                                        IntegerArgumentType.getInteger(ctx,"days"))))))
                                .then(Commands.literal("manual").executes(ctx ->
                                        bool(ChallengeSetupWizard.manual(ctx.getSource().getServer(),ctx.getSource().getPlayerOrException()))))
                                .then(Commands.literal("adjust")
                                        .then(Commands.argument("delta",IntegerArgumentType.integer(-5,5)).executes(ctx ->
                                                bool(ChallengeSetupWizard.adjust(ctx.getSource().getServer(),ctx.getSource().getPlayerOrException(),
                                                        IntegerArgumentType.getInteger(ctx,"delta"))))))
                                .then(Commands.literal("confirm").executes(ctx ->
                                        bool(ChallengeSetupWizard.confirm(ctx.getSource().getServer(),ctx.getSource().getPlayerOrException()))))
                                .then(Commands.literal("cancel").executes(ctx ->
                                        bool(ChallengeSetupWizard.cancel(ctx.getSource().getServer(),ctx.getSource().getPlayerOrException())))))
                        .then(Commands.literal("status").executes(ctx -> {
                            ChallengeMenu.status(ctx.getSource().getPlayerOrException(),ctx.getSource().getServer()); return 1;
                        }))
                        .then(Commands.literal("hud").executes(ctx -> sendHud(ctx.getSource().getPlayerOrException(),0)))
                        .then(Commands.literal("debug")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal("§6[베드 디펜스] /beddefense debug cracks · preview"),false);
                                    return 1;
                                })
                                .then(Commands.literal("preview").executes(ctx -> sendHud(ctx.getSource().getPlayerOrException(),1)))
                                .then(Commands.literal("cracks")
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            HitResult hit = player.pick(6.0D, 0.0F, false);
                                            if (hit.getType() != HitResult.Type.BLOCK) {
                                                ctx.getSource().sendFailure(Component.literal("6블록 이내의 조약돌을 바라보세요."));
                                                return 0;
                                            }
                                            BlockPos target = ((BlockHitResult)hit).getBlockPos();
                                            ServerLevel level = (ServerLevel)player.level();
                                            if (!level.getBlockState(target).is(Blocks.COBBLESTONE)) {
                                                ctx.getSource().sendFailure(Component.literal("디버그에서는 조약돌만 파괴할 수 있습니다."));
                                                return 0;
                                            }
                                            if (!BlockCrackDemo.begin(level,target)) return 0;
                                            return 1;
                                        })))
                )
        );

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            BlockCrackDemo.tick(server);
            ChallengeSetupWizard.tick(server);
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
    private static int setup(net.minecraft.server.MinecraftServer server,ServerPlayer player) {
        return bool(ChallengeSetupWizard.begin(server,player));
    }
    private static int bool(boolean yes) { return yes ? 1 : 0; }
    private static int sendHud(ServerPlayer player,int command) {
        if (ServerPlayNetworking.canSend(player,HudControlPayload.TYPE)) {
            ServerPlayNetworking.send(player,new HudControlPayload(command));
            return 1;
        }
        player.sendSystemMessage(Component.literal("§cBed Defense HUD용 클라이언트 연결이 없습니다."));
        return 0;
    }
}
