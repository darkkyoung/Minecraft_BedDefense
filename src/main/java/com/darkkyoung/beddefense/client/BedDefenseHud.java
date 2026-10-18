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
    private static int serverBedState; // 0 unregistered/unplaced, 1 placed; no HP mechanics yet.

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
    private static long receivedAtClientTick;
    private static boolean wasInWorld;

    private BedDefenseHud() {}

    static void acceptServerTime(long dayTime, long elapsedWorldTicks) {
        Minecraft mc = Minecraft.getInstance();
        // Use the same client-world tick reference in the packet callback and rendering.
        receivedAtClientTick = mc.level != null ? mc.level.getGameTime() : 0L;
        serverDayTime = dayTime;
        serverElapsedTime = elapsedWorldTicks;
        synced = true;
    }

    static void acceptBedState(int state) { serverBedState = state; }

    static void onDisconnected() {
        if (wasInWorld) {
            synced = false;
            preview = Preview.OFF;
            serverBedState = 0;
            receivedAtClientTick = 0;
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
        long playedTicks = synced ? serverElapsedTime + elapsedSinceSync : -1L;

        long day = dayTime >= 0 ? Math.floorDiv(dayTime, 24000L) + 1L : -1L;
        String dayLabel = day >= 0 ? day + "일차" : "--일차";
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

        // Render using vanilla Minecraft boss-bar sprites, not a custom rectangle.
        // The bar is 25% of the former width; label identifies what it represents.
        int right = screenWidth - EDGE;
        String label = "스폰 침대";
        graphics.text(font, label, right - font.width(label), 11, WHITE, true);
        int displayWidth = Math.round(BAR_WIDTH * BAR_SCALE);
        int barX = Math.max(EDGE, right - displayWidth);
        int barY = 25;
        int effectiveState = switch (preview) {
            case OFF -> serverBedState;
            case PLACED -> 1;
            case UNPLACED -> 0;
            case DESTROYED -> 2;
        };
        String color = effectiveState == 1 ? "green" : effectiveState == 2 ? "red" : "yellow";
        int progress = (preview == Preview.PLACED) ? Math.round(BAR_WIDTH * 0.86F) : BAR_WIDTH;
        Identifier background = Identifier.fromNamespaceAndPath("minecraft", "boss_bar/" + color + "_background");
        Identifier foreground = Identifier.fromNamespaceAndPath("minecraft", "boss_bar/" + color + "_progress");
        graphics.pose().pushMatrix();
        graphics.pose().translate(barX, barY);
        graphics.pose().scale(BAR_SCALE, 1.0F);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, background, 0, 0, BAR_WIDTH, BAR_HEIGHT);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, foreground,
                BAR_WIDTH, BAR_HEIGHT, 0, 0, 0, 0, progress, BAR_HEIGHT);
        graphics.pose().popMatrix();

        long toRaid = ticksUntilBedtime(dayTime);
        if (toRaid > 0L && toRaid <= WARNING_DURATION_TICKS) {
            String warning = "레이드까지 " + formatWarning(toRaid) + "초...";
            drawCentered(graphics, font, warning, screenWidth, 10, RAID_ORANGE);
        }

        if (preview != Preview.OFF) {
            // All raid indicators are visibly tagged as preview to avoid implying live gameplay.
            String previewTitle = "[미리보기] 레이드 몬스터 12 / 16";
            String bedAlert = "스폰 침대 공격 중! [미리보기]";
            drawCentered(graphics, font, previewTitle, screenWidth, 38, WHITE);
            if (preview == Preview.PLACED) {
                drawCentered(graphics, font, bedAlert, screenWidth, 52, RED);
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
