package com.arcaneforge.forge;

import com.arcaneforge.config.ArcaneForgeConfig;

/** Result of a single hammer strike in the anvil rhythm minigame. */
public enum ForgeRating {
    MISS(0, 0xFF4040, "miss"),
    OK(1, 0x7FD8FF, "ok"),
    GOOD(2, 0x7CFC00, "good"),
    PERFECT(3, 0xFFD700, "perfect");

    public static final int MAX_SCORE = 3;

    private final int score;
    private final int color;
    private final String key;

    ForgeRating(int score, int color, String key) {
        this.score = score;
        this.color = color;
        this.key = key;
    }

    public int score() {
        return score;
    }

    public int color() {
        return color;
    }

    public String key() {
        return key;
    }

    public static ForgeRating byOrdinal(int ordinal) {
        ForgeRating[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    /** Rates a strike from the normalized distance between the shrinking ring and the target radius. */
    public static ForgeRating fromDistance(double distance, double perfect, double good, double ok) {
        if (distance <= perfect) return PERFECT;
        if (distance <= good) return GOOD;
        if (distance <= ok) return OK;
        return MISS;
    }

    public static ForgeRating fromDistance(double distance, ArcaneForgeConfig.Forging cfg) {
        return fromDistance(distance, cfg.perfectWindow, cfg.goodWindow, cfg.okWindow);
    }

    /**
     * Converts the accumulated strike score into a 1-5 star quality.
     * All perfect strikes yield 5 stars; all misses yield 1 star.
     */
    public static int starsFor(int scoreSum, int strikes) {
        if (strikes <= 0) {
            return 1;
        }
        double ratio = (double) scoreSum / (strikes * (double) MAX_SCORE);
        int stars = 1 + (int) Math.round(ratio * 4.0);
        return Math.max(1, Math.min(5, stars));
    }
}
