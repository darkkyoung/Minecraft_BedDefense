package com.darkkyoung.beddefense;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** The main entry point is always /beddefense; no separate /bdhud alias. */
public final class ChallengeMenu {
    private ChallengeMenu() { }

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> {
                    if (!handler.player.isRemoved()) show(handler.player, server);
                }));
    }

    public static long overworldClock(MinecraftServer server) {
        return server.clockManager()
                .getInstance(server.overworld().dimensionType().defaultClock().orElseThrow())
                .totalTicks();
    }

    public static void show(ServerPlayer player, MinecraftServer server) {
        ChallengeData data = ChallengeData.get(server.overworld());
        player.sendSystemMessage(Component.literal("§6§l────── 베드 디펜스 ──────"));
        if (!data.isStarted()) {
            // Requested layout: start button FIRST, rules below as plain chat text.
            player.sendSystemMessage(button(" [게임 시작 · 설정] ", ChatFormatting.GREEN,
                    "/beddefense setup", "난이도·시간·레이드 주기를 설정합니다"));
        } else {
            player.sendSystemMessage(Component.literal("§a진행 중 §f"
                    + data.day(overworldClock(server)) + "일차"));
            player.sendSystemMessage(button(" [진행 상태] ", ChatFormatting.AQUA,
                    "/beddefense status", "현재 챌린지 상태"));
        }
        player.sendSystemMessage(Component.literal("§7목표: §f100일 안에 엔더 드래곤을 처치하세요."));
        player.sendSystemMessage(Component.literal("§7침대: §f첫 번째 스폰 침대가 유일한 리스폰 지점입니다."));
        player.sendSystemMessage(Component.literal("§7레이드: §f침대 유무와 무관하게 설정한 날 밤에 등장합니다."));
        player.sendSystemMessage(Component.literal("§7패배: §f침대가 없거나 파괴된 후 죽으면 탈락합니다."));
        player.sendSystemMessage(Component.literal("§8/beddefense · 채팅 메뉴 다시 열기"));
    }

    /** Called only after the explicit setup wizard and 3-second countdown. */
    public static boolean startNow(ServerPlayer player, MinecraftServer server) {
        ChallengeData data = ChallengeData.get(server.overworld());
        if (!data.start(server.overworld().getGameTime(), overworldClock(server))) {
            player.sendSystemMessage(Component.literal("§e이미 진행 중인 챌린지입니다."));
            return false;
        }
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("§a§l[베드 디펜스] §f챌린지 시작! §7(1일차 · 00:00:00)"), false);
        return true;
    }

    public static void status(ServerPlayer player, MinecraftServer server) {
        ChallengeData data = ChallengeData.get(server.overworld());
        if (!data.isStarted()) {
            player.sendSystemMessage(Component.literal("§e도전 시작 전입니다."));
        } else {
            long seconds = data.elapsedTicks(server.overworld().getGameTime()) / 20L;
            String time = String.format(java.util.Locale.ROOT, "%02d:%02d:%02d",
                    seconds / 3600, seconds / 60 % 60, seconds % 60);
            ChallengeOptionsData opts = ChallengeOptionsData.get(server.overworld());
            player.sendSystemMessage(Component.literal("§a진행 중: §f"
                    + data.day(overworldClock(server)) + "일차 · " + time));
            player.sendSystemMessage(Component.literal("§7난이도: " + opts.difficulty()
                    + " · 레이드 주기 " + opts.raidEveryDays() + "일"));
        }
        player.sendSystemMessage(button(" [메뉴] ", ChatFormatting.YELLOW,
                "/beddefense", "메뉴 다시 열기"));
    }

    public static MutableComponent button(String title, ChatFormatting color,
                                           String command, String tooltip) {
        return Component.literal(title)
                .withStyle(style -> style.withColor(color).withBold(true)
                        .withClickEvent(new ClickEvent.RunCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(tooltip))));
    }
}
