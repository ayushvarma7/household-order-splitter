package com.householdsplitter.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;

import com.householdsplitter.R;

/**
 * The semantic state colours, resolved from resources rather than written into code.
 *
 * <p>Hard-coding an ARGB literal in a Fragment is what stops an app having a dark mode: the
 * value cannot change with the configuration. Every state colour here comes from
 * {@code values/colors.xml} and its {@code values-night} twin, so the same call returns a
 * readable colour in either. SPEC 11.8 makes the same argument about screenshots, and it
 * holds for the interface.
 *
 * <p>Colour is never the only signal. Each state below has a matching icon and a word in
 * the layout that uses it, so the meaning survives for a colour blind user and in a
 * greyscale screenshot.
 */
public final class StateColors {

    /** What the interface is telling the user about a value or a row. */
    public enum State {
        /** Everything reconciles, or the item is answered for. */
        SUCCESS,
        /** Worth a look, but nothing is blocked. */
        WARNING,
        /** Blocking, wrong, or destructive. */
        DANGER,
        /** No judgement attached. */
        NEUTRAL
    }

    private StateColors() {
    }

    @ColorInt
    public static int content(Context context, State state) {
        return color(context, contentRes(state));
    }

    @ColorInt
    public static int container(Context context, State state) {
        return color(context, containerRes(state));
    }

    @ColorInt
    public static int onContainer(Context context, State state) {
        return color(context, onContainerRes(state));
    }

    /** Paints a banner or pill: container behind, matching foreground on top. */
    public static void applyContainer(View background, TextView text, State state) {
        Context context = background.getContext();
        background.setBackgroundTintList(
                ColorStateList.valueOf(container(context, state)));
        if (text != null) {
            text.setTextColor(onContainer(context, state));
        }
    }

    /** A faint wash of the state colour, for tinting a whole list row. */
    @ColorInt
    public static int wash(Context context, State state) {
        int base = container(context, state);
        return Color.argb(110, Color.red(base), Color.green(base), Color.blue(base));
    }

    @ColorRes
    private static int contentRes(State state) {
        switch (state) {
            case SUCCESS:
                return R.color.success;
            case WARNING:
                return R.color.warning;
            case DANGER:
                return R.color.danger;
            default:
                return R.color.on_surface_variant;
        }
    }

    @ColorRes
    private static int containerRes(State state) {
        switch (state) {
            case SUCCESS:
                return R.color.success_container;
            case WARNING:
                return R.color.warning_container;
            case DANGER:
                return R.color.danger_container;
            default:
                return R.color.surface_container;
        }
    }

    @ColorRes
    private static int onContainerRes(State state) {
        switch (state) {
            case SUCCESS:
                return R.color.on_success_container;
            case WARNING:
                return R.color.on_warning_container;
            case DANGER:
                return R.color.on_danger_container;
            default:
                return R.color.on_surface_variant;
        }
    }

    @ColorInt
    private static int color(Context context, @ColorRes int res) {
        return ContextCompat.getColor(context, res);
    }
}
