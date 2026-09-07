package com.householdsplitter.ui.common;

import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Edge-to-edge padding.
 *
 * <p>The app draws behind the status bar and the gesture bar, so every screen has to inset
 * itself from the real system bars rather than guessing a height. Guessing is what produces
 * a title sitting under the clock on one device and a gap on another, and it is exactly the
 * kind of fixed-size assumption that breaks the moment the app runs on a different phone, a
 * tablet, or a foldable mid-fold.
 *
 * <p>The view's own padding is captured once and the inset is added to it, so a layout can
 * still express its own spacing in tokens.
 */
public final class Insets {

    private Insets() {
    }

    /** Adds the status bar and cutout inset to the view's existing top padding. */
    public static void padTop(View view) {
        apply(view, true, false, false);
    }

    /** Adds the navigation bar, or the keyboard when it is showing, below the view. */
    public static void padBottom(View view) {
        apply(view, false, true, false);
    }

    public static void padTopAndBottom(View view) {
        apply(view, true, true, false);
    }

    /** For a scrolling region: bottom inset only, applied as padding that content can scroll under. */
    public static void padBottomScrollable(View view) {
        apply(view, false, true, true);
    }

    private static void apply(View view, boolean top, boolean bottom, boolean clipDisabled) {
        final int startTop = view.getPaddingTop();
        final int startBottom = view.getPaddingBottom();
        final int startLeft = view.getPaddingLeft();
        final int startRight = view.getPaddingRight();
        if (clipDisabled && view instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) view).setClipToPadding(false);
        }
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            androidx.core.graphics.Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
                            | WindowInsetsCompat.Type.ime());
            target.setPadding(
                    startLeft + bars.left,
                    top ? startTop + bars.top : startTop,
                    startRight + bars.right,
                    bottom ? startBottom + bars.bottom : startBottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
