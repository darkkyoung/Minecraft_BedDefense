package com.darkkyoung.beddefense.client;

/**
 * Client-only presentation state for the first Bed Defense introduction.
 * No ChatOptions/GameOptions value is modified or saved to disk.
 * The vanilla chat height is restored immediately on all other menu packets.
 */
public final class WelcomeChatDisplay {
    private static final int WELCOME_HEIGHT = 240; // GUI-scaled chat pixels, enough for wrapped Korean lines.
    private static final int VIEWPORT_TOP_MARGIN = 70;
    private static boolean showing;

    private WelcomeChatDisplay() { }

    public static void show(boolean welcome) { showing = welcome; }

    public static boolean showing() { return showing; }

    /**
     * ChatComponent.getHeight() returns internal chat pixels before chat-scale application.
     * Reserve a margin for other HUD overlays and respect unusually small windows.
     */
    public static int height(int vanillaHeight, int guiHeight, double chatScale) {
        if (!showing || !Double.isFinite(chatScale) || chatScale <= 0) return vanillaHeight;
        int fit = (int)Math.floor(Math.max(0,guiHeight - VIEWPORT_TOP_MARGIN) / chatScale);
        return Math.max(vanillaHeight,Math.min(WELCOME_HEIGHT,fit));
    }
}
