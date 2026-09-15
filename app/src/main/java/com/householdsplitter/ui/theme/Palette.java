package com.householdsplitter.ui.theme;

import androidx.annotation.StyleRes;

import com.householdsplitter.R;

/**
 * The colour schemes the user can choose between.
 *
 * <p>Each one is a theme overlay that replaces the primary roles and nothing else. The
 * surfaces, the semantic colours for success, warning and danger, and the ten validated
 * member colours are deliberately untouched by all of them: those carry meaning, and a
 * preference about liking purple is not a reason to change what "settled" looks like.
 *
 * <p>{@link #AMETHYST} has no overlay at all. It is the scheme the app already had, and
 * applying nothing is the only way to be certain it is unchanged.
 */
public enum Palette {

    AMETHYST("amethyst", R.string.palette_amethyst, 0, 0xFF6A3FC0),
    SLATE("slate", R.string.palette_slate, R.style.ThemeOverlay_Splitter_Slate, 0xFF386885),
    CLAY("clay", R.string.palette_clay, R.style.ThemeOverlay_Splitter_Clay, 0xFF984E3C),
    PLUM("plum", R.string.palette_plum, R.style.ThemeOverlay_Splitter_Plum, 0xFF97496F),
    OLIVE("olive", R.string.palette_olive, R.style.ThemeOverlay_Splitter_Olive, 0xFF666539),
    GRAPHITE("graphite", R.string.palette_graphite, R.style.ThemeOverlay_Splitter_Graphite,
            0xFF67616D);

    private final String key;
    private final int labelRes;
    private final int overlayRes;
    private final int swatch;

    Palette(String key, int labelRes, @StyleRes int overlayRes, int swatch) {
        this.key = key;
        this.labelRes = labelRes;
        this.overlayRes = overlayRes;
        this.swatch = swatch;
    }

    /** Stored by name, so reordering this enum cannot reinterpret somebody's choice. */
    public String key() {
        return key;
    }

    public int labelRes() {
        return labelRes;
    }

    /** Zero when this scheme needs no overlay, which is true of the original only. */
    @StyleRes
    public int overlayRes() {
        return overlayRes;
    }

    /**
     * The light mode accent, for the dot beside the name in Settings.
     *
     * <p>Held here rather than resolved from the overlay because the picker shows every
     * scheme at once, including the five that are not currently applied, and a theme can
     * only resolve the one it is.
     */
    public int swatch() {
        return swatch;
    }

    /** Falls back to the original scheme, which is what every existing install has. */
    public static Palette fromKey(String value) {
        if (value != null) {
            for (Palette palette : values()) {
                if (palette.key.equals(value)) {
                    return palette;
                }
            }
        }
        return AMETHYST;
    }
}
