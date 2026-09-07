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

    private static final String[] COLORS = {
            "#1F77B4", "#D62728", "#2CA02C", "#FF7F0E", "#9467BD",
            "#8C564B", "#17BECF", "#BCBD22", "#E377C2", "#7F7F7F"
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

    /** Falls back to the first colour rather than crashing on a malformed stored value. */
    public static int parse(String colorHex) {
        try {
            return android.graphics.Color.parseColor(colorHex);
        } catch (IllegalArgumentException | NullPointerException malformed) {
            return android.graphics.Color.parseColor(COLORS[0]);
        }
    }
}
