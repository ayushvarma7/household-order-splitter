package com.householdsplitter.ui.common;

/**
 * SPEC 7.2.6: colours are assigned round-robin from a palette of at least eight visually
 * distinct values.
 *
 * <p>Ten values, chosen to stay distinguishable for the common forms of colour blindness.
 * The colour is decoration: SPEC accessibility and PROMPT Phase 6.3 forbid meaning being
 * carried by colour alone, so every avatar also shows the member's initials and every chip
 * its name.
 */
public final class MemberPalette {

    /**
     * The stored value, which is what a member row keeps in the database. It is a light
     * palette value; {@link #resolve} maps it to the readable equivalent for the current
     * configuration, so a household created in light mode still has legible avatars at
     * night without rewriting a single row.
     */
    private static final String[] COLORS = {
            "#1F6FB2", "#B3261E", "#2E7D4F", "#B25E00", "#7A4FBF",
            "#8C5A3C", "#00707D", "#7A7300", "#B03A7A", "#525968"
    };

    private static final int[] RESOURCES = {
            com.householdsplitter.R.color.member_1, com.householdsplitter.R.color.member_2,
            com.householdsplitter.R.color.member_3, com.householdsplitter.R.color.member_4,
            com.householdsplitter.R.color.member_5, com.householdsplitter.R.color.member_6,
            com.householdsplitter.R.color.member_7, com.householdsplitter.R.color.member_8,
            com.householdsplitter.R.color.member_9, com.householdsplitter.R.color.member_10
    };

    private MemberPalette() {
    }

    public static int size() {
        return COLORS.length;
    }

    /** Round-robin, so the eleventh member reuses the first colour. */
    public static String colorForIndex(int index) {
        int safe = index % COLORS.length;
        if (safe < 0) {
            safe += COLORS.length;
        }
        return COLORS[safe];
    }

    /**
     * Maps a stored colour to the palette entry for the current configuration, so avatars
     * stay legible in dark mode without migrating any data. An unrecognised value is parsed
     * as-is, and a malformed one falls back rather than crashing.
     */
    public static int resolve(android.content.Context context, String colorHex) {
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i].equalsIgnoreCase(colorHex)) {
                return androidx.core.content.ContextCompat.getColor(context, RESOURCES[i]);
            }
        }
        try {
            return android.graphics.Color.parseColor(colorHex);
        } catch (IllegalArgumentException | NullPointerException malformed) {
            return androidx.core.content.ContextCompat.getColor(context, RESOURCES[0]);
        }
    }

    /** Falls back to the first colour rather than crashing on a malformed stored value. */
    public static int parse(String colorHex) {
        try {
            return android.graphics.Color.parseColor(colorHex);
        } catch (IllegalArgumentException | NullPointerException malformed) {
            return android.graphics.Color.parseColor(COLORS[0]);
        }
    }
}
