package com.darkkyoung.beddefense.client;

import java.util.Locale;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * Korean-only, frameless HUD v2.
 * Live: Overworld date, coordinates, elapsed world-tick stopwatch, biome.
 * Simulated: Spawn Bed bar, preview raid. Never mutates Bed/raid state.
 */
public final class BedDefenseHud {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int ORANGE = 0xFFFFCC91;
    private static final int RAID_ORANGE = 0xFFFFB86B;
    private static final int RED = 0xFFE65A5A;
    private static final int YELLOW = 0xFFF3D051;
    private static final int GREEN = 0xFF78C98B;
    private static final int BAR_TRACK = 0xAA202020;
    private static final int EDGE = 12;
    // 182px vanilla boss bar, displayed at 1/4 width without stretching its pixels.
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;
    private static final float BAR_SCALE = 0.25F;
    private static int serverBedState; // 0 unregistered, 1 placed, 2 destroyed, 3 unplaced
    private static int serverBedHp;
    private static long destroyFlashUntilTick;
    private static int serverBedMax=100;
    private static int activeRaidDay;
    private static int activeRaidRemaining;
    private static int activeRaidTotal;
    private static float animatedHpRatio;
    private static long flashUntilTick;
    private static long lastVisualTick = -1L;

    // Prototype only: the approximate first time Minecraft permits sleeping.
    // Must use the real server raid scheduler when raids are implemented.
    private static final long BEDTIME_TICK = 12542L;
    private static final long WARNING_DURATION_TICKS = 20L * 60L;

    private enum Preview { OFF, PLACED, UNPLACED, DESTROYED }
    private static Preview preview = Preview.OFF;
    private static boolean visible = true;
    private static boolean synced = false;
    private static long serverDayTime;
    private static long serverElapsedTime;
    private static long serverChallengeDay = -1L;
    private static long receivedAtClientTick;
    private static boolean wasInWorld;

    private BedDefenseHud() {}

    static void acceptServerTime(long dayTime, long challengeElapsedTicks, long challengeDay) {
        Minecraft mc = Minecraft.getInstance();
        // Use the same client-world tick reference in the packet callback and rendering.
        receivedAtClientTick = mc.level != null ? mc.level.getGameTime() : 0L;
        serverDayTime = dayTime;
        serverElapsedTime = challengeElapsedTicks;
        serverChallengeDay = challengeDay;
        synced = true;
    }

    static void acceptRaidStatus(int day,int remaining,int total){
        activeRaidDay=day;activeRaidRemaining=remaining;activeRaidTotal=total;
    }

    static void acceptBedState(int state, int hp, int maxHp) {
        Minecraft mc = Minecraft.getInstance();
        long tick = mc.level != null ? mc.level.getGameTime() : 0L;
        if (serverBedState != 1 && state == 1) animatedHpRatio = 0;
        if (serverBedState == 1 && state == 1 && hp < serverBedHp) {
            flashUntilTick=tick+8;
            if (mc.player != null) mc.player.displayClientMessage(
                    Component.literal("§c스폰 침대 공격 받는 중!"), true);
        }
        if (serverBedState == 1 && state == 2) destroyFlashUntilTick = tick+12;
        serverBedState = state;
        serverBedHp = Math.max(0,hp);
        serverBedMax = Math.max(1,maxHp);
    }

    static void onDisconnected() {
        if (wasInWorld) {
            synced = false;
            preview = Preview.OFF;
            serverBedState = 0;
            serverBedHp = 0;
            activeRaidDay=0;activeRaidRemaining=0;activeRaidTotal=0;
            animatedHpRatio = 0;
            flashUntilTick = 0;
            destroyFlashUntilTick=0;
            lastVisualTick = -1;
            receivedAtClientTick = 0;
            serverChallengeDay = -1L;
            serverElapsedTime = -1L;
            wasInWorld = false;
        }
    }

    static void toggle() { visible = !visible; }
    static boolean isVisible() { return visible; }

    /** Each call cycles: installed -> removed -> permanently destroyed -> live (unknown). */
    static String nextPreview() {
        preview = switch (preview) {
            case OFF -> Preview.PLACED;
            case PLACED -> Preview.UNPLACED;
            case UNPLACED -> Preview.DESTROYED;
            case DESTROYED -> Preview.OFF;
        };
        return switch (preview) {
            case OFF -> "미리보기 해제";
            case PLACED -> "침대 설치 (체력 86%)";
            case UNPLACED -> "침대 미설치 (노란색)";
            case DESTROYED -> "침대 파괴 (빨간색)";
        };
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!visible || mc.player == null || mc.level == null || mc.gui.hud.isHidden()) {
            return;
        }
        wasInWorld = true;
        Player player = mc.player;
        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        long clientTick = mc.level.getGameTime();
        long elapsedSinceSync = synced ? Math.max(0L, clientTick - receivedAtClientTick) : 0L;
        long dayTime = synced ? serverDayTime + elapsedSinceSync : -1L;
        boolean running = synced && serverChallengeDay >= 1L && serverElapsedTime >= 0L;
        long playedTicks = running ? serverElapsedTime + elapsedSinceSync : 0L;

        long day = running ? serverChallengeDay : -1L;
        String dayLabel = running ? day + "일차" : "시작 전";
        drawLarge(graphics, font, dayLabel, EDGE, EDGE, ORANGE, 1.7F);

        graphics.text(font,
                "XYZ : " + player.blockPosition().getX()
                        + " / " + player.blockPosition().getY()
                        + " / " + player.blockPosition().getZ(),
                EDGE, 35, WHITE, true);
        graphics.text(font, "플레이 시간 : " + formatStopwatch(playedTicks),
                EDGE, 48, WHITE, true);
        graphics.text(font, "생물 군계 : " + biomeName(player),
                EDGE, 61, WHITE, true);

        // Vanilla boss bar positioned like AllBlocks progress bar at top-center.
        int barX = (screenWidth - BAR_WIDTH) / 2;
        int barY = 21;
        int state = switch(preview) {
            case OFF -> serverBedState;
            case PLACED -> 1;
            case UNPLACED -> 3;
            case DESTROYED -> 2;
        };
        float target = (state==1)
                ? (preview==Preview.PLACED ? 0.86F : Math.min(1F, (float)serverBedHp/serverBedMax))
                : (state==0 ? 0F : 1F);

        long nowTick=mc.level.getGameTime();
        if(lastVisualTick<0L)lastVisualTick=nowTick;
        long deltaTicks=Math.max(1L,Math.min(10L,nowTick-lastVisualTick));
        lastVisualTick=nowTick;
        // This easing animates initial registration, repair and raid damage.
        float step=Math.min(1F,deltaTicks*0.14F);
        animatedHpRatio += (target-animatedHpRatio)*step;
        float visibleRatio=state==1 ? animatedHpRatio : target;
        String color = state==1 ? "green" : state==2 ? "red" : state==3 ? "yellow" : "white";
        boolean flashing = state==1 && nowTick <= flashUntilTick;
        if(flashing || (state==2 && nowTick<=destroyFlashUntilTick))color="white";
        int jitter=flashing && nowTick%2==0 ? 1 : flashing ? -1 : 0;
        Identifier background=Identifier.fromNamespaceAndPath("minecraft","boss_bar/"+color+"_background");
        Identifier foreground=Identifier.fromNamespaceAndPath("minecraft","boss_bar/"+color+"_progress");
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, background, barX+jitter,barY,BAR_WIDTH,BAR_HEIGHT);
        int progress=Math.max(0,Math.min(BAR_WIDTH,Math.round(visibleRatio*BAR_WIDTH)));
        if(progress>0)graphics.blitSprite(RenderPipelines.GUI_TEXTURED, foreground,
                BAR_WIDTH,BAR_HEIGHT,0,0,barX+jitter,barY,progress,BAR_HEIGHT);

        drawCentered(graphics,font,"스폰 침대",screenWidth,8,WHITE);
        if(state==0)drawCentered(graphics,font,"스폰 침대를 지정하세요",screenWidth,32,WHITE);
        if(state==3)drawCentered(graphics,font,"스폰 침대 미설치 · 사망 시 패배",screenWidth,32,YELLOW);
        if(state==2)drawCentered(graphics,font,"스폰 침대 파괴 · 마지막 생존",screenWidth,32,RED);

        long toRaid = ticksUntilBedtime(dayTime);
        if (running && toRaid > 0L && toRaid <= WARNING_DURATION_TICKS) {
            String warning = "레이드까지 " + formatWarning(toRaid) + "초...";
            drawCentered(graphics, font, warning, screenWidth, 47, RAID_ORANGE);
        }

        if(activeRaidDay>0 && running) {
            drawCentered(graphics,font,"남은 몬스터 수 : "+activeRaidRemaining+" / "+activeRaidTotal,
                    screenWidth,47,WHITE);
        }
        if (preview != Preview.OFF) {
            // All raid indicators are visibly tagged as preview to avoid implying live gameplay.
            String previewTitle = "[미리보기] 레이드 몬스터 12 / 16";
            String bedAlert = "스폰 침대 공격 중! [미리보기]";
            drawCentered(graphics, font, previewTitle, screenWidth, 91, WHITE);
            if (preview == Preview.PLACED) {
                drawCentered(graphics, font, bedAlert, screenWidth, 104, RED);
            }
        }
    }

    private static long ticksUntilBedtime(long dayTime) {
        if (dayTime < 0) {
            return Long.MAX_VALUE;
        }
        long time = Math.floorMod(dayTime, 24000L);
        return time < BEDTIME_TICK ? BEDTIME_TICK - time : 24000L - time + BEDTIME_TICK;
    }

    private static String formatWarning(long ticks) {
        long secs = (ticks + 19L) / 20L;
        return (secs / 60) + ":" + String.format(Locale.ROOT, "%02d", secs % 60);
    }

    private static String formatStopwatch(long ticks) {
        if (ticks < 0L) {
            return "--:--:--";
        }
        long secs = ticks / 20L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d",
                secs / 3600L, (secs % 3600L) / 60L, secs % 60L);
    }

    private static String biomeName(Player player) {
        return player.level().getBiome(player.blockPosition()).unwrapKey()
                .map(key -> {
                    String translationKey = "beddefense.biome." +
                            key.identifier().getNamespace() + "." + key.identifier().getPath();
                    String translated = Component.translatable(translationKey).getString();
                    return translated.equals(translationKey) ? "알 수 없는 생물 군계" : translated;
                })
                .orElse("알 수 없는 생물 군계");
    }

    private static void drawCentered(GuiGraphicsExtractor graphics, Font font,
                                     String text, int width, int y, int color) {
        graphics.text(font, text,
                Math.max(EDGE, (width - font.width(text)) / 2), y, color, true);
    }

    private static void drawLarge(GuiGraphicsExtractor graphics, Font font, String text,
                                  int x, int y, int color, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color, true);
        graphics.pose().popMatrix();
    }
}
