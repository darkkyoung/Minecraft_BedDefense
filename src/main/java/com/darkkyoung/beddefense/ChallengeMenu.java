package com.darkkyoung.beddefense;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.darkkyoung.beddefense.network.MenuUiPayload;
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
                    if (!handler.player.isRemoved()) {ChallengeLifecycle.join(handler.player,server);show(handler.player, server);}
                }));
    }

    /** Mirrors AllBlocks master: remove the last menu before printing its updated state. */
    public static void clearMenuChat(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, MenuUiPayload.TYPE))
            ServerPlayNetworking.send(player, new MenuUiPayload(false));
    }

    /** Send once before the first countdown title; avoid leaving chat over the 3-2-1. */
    public static void closeChatForCountdown(MinecraftServer server) {
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(target, MenuUiPayload.TYPE))
                ServerPlayNetworking.send(target, new MenuUiPayload(true));
        }
    }

    public static long overworldClock(MinecraftServer server) {
        return server.clockManager()
                .getInstance(server.overworld().dimensionType().defaultClock().orElseThrow())
                .totalTicks();
    }

    public static void show(ServerPlayer player, MinecraftServer server) {
        clearMenuChat(player);
        ChallengeData data = ChallengeData.get(server.overworld());
        if (!data.isStarted()) {
            // AllBlocks master welcome sequence: greeting, introduction, then action.
            for (Component line : welcomeMessages()) player.sendSystemMessage(line);
            return;
        } else if(!data.running()) {
            player.sendSystemMessage(Component.literal(data.outcome()==ChallengeData.WON?"§a지난 챌린지 성공!":"§c지난 챌린지 실패: "+data.reason()));
            player.sendSystemMessage(button(" [다시 시작] ", ChatFormatting.GREEN,
                    "/beddefense setup","기존 월드를 보존하고 새 도전의 설정을 엽니다"));
            player.sendSystemMessage(Component.literal("§7이전 침대 HP·레이드·도전 시간만 초기화하며, 월드·일반 아이템은 유지됩니다."));
        } else {
            player.sendSystemMessage(Component.literal("§a진행 중 §f"
                    + data.day(overworldClock(server)) + "일차"));
            player.sendSystemMessage(button(" [진행 상태] ", ChatFormatting.AQUA,
                    "/beddefense status", "현재 챌린지 상태"));
        }
        player.sendSystemMessage(Component.literal("§7목표: §f100일 안에 엔더 드래곤을 처치하세요."));
        player.sendSystemMessage(Component.literal("§7침대: §f첫 번째 스폰 침대가 유일한 리스폰 지점입니다."));
        player.sendSystemMessage(Component.literal("§7레이드: §f침대 유무와 무관하게 매일 밤 등장하며 편성 진행 배속에 따라 강화됩니다."));
        player.sendSystemMessage(Component.literal("§7패배: §f침대가 없거나 파괴된 후 죽으면 탈락합니다."));
        player.sendSystemMessage(Component.literal("§7수리: §f에메랄드 또는 다이아몬드 1개 = HP 1 · 회수: 침대 파괴"));
        player.sendSystemMessage(Component.literal("§8/beddefense · 채팅 메뉴 다시 열기"));
    }

    /** Build the first-join chat in AllBlocks master's greeting -> overview -> start order. */
    static java.util.List<Component> welcomeMessages() {
        return java.util.List.of(
                Component.literal("§8━━━━━━━━━━━━━━━━━━━━━━━━━━"),
                Component.literal("§6§l[ 베드 디펜스 ]"),
                Component.literal("§f베드 디펜스에 오신 것을 환영합니다!"),
                Component.literal("§f낮에는 탐험하고, 밤에는 몬스터의 습격으로부터 침대를 지키는 100일 생존 챌린지입니다."),
                Component.literal(""),
                Component.literal("§e목표: §f100일 안에 엔더 드래곤을 처치하세요."),
                Component.literal("§7첫 번째로 지정한 침대만 리스폰 지점으로 사용할 수 있습니다."),
                Component.literal("§7레이드는 침대 유무와 관계없이 매일 밤 등장합니다."),
                Component.literal("§7침대가 없거나 파괴된 상태에서 사망하면 챌린지에서 탈락합니다."),
                Component.literal(""),
                button("   [게임 시작]", ChatFormatting.GREEN,
                        "/beddefense setup", "난이도·시간·레이드 편성 배속을 설정합니다"),
                Component.literal("§8/beddefense · 채팅 메뉴 다시 열기"),
                Component.literal("§8━━━━━━━━━━━━━━━━━━━━━━━━━━")
        );
    }

    /** Called only after the explicit setup wizard and 3-second countdown. */
    public static boolean startNow(ServerPlayer player, MinecraftServer server) {
        ChallengeData data = ChallengeData.get(server.overworld());
        if (!ChallengeLifecycle.resetFinishedRun(server)) {
            player.sendSystemMessage(Component.literal("§c기존 도전을 정리할 수 없어 다시 시작하지 않았습니다."));
            return false;
        }
        boolean hard = HardRaidBalance.enabled(ChallengeOptionsData.get(server.overworld()).difficulty());
        if (!data.start(server.overworld().getGameTime(), overworldClock(server), hard)) {
            player.sendSystemMessage(Component.literal("§e이미 진행 중인 챌린지입니다."));
            return false;
        }
        for(ServerPlayer participant:server.getPlayerList().getPlayers())
            if(!participant.isSpectator())data.join(participant.getUUID());
        RaidManager.run(server,"title @a title {\"text\":\"시작!\",\"color\":\"green\"}");
        RaidManager.run(server,"execute as @a at @s run playsound minecraft:entity.player.levelup master @s ~ ~ ~ 1 1");
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("§a§l[베드 디펜스] §f챌린지 시작! §7(1일차 · 00:00:00)"), false);
        return true;
    }

    public static void status(ServerPlayer player, MinecraftServer server) {
        clearMenuChat(player);
        ChallengeData data = ChallengeData.get(server.overworld());
        if (!data.isStarted()) {
            player.sendSystemMessage(Component.literal("§e도전 시작 전입니다."));
        } else {
            long seconds = data.elapsedTicks(server.overworld().getGameTime()) / 20L;
            String time = String.format(java.util.Locale.ROOT, "%02d:%02d:%02d",
                    seconds / 3600, seconds / 60 % 60, seconds % 60);
            ChallengeOptionsData opts = ChallengeOptionsData.get(server.overworld());
            player.sendSystemMessage(Component.literal((data.running()?"§a진행 중: §f":"§e종료: §f")
                    + data.day(overworldClock(server)) + "일차 · " + time));
            player.sendSystemMessage(Component.literal("§7난이도: " + opts.difficulty()
                    + " · 레이드 편성 진행 ×" + opts.raidStride()));
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
