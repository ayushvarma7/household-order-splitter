package com.householdsplitter.widget;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Which group each placed widget is showing.
 *
 * <p>A widget is not the app. The app has one group open at a time and a drawer to change
 * it; a home screen can hold two widgets side by side, and the useful thing to put in them
 * is two different groups. So the group is a property of the placed widget rather than
 * something read out of the app's own state.
 *
 * <p>{@link #FOLLOW_APP} is the default and stays available as an explicit choice, because
 * one widget that always shows whatever you last opened is the right answer for anybody
 * with a single group, which is almost everybody. Choosing a specific group is for the case
 * where it matters.
 *
 * <p>Its own preferences file rather than a corner of the app's. These entries are keyed by
 * a widget id the launcher owns and are deleted when the widget is removed, which is a
 * different lifetime from every setting the app keeps, and mixing the two would mean the
 * app's settings file slowly filling with ids of widgets that no longer exist.
 */
public final class WidgetPreferences {

    /** Show whichever group the app currently has open. The default. */
    public static final long FOLLOW_APP = 0L;

    private static final String FILE = "widget_bindings";
    private static final String KEY_PREFIX = "household_for_";

    private final SharedPreferences preferences;

    public WidgetPreferences(Context context) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** The group this widget was configured with, or {@link #FOLLOW_APP}. */
    public long householdFor(int appWidgetId) {
        return preferences.getLong(KEY_PREFIX + appWidgetId, FOLLOW_APP);
    }

    public void householdFor(int appWidgetId, long householdId) {
        preferences.edit().putLong(KEY_PREFIX + appWidgetId, householdId).apply();
    }

    /**
     * Forgets a widget the user has removed.
     *
     * <p>Called from the provider's onDeleted. Without it the file grows by one entry every
     * time a widget is placed and removed, and a launcher reusing an id would inherit a
     * group chosen for a widget that no longer exists.
     */
    public void forget(int... appWidgetIds) {
        SharedPreferences.Editor editor = preferences.edit();
        for (int appWidgetId : appWidgetIds) {
            editor.remove(KEY_PREFIX + appWidgetId);
        }
        editor.apply();
    }
}
