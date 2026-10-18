package com.darkkyoung.beddefense;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Clickable Korean chat menu. Join and explicit /beddefense menu are display-only.
 * Only the explicit /beddefense start command changes the persistent challenge state.
 */
public final class ChallengeMenu {
    private ChallengeMenu() {}

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> {
                    if (handler.player.isRemoved()) return;
                    show(handler.player, server);
                }));
    }

    public static long overworldClock(MinecraftServer server) {
        return server.clockManager()
                .getInstance(server.overworld().dimensionType().defaultClock().orElseThrow())
                .totalTicks();
    }

    public static void show(ServerPlayer player, MinecraftServer server) {
        ChallengeData state = ChallengeData.get(server.overworld());
        player.sendSystemMessage(Component.literal("§6§l──────── 베드 디펜스 ────────"));
        if (!state.isStarted()) {
            player.sendSystemMessage(Component.literal("§f침대를 지키며 엔더 드래곤을 처치하세요."));
            player.sendSystemMessage(Component.literal("§7시작 전에는 도전 시간과 일차가 진행되지 않습니다."));
            player.sendSystemMessage(
                    button(" [게임 시작] ", ChatFormatting.GREEN, "/beddefense start", "Bed Defense 챌린지 시작")
                            .append(Component.literal("  "))
                            .append(button("[규칙 보기]", ChatFormatting.YELLOW,
                                    "/beddefense rules", "게임 규칙 확인")));
        } else {
            player.sendSystemMessage(Component.literal("§a진행 중  §7·  " +
                    state.day(overworldClock(server)) + "일차"));
            player.sendSystemMessage(button(" [진행 상태] ", ChatFormatting.AQUA,
                    "/beddefense status", "진행 중인 도전 확인")
                    .append(Component.literal("  "))
                    .append(button("[규칙 보기]", ChatFormatting.YELLOW,
                            "/beddefense rules", "게임 규칙 확인")));
        }
        player.sendSystemMessage(Component.literal("§8채팅 메뉴 다시 열기: /beddefense menu"));
    }

    public static boolean start(ServerPlayer player, MinecraftServer server) {
        ChallengeData data = ChallengeData.get(server.overworld());
        if (!data.start(server.overworld().getGameTime(), overworldClock(server))) {
            player.sendSystemMessage(Component.literal("§e이미 시작한 도전입니다. /beddefense status"));
            return false;
        }
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("§a§l[베드 디펜스] §f챌린지가 시작되었습니다! §7(1일차 · 00:00:00)"),
                false);
        player.sendSystemMessage(Component.literal("§7현재는 시작 메뉴와 타이머만 작동하는 개발 버전입니다. 레이드는 아직 생성되지 않습니다."));
        return true;
    }

    public static void rules(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§6§l[베드 디펜스] 기본 규칙"));
        player.sendSystemMessage(Component.literal("§f목표: 100일 안에 엔더 드래곤 처치"));
        player.sendSystemMessage(Component.literal("§f첫 스폰 침대 보호 / 밤마다 레이드 / 침대 파괴 후 사망 시 탈락"));
        player.sendSystemMessage(Component.literal("§7현재 테스트 버전은 침대 등록 시각 효과와 HUD만 지원합니다."));
        player.sendSystemMessage(button(" [메뉴로 돌아가기] ", ChatFormatting.YELLOW,
                "/beddefense menu", "시작 메뉴"));
    }

    public static void status(ServerPlayer player, MinecraftServer server) {
        ChallengeData data = ChallengeData.get(server.overworld());
        if (!data.isStarted()) {
            player.sendSystemMessage(Component.literal("§e챌린지 시작 전입니다."));
        } else {
            long seconds = data.elapsedTicks(server.overworld().getGameTime()) / 20L;
            String time = String.format(java.util.Locale.ROOT, "%02d:%02d:%02d",
                    seconds / 3600, seconds / 60 % 60, seconds % 60);
            player.sendSystemMessage(Component.literal("§a베드 디펜스 진행 중  §f" +
                    data.day(overworldClock(server)) + "일차 · " + time));
        }
        player.sendSystemMessage(button(" [메뉴 열기] ", ChatFormatting.YELLOW,
                "/beddefense menu", "시작 메뉴"));
    }

    private static Component button(String title, ChatFormatting color, String command, String tooltip) {
        return Component.literal(title)
                .withStyle(style -> style.withColor(color).withBold(true)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(tooltip))));
    }
}
