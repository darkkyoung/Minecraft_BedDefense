package com.darkkyoung.beddefense;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Recreates the AllBlocks master settings pattern without sharing code or state.
 * Daily raids always occur; this setting advances the day-by-day *roster* faster.
 * Settings persist separately from the run lifecycle and are immutable once started.
 */
public final class ChallengeSetupWizard {
    private static final Map<MinecraftServer, Session> SESSIONS = new WeakHashMap<>();
    private static final int COUNTDOWN_TICKS = 60;
    private ChallengeSetupWizard() { }

    private static final class Session {
        final UUID owner;
        String difficulty = "보통";
        boolean resetTime = false;
        int raidStride = 1;
        boolean manual = false;
        int countdown = -1;
        int lastSecond = -1;

        Session(UUID owner) { this.owner = owner; }
    }

    public static boolean begin(MinecraftServer server, ServerPlayer player) {
        if (ChallengeData.get(server.overworld()).running()) {
            player.sendSystemMessage(Component.literal("§e이미 진행 중입니다. 설정을 다시 시작할 수 없습니다."));
            return false;
        }
        Session previous = SESSIONS.get(server);
        if (previous != null && !previous.owner.equals(player.getUUID()) &&
                server.getPlayerList().getPlayer(previous.owner) != null) {
            player.sendSystemMessage(Component.literal("§c다른 플레이어가 설정 중입니다."));
            return false;
        }
        Session session = new Session(player.getUUID());
        ChallengeOptionsData saved=ChallengeOptionsData.get(server.overworld());
        session.difficulty=saved.difficulty();session.resetTime=saved.resetWorldClock();session.raidStride=saved.raidStride();
        SESSIONS.put(server, session);
        show(player, session);
        return true;
    }

    public static boolean setDifficulty(MinecraftServer server, ServerPlayer player, String difficulty) {
        Session s = controlled(server, player);
        if (s == null || !java.util.List.of("쉬움", "보통", "어려움").contains(difficulty)) return false;
        s.difficulty = difficulty;
        persist(server,s);
        show(player,s);
        return true;
    }
    public static boolean setTime(MinecraftServer server, ServerPlayer player, boolean reset) {
        Session s = controlled(server, player);
        if (s == null) return false;
        s.resetTime = reset;
        persist(server,s);
        show(player,s);
        return true;
    }
    public static boolean setInterval(MinecraftServer server, ServerPlayer player, int interval) {
        Session s = controlled(server, player);
        if (s == null || !(interval == 1 || interval == 2 || interval == 3 || interval == 5)) return false;
        s.raidStride = interval;
        s.manual = false;
        persist(server,s);
        show(player,s);
        return true;
    }
    public static boolean manual(MinecraftServer server, ServerPlayer player) {
        Session s = controlled(server, player);
        if (s == null) return false;
        s.manual = true;
        persist(server,s);
        show(player,s);
        return true;
    }
    public static boolean adjust(MinecraftServer server, ServerPlayer player, int delta) {
        Session s = controlled(server, player);
        if (s == null || !s.manual) return false;
        s.raidStride = Math.max(1, Math.min(100, s.raidStride + delta));
        persist(server,s);
        show(player,s);
        return true;
    }
    public static boolean cancel(MinecraftServer server, ServerPlayer player) {
        Session s = controlled(server,player);
        if(s==null)return false;
        SESSIONS.remove(server);
        ChallengeMenu.show(player,server);
        return true;
    }
    public static boolean confirm(MinecraftServer server, ServerPlayer player) {
        Session s = controlled(server,player);
        if(s == null)return false;
        if(s.countdown >= 0)return false;
        s.countdown = COUNTDOWN_TICKS;
        s.lastSecond = 3;
        // AllBlocks master: remove the chat overlay first, then show title 3/2/1.
        ChallengeMenu.closeChatForCountdown(server);
        countdownTitle(server,3);
        return true;
    }
    public static void tick(MinecraftServer server) {
        Session s = SESSIONS.get(server);
        if(s==null||s.countdown<0)return;
        ServerPlayer owner = server.getPlayerList().getPlayer(s.owner);
        if(owner==null){SESSIONS.remove(server);return;}
        if (ChallengeData.get(server.overworld()).running()){SESSIONS.remove(server);return;}
        --s.countdown;
        if (s.countdown <= 0) {
            // Settings were saved on each edit; only the run begins after the countdown.
            if(s.resetTime) {
                String[] commands = {
                    "time of minecraft:overworld rate 1",
                    "time of minecraft:overworld resume",
                    "time of minecraft:overworld set 0"
                };
                try {
                    for (String command : commands)
                        server.getCommands().performPrefixedCommand(
                                server.createCommandSourceStack().withSuppressedOutput(), command);
                } catch(Exception e) {
                    owner.sendSystemMessage(Component.literal("§c시간 초기화가 실패해 도전을 시작하지 않았습니다."));
                    SESSIONS.remove(server);
                    return;
                }
            }
            ChallengeOptionsData.get(server.overworld()).update(s.difficulty, s.resetTime, s.raidStride);
            ChallengeMenu.startNow(owner,server);
            SESSIONS.remove(server);
            return;
        }
        int second=(s.countdown + 19)/20;
        if(second>0 && second!=s.lastSecond){
            s.lastSecond=second;
            countdownTitle(server,second);
        }
    }

    private static void countdownTitle(MinecraftServer server, int second) {
        // Native vanilla commands mimic the existing AllBlocks 3-second countdown.
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(),
                "title @a title {\"text\":\""+second+"\",\"color\":\"gold\",\"bold\":true}");
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(),
                "execute as @a at @s run playsound minecraft:block.note_block.pling master @s ~ ~ ~ 0.65 "+(second==3?0.93:second==2?1.1:1.3));
    }

    private static void persist(MinecraftServer server,Session s) {
        ChallengeOptionsData.get(server.overworld()).update(s.difficulty,s.resetTime,s.raidStride);
    }
    private static Session controlled(MinecraftServer server, ServerPlayer player) {
        Session s=SESSIONS.get(server);
        if(s==null||s.countdown>=0||!s.owner.equals(player.getUUID())
                ||ChallengeData.get(server.overworld()).running()) {
            player.sendSystemMessage(Component.literal("§c설정 권한이 없거나 이미 시작되었습니다. /beddefense"));
            return null;
        }
        return s;
    }

    private static MutableComponent choice(String label, String command, boolean active) {
        return Component.literal(label).withStyle(style -> style
                .withColor(active?ChatFormatting.GREEN:ChatFormatting.GRAY)
                .withBold(active)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("선택: "+label))));
    }

    /** Dedicated pale-lime primary action, distinct from the gray inactive options. */
    static MutableComponent confirmStartButton() {
        return Component.literal(" [설정 완료 · 시작] ").withStyle(style -> style
                .withColor(TextColor.fromRgb(0xA8FF73))
                .withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/beddefense setup confirm"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("설정을 확정하고 3초 후 시작합니다"))));
    }

    public static void show(ServerPlayer player, Session s) {
        ChallengeMenu.clearMenuChat(player);
        player.sendSystemMessage(Component.literal("§6§l──── 베드 디펜스 · 게임 설정 ────"));
        player.sendSystemMessage(Component.literal("§f난이도: §e"+s.difficulty+" §7(어려움: 새 레이드 테스트)"));
        player.sendSystemMessage(choice(" [쉬움] ","/beddefense setup difficulty easy",s.difficulty.equals("쉬움"))
                .append(choice("[보통] ","/beddefense setup difficulty normal",s.difficulty.equals("보통")))
                .append(choice("[어려움]","/beddefense setup difficulty hard",s.difficulty.equals("어려움"))));
        player.sendSystemMessage(Component.literal(" "));
        player.sendSystemMessage(Component.literal("§f시작 시 인게임 시간"));
        player.sendSystemMessage(choice(" [현재 시간 유지] ","/beddefense setup time keep",!s.resetTime)
                .append(choice("[1일차 아침으로 초기화]","/beddefense setup time reset",s.resetTime)));
        player.sendSystemMessage(Component.literal(" "));
        player.sendSystemMessage(Component.literal("§f레이드 편성 진행: §e×"+s.raidStride+" §7(매일 레이드 1회)"));
        player.sendSystemMessage(Component.literal("§7×2 선택 시 실제 1·2·3일차에 편성표 1·3·5일차가 적용됩니다."));
        player.sendSystemMessage(choice(" [×1] ","/beddefense setup stride 1",!s.manual&&s.raidStride==1)
                .append(choice("[×2] ","/beddefense setup stride 2",!s.manual&&s.raidStride==2))
                .append(choice("[×3] ","/beddefense setup stride 3",!s.manual&&s.raidStride==3))
                .append(choice("[×5] ","/beddefense setup stride 5",!s.manual&&s.raidStride==5))
                .append(choice("[수동]","/beddefense setup manual",s.manual)));
        if(s.manual){
            player.sendSystemMessage(choice(" [-5] ","/beddefense setup adjust -5",false)
                    .append(choice("[-1] ","/beddefense setup adjust -1",false))
                    .append(Component.literal(" §e"+"×"+s.raidStride+" "))
                    .append(choice("[+1] ","/beddefense setup adjust 1",false))
                    .append(choice("[+5]","/beddefense setup adjust 5",false)));
        }
        player.sendSystemMessage(Component.literal(" "));
        player.sendSystemMessage(confirmStartButton()
                .append(choice("[취소]","/beddefense setup cancel",false)));
    }
}
