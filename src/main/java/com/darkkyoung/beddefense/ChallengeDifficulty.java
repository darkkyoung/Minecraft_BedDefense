package com.darkkyoung.beddefense;

/** AllBlocks-style three preset values; balance modifiers are not active yet. */
public enum ChallengeDifficulty {
    EASY("쉬움"), NORMAL("보통"), HARD("어려움");
    public final String display;
    ChallengeDifficulty(String display) { this.display = display; }
    public static ChallengeDifficulty parse(String key) {
        return switch (key) { case "easy" -> EASY; case "hard" -> HARD; default -> NORMAL; };
    }
    public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
}
