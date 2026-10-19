package com.darkkyoung.beddefense;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/**
 * Three-second AllBlocks-style start countdown. No game settings are mutable
 * after start; in-progress worlds always retain their SavedData.
 */
public final class ChallengeSetupManager {
    public static final class Setup {
        public final UUID owner;
        public ChallengeDifficulty difficulty = ChallengeDifficulty.NORMAL;
        public boolean resetWorldTime;
        public int raidStride = 1;
        public long countdownEnds = -1;
        private int lastSecond = 4;
        Setup(UUID owner) { this.owner = owner; }
    }
    private static final Map<MinecraftServer, Setup> SESSIONS = new HashMap<>();
    private ChallengeSetupManager() {}

    public static void open(ServerPlayer player, MinecraftServer server) {
        if (ChallengeData.get(server.overworld()).isStarted()) {
            ChallengeMenu.show(player, server);
            return;
        }
        var existing = SESSIONS.get(server);
        if (existing != null && !existing.owner.equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§c다른 플레이어가 설정 중입니다."));
            return;
        }
        var setup = existing == null ? new Setup(player.getUUID()) : existing;
        SESSIONS.put(server, setup);
        ChallengeMenu.showSettings(player, setup);
    }

    private static Setup controlled(ServerPlayer player, MinecraftServer server) {
        var s = SESSIONS.get(server);
        if (s == null || !s.owner.equals(player.getUUID()) || s.countdownEnds >= 0
                || ChallengeData.get(server.overworld()).isStarted())
            return null;
        return s;
    }

    public static void difficulty(ServerPlayer player, MinecraftServer server, String key) {
        var s=controlled(player,server);
        if(s==null) {player.sendSystemMessage(Component.literal("§e설정 화면을 먼저 여세요."));return;}
        s.difficulty=ChallengeDifficulty.parse(key);
        ChallengeMenu.showSettings(player,s);
    }
    public static void clock(ServerPlayer player, MinecraftServer server, boolean reset) {
        var s=controlled(player,server);
        if(s==null){player.sendSystemMessage(Component.literal("§e설정 화면을 먼저 여세요."));return;}
        s.resetWorldTime=reset;
        ChallengeMenu.showSettings(player,s);
    }
    public static void stride(ServerPlayer player, MinecraftServer server, int value, boolean relative) {
        var s=controlled(player,server);
        if(s==null){player.sendSystemMessage(Component.literal("§e설정 화면을 먼저 여세요."));return;}
        s.raidStride=Math.max(1,Math.min(10,relative?s.raidStride+value:value));
        ChallengeMenu.showSettings(player,s);
    }
    public static void cancel(ServerPlayer player, MinecraftServer server) {
        var s=controlled(player,server);
        if(s!=null) SESSIONS.remove(server);
        ChallengeMenu.show(player,server);
    }

    public static void confirm(ServerPlayer player, MinecraftServer server) {
        var s=controlled(player,server);
        if(s==null){player.sendSystemMessage(Component.literal("§e설정을 먼저 완료하세요."));return;}
        s.countdownEnds=server.overworld().getGameTime()+60;
        s.lastSecond=4;
        server.getPlayerList().broadcastSystemMessage(Component.literal("§6[베드 디펜스] §f3초 후 시작합니다!"),false);
    }

    public static void tick(MinecraftServer server) {
        var s=SESSIONS.get(server);
        if(s==null || s.countdownEnds<0)return;
        if(ChallengeData.get(server.overworld()).isStarted()) {SESSIONS.remove(server);return;}
        long remaining=s.countdownEnds-server.overworld().getGameTime();
        int seconds=Math.max(0,(int)((remaining+19)/20));
        if(seconds>0 && seconds<s.lastSecond){
            s.lastSecond=seconds;
            title(server,String.valueOf(seconds),"gold");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "playsound minecraft:block.note_block.pling master @a ~ ~ ~ 1 1");
        }
        if(remaining>0)return;
        SESSIONS.remove(server);
        if(s.resetWorldTime){
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "time of minecraft:overworld rate 1");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "time of minecraft:overworld resume");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "time of minecraft:overworld set 0");
        }
        var state=ChallengeData.get(server.overworld());
        if(state.start(server.overworld().getGameTime(),ChallengeMenu.overworldClock(server),
                s.difficulty,s.raidStride,s.resetWorldTime)){
            title(server,"시작!","green");
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "§a[베드 디펜스] §f챌린지 시작 · 난이도 "+s.difficulty.display
                    +" · 편성 진행 ×"+s.raidStride+" · 1일차"),false);
        }
    }

    private static void title(MinecraftServer server,String text,String color) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                "title @a times 0 20 5");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                "title @a title {\"text\":\""+text+"\",\"color\":\""+color+"\",\"bold\":true}");
    }
}
