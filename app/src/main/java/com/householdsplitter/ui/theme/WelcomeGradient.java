package com.householdsplitter.ui.theme;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import androidx.annotation.ColorInt;

import com.google.android.material.color.MaterialColors;
import com.householdsplitter.R;

/**
 * The introduction's background: a deep wash of whichever palette is switched on.
 *
 * <p>Built from {@code colorPrimary} rather than written down, so the first thing a new
 * install shows is the theme that install is actually running. Switch to Olive and the
 * introduction is olive. None of this is a colour constant, which is what stops the
 * introduction drifting away from the rest of the app the next time a palette is added.
 *
 * <p>The awkward part is that {@code colorPrimary} is dark in the light theme and light in
 * the dark one, and the text on top of this is white in both. So the shade is not chosen by
 * branching on the theme, which would be two rules to keep in step; it is chosen by
 * measurement. The colour is darkened until white text on it clears
 * {@link #MIN_CONTRAST}, whatever it started as. A light lilac primary lands in the same
 * place as a dark violet one, and a palette added in a year gets the same treatment without
 * anybody remembering this file exists.
 */
public final class WelcomeGradient {

    /**
     * White text on the lightest part of the gradient. Above the WCAG AA floor of 4.5 on
     * purpose: this is large text over a gradient, and the figure should hold at the
     * lightest point rather than on average.
     */
    private static final double MIN_CONTRAST = 5.0;

    /** How much darker the foot of the gradient is than its head. */
    private static final int FOOT_DARKER_PERCENT = 28;

    /** A first lift, so a very dark primary still reads as a gradient and not a flat block. */
    private static final int HEAD_LIGHTEN_PERCENT = 10;

    private WelcomeGradient() {
    }

    /** The gradient for this context's current theme. */
    public static GradientDrawable forTheme(Context context) {
        @ColorInt int primary = MaterialColors.getColor(
                context, androidx.appcompat.R.attr.colorPrimary,
                androidx.core.content.ContextCompat.getColor(context, R.color.store_walmart));
        return from(head(primary));
    }

    /** Visible for testing: the head colour this primary produces. */
    public static int head(@ColorInt int primary) {
        int candidate = blend(primary, Color.WHITE, HEAD_LIGHTEN_PERCENT);
        // Darken in small steps until white sits comfortably on it. Terminates because
        // black gives a contrast of 21 and every step moves towards black.
        for (int step = 0; step < 100 && contrast(candidate, Color.WHITE) < MIN_CONTRAST; step++) {
            candidate = blend(candidate, Color.BLACK, 5);
        }
        return candidate;
    }

    private static GradientDrawable from(@ColorInt int head) {
        int foot = blend(head, Color.BLACK, FOOT_DARKER_PERCENT);
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{head, foot});
        gradient.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        return gradient;
    }

    /** {@code percent} of the way from one colour to another. */
    static int blend(@ColorInt int from, @ColorInt int to, int percent) {
        return Color.rgb(
                mix(Color.red(from), Color.red(to), percent),
                mix(Color.green(from), Color.green(to), percent),
                mix(Color.blue(from), Color.blue(to), percent));
    }

    private static int mix(int from, int to, int percent) {
        return from + (to - from) * percent / 100;
    }

    /** WCAG relative contrast between two opaque colours. */
    static double contrast(@ColorInt int a, @ColorInt int b) {
        double la = luminance(a);
        double lb = luminance(b);
        double lighter = Math.max(la, lb);
        double darker = Math.min(la, lb);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(@ColorInt int colour) {
        return 0.2126 * channel(Color.red(colour))
                + 0.7152 * channel(Color.green(colour))
                + 0.0722 * channel(Color.blue(colour));
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
