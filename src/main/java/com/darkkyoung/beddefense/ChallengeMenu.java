package com.darkkyoung.beddefense;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** AllBlocks-master inspired Korean click-chat settings, with no background panels. */
public final class ChallengeMenu {
    private ChallengeMenu() {}

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> {
                    if (!handler.player.isRemoved())
                        show(handler.player, server);
                }));
    }

    public static long overworldClock(MinecraftServer server) {
        return server.clockManager()
                .getInstance(server.overworld().dimensionType().defaultClock().orElseThrow())
                .totalTicks();
    }

    public static void show(ServerPlayer player, MinecraftServer server) {
        ChallengeData data = ChallengeData.get(server.overworld());
        title(player, "베드 디펜스");
        if (!data.isStarted()) {
            player.sendSystemMessage(button("      [게임 시작]", ChatFormatting.GREEN,
                    "/beddefense prepare", "게임 설정 화면으로 이동"));
            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(Component.literal("§f100일 안에 엔더 드래곤을 처치하세요."));
            player.sendSystemMessage(Component.literal("§7첫 번째 침대가 유일한 스폰 침대가 됩니다."));
            player.sendSystemMessage(Component.literal("§7매일 밤 레이드가 발생하며 침대가 파괴되면 더 이상 부활할 수 없습니다."));
            player.sendSystemMessage(Component.literal("§7침대 없이 사망하면 도전이 끝납니다."));
            player.sendSystemMessage(Component.literal("§8아직 초기 버전이며 미완성 전투 시스템은 구현 중입니다."));
        } else {
            player.sendSystemMessage(Component.literal("§a도전 진행 중 · §f" + data.day(overworldClock(server)) + "일차"));
            player.sendSystemMessage(Component.literal("§7난이도 " + data.difficulty().display +
                    " · 레이드 편성 ×" + data.raidStride()));
            player.sendSystemMessage(row(
                    button("[진행 상태]", ChatFormatting.AQUA, "/beddefense status", "현재 도전"),
                    button("[HUD 표시 전환]", ChatFormatting.YELLOW, "/beddefense hud", "HUD 표시 또는 숨김")));
        }
        footer(player);
    }

    public static void showSettings(ServerPlayer player, ChallengeSetupManager.Setup s) {
        title(player, "게임 설정");
        player.sendSystemMessage(Component.literal("§7아래 항목을 클릭해 선택하세요. 설정은 시작 전에만 변경됩니다."));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§f1. 난이도  §7(강화 수치는 추후 조정)"));
        player.sendSystemMessage(row(
                select("[쉬움]", s.difficulty == ChallengeDifficulty.EASY, "/beddefense setup difficulty easy"),
                select("[보통]", s.difficulty == ChallengeDifficulty.NORMAL, "/beddefense setup difficulty normal"),
                select("[어려움]", s.difficulty == ChallengeDifficulty.HARD, "/beddefense setup difficulty hard")));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§f2. 시작 시 월드 시간"));
        player.sendSystemMessage(row(
                select("[현재 시간 유지]", !s.resetWorldTime, "/beddefense setup clock keep"),
                select("[1일차 아침 초기화]", s.resetWorldTime, "/beddefense setup clock reset")));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§f3. 일일 레이드 편성 진행 속도"));
        player.sendSystemMessage(Component.literal("§7매일 레이드는 1회 발생하며, 2배면 1·3·5·7...일차 편성이 적용됩니다."));
        player.sendSystemMessage(row(
                select("[기본 ×1]", s.raidStride == 1, "/beddefense setup stride 1"),
                select("[×2]", s.raidStride == 2, "/beddefense setup stride 2"),
                select("[×3]", s.raidStride == 3, "/beddefense setup stride 3"),
                select("[×5]", s.raidStride == 5, "/beddefense setup stride 5")));
        player.sendSystemMessage(row(
                button("[-1]", ChatFormatting.YELLOW, "/beddefense setup adjust -1", "편성 배속 낮추기"),
                Component.literal("§f현재 ×" + s.raidStride + " §8(1~10)"),
                button("[+1]", ChatFormatting.YELLOW, "/beddefense setup adjust 1", "편성 배속 높이기")));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(row(
                button("[설정 완료 · 3초 후 시작]", ChatFormatting.GREEN,
                        "/beddefense confirm", "카운트다운 후 도전 시작"),
                button("[돌아가기]", ChatFormatting.GRAY, "/beddefense back", "시작 메뉴")));
        footer(player);
    }

    public static void title(ServerPlayer player, String name) {
        player.sendSystemMessage(Component.literal("§8━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendSystemMessage(Component.literal("§6§l[ " + name + " ]"));
        player.sendSystemMessage(Component.literal(""));
    }

    public static void footer(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§8━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendSystemMessage(Component.literal("§8/beddefense : 메뉴 다시 열기"));
    }

    public static void status(ServerPlayer player, MinecraftServer server) {
        var state = ChallengeData.get(server.overworld());
        if (!state.isStarted()) {
            player.sendSystemMessage(Component.literal("§e시작 전입니다. [게임 시작]을 클릭하세요."));
        } else {
            long seconds = state.elapsedTicks(server.overworld().getGameTime()) / 20L;
            player.sendSystemMessage(Component.literal("§a진행 중: " + state.day(overworldClock(server))
                    + "일차 · " + String.format(java.util.Locale.ROOT, "%02d:%02d:%02d",
                    seconds / 3600L, (seconds / 60L) % 60L, seconds % 60L)
                    + " · 적용 레이드 편성 " + state.raidIndex(state.day(overworldClock(server))) + "일차"));
        }
    }

    private static MutableComponent select(String text, boolean active, String command) {
        return button(text, active ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                command, active ? "선택됨" : "클릭해 변경")
                .withStyle(s -> s.withBold(active));
    }

    private static MutableComponent button(String text, ChatFormatting color, String command, String hint) {
        return Component.literal(text).withStyle(s -> s.withColor(color)
                .withBold(true).withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hint))));
    }
    private static MutableComponent row(Component... children) {
        var result = Component.literal("");
        for (var child : children) {
            if (!result.getSiblings().isEmpty()) result.append(Component.literal("  "));
            result.append(child);
        }
        return result;
    }
}
