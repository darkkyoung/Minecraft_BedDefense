package com.darkkyoung.beddefense.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

/**
 * Frameless vanilla-font HUD. LIVE fields: global Overworld clock, player coordinates, biome.
 * Preview-only fields: Spawn Bed HP and raid status. Never advertise mock values as gameplay.
 */
public final class BedDefenseHud {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int DIM = 0xFFE0E0E0;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int RED = 0xFFFF6666;
    private static final int GREEN = 0xFF88DD99;
    private static final int EDGE = 8;
    // Approximate first vanilla bedtime. When raids are implemented, use the real scheduler.
    private static final long BEDTIME_TICK = 12542L;

    private static boolean visible = true;
    private static boolean preview = false;
    private static boolean synced = false;
    private static long serverDayTime;
    private static long receivedAtClientTick;
    private static long clientTickCounter;
    private static boolean wasInWorld;

    private BedDefenseHud() {}

    static void acceptServerTime(long value) {
        serverDayTime = value;
        receivedAtClientTick = clientTickCounter;
        synced = true;
    }

    static void onDisconnected() {
        if (wasInWorld) {
            synced = false;
            preview = false;
            wasInWorld = false;
        }
    }

    static void toggle() { visible = !visible; }
    static void togglePreview() { preview = !preview; }
    static boolean isVisible() { return visible; }
    static boolean isPreview() { return preview; }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!visible || mc.player == null || mc.level == null || mc.gui.isHidden()) {
            return;
        }
        wasInWorld = true;
        clientTickCounter = mc.level.getGameTime();
        Player player = mc.player;
        Font font = mc.font;
        int width = mc.getWindow().getGuiScaledWidth();

        long dayTime = synced ? serverDayTime + Math.max(0L, clientTickCounter - receivedAtClientTick) : -1L;
        int day = dayTime >= 0 ? (int) (Math.floorDiv(dayTime, 24000L) + 1) : -1;
        String dayLabel = day >= 0 ? "DAY " + day + " / 100" : "DAY -- / 100";

        drawLarge(graphics, font, dayLabel, EDGE, EDGE, WHITE, 1.7F);
        int x = player.blockPosition().getX();
        int y = player.blockPosition().getY();
        int z = player.blockPosition().getZ();
        graphics.text(font, String.format("X: %d  Y: %d  Z: %d", x, y, z), EDGE, 27, DIM, true);

        String timer = dayTime >= 0 ? countDown(dayTime) : "CLOCK SYNC...";
        graphics.text(font, timer, EDGE, 40, dayTime < 0 ? MUTED : WHITE, true);
        String biome = player.level().getBiome(player.blockPosition()).unwrapKey()
                .map(key -> key.identifier().getPath().replace('_', ' '))
                .orElse("unknown");
        String dimension = player.level().dimension().identifier().getPath().toUpperCase();
        graphics.text(font, "BIOME: " + biome, EDGE, 53, MUTED, true);
        graphics.text(font, dimension, EDGE, 66, MUTED, true);

        // No real Spawn Bed state implementation yet. Never show fictitious HP outside preview.
        String bedText = preview ? "BED 86 / 100" : "BED -- / --";
        int bedX = width - EDGE - Math.round(font.width(bedText) * 1.7F);
        drawLarge(graphics, font, bedText, Math.max(EDGE, bedX), EDGE,
                preview ? GREEN : WHITE, 1.7F);
        String status = preview ? "PLACED  |  PREVIEW" : "NOT REGISTERED (PROTOTYPE)";
        graphics.text(font, status, Math.max(EDGE, width - EDGE - font.width(status)),
                27, preview ? GREEN : MUTED, true);

        if (preview) {
            // Text only: no square backgrounds or decorative panels.
            String raidName = "DAY " + (day >= 0 ? day : 24) + " RAID  [PREVIEW]";
            graphics.text(font, raidName,
                    Math.max(EDGE, (width - font.width(raidName)) / 2), 8, RED, true);
            String remaining = "RAIDERS 12 / 16";
            graphics.text(font, remaining,
                    Math.max(EDGE, (width - font.width(remaining)) / 2), 22, WHITE, true);
            String alert = "SPAWN BED UNDER ATTACK!  [PREVIEW]";
            graphics.text(font, alert,
                    Math.max(EDGE, (width - font.width(alert)) / 2), 37, RED, true);
        }
    }

    private static String countDown(long absoluteTime) {
        long time = Math.floorMod(absoluteTime, 24000L);
        long until = time < BEDTIME_TICK ? BEDTIME_TICK - time : 24000L - time + BEDTIME_TICK;
        // One real second is 20 ticks at the normal game tick rate.
        long seconds = (until + 19L) / 20L;
        return String.format("NIGHT IN: %02d:%02d", seconds / 60L, seconds % 60L);
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
