package com.householdsplitter;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.di.ServiceLocator;

/**
 * Puts the app back to a fresh install between tests.
 *
 * <p>SPEC 12.5.1 and 12.6 both depend on "fresh install" meaning genuinely empty, and the
 * app ships no seed of any kind (SPEC 1.6), so wiping is all it takes.
 */
public final class TestData {

    private TestData() {
    }

    public static ServiceLocator locator() {
        Context context = ApplicationProvider.getApplicationContext();
        return ((SplitterApp) context).serviceLocator();
    }

    public static AppDatabase database() {
        return locator().database();
    }

    /** Deletes every household, which cascades to everything else. */
    public static void wipe() {
        database().householdDao().deleteAll();
        locator().settings().clear();
        locator().currentHouseholdId(0L);
    }

    /**
     * A fresh install whose introduction has already been read, so the app opens on S1.
     *
     * <p>The screens SPEC 12.5 describes start at the group name. A genuinely untouched
     * install now opens on the introduction first, and a test about the setup flow should
     * not have to page through it, nor start failing because it exists.
     * {@link WelcomeFlowTest} covers the introduction itself.
     */
    public static void wipePastIntro() {
        wipe();
        locator().settings().welcomeSeen(true);
    }
}
