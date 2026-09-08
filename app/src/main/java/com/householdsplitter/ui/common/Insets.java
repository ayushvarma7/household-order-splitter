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
 *
 * <p>Padding is right for a container: the bar's height grows and its contents move clear of
 * the system bar. It is wrong for a control, where padding inflates the control itself rather
 * than moving it, so a button ends up tall with its label adrift. Those take
 * {@link #marginTop} and {@link #marginBottom} instead.
 *
 * <p>A view given {@link #padTop} must not have a fixed height. Adding padding to a fixed
 * 56dp toolbar leaves 56dp minus the status bar for the title and the menu, which on a phone
 * with a tall status bar is a few pixels, and the overflow button simply disappears. Use
 * {@code wrap_content} with {@code minHeight}.
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

    /**
     * Adds the navigation bar inset to the view's bottom margin.
     *
     * <p>For a control rather than a container. Padding on a floating action button makes
     * the button taller and pushes its own label around inside it; a margin moves the whole
     * button clear of the gesture bar, which is what was wanted.
     */
    public static void marginBottom(View view) {
        applyMargin(view, false, true);
    }

    /** Adds the status bar and cutout inset to the view's top margin. */
    public static void marginTop(View view) {
        applyMargin(view, true, false);
    }

    private static void applyMargin(View view, boolean top, boolean bottom) {
        android.view.ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof android.view.ViewGroup.MarginLayoutParams)) {
            // Nothing sensible to do, and silently padding instead would reintroduce the
            // problem this method exists to avoid.
            return;
        }
        final int startTop = ((android.view.ViewGroup.MarginLayoutParams) params).topMargin;
        final int startBottom = ((android.view.ViewGroup.MarginLayoutParams) params).bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            androidx.core.graphics.Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
                            | WindowInsetsCompat.Type.ime());
            android.view.ViewGroup.LayoutParams current = target.getLayoutParams();
            if (current instanceof android.view.ViewGroup.MarginLayoutParams) {
                android.view.ViewGroup.MarginLayoutParams margins =
                        (android.view.ViewGroup.MarginLayoutParams) current;
                if (top) {
                    margins.topMargin = startTop + bars.top;
                }
                if (bottom) {
                    margins.bottomMargin = startBottom + bars.bottom;
                }
                target.setLayoutParams(margins);
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
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
