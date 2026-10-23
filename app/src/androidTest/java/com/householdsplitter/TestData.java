package com.householdsplitter;

import android.content.Context;
import android.content.SharedPreferences;

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

    /**
     * Where the harness records that this install is its own.
     *
     * <p>Its own preferences file, not the app's settings, because {@link #wipe()} clears
     * those and a claim that a wipe erases is no claim at all.
     */
    private static final String CLAIM_PREFS = "test-harness-claim";
    private static final String CLAIMED_AT = "claimedAt";

    /**
     * Deletes every household, which cascades to everything else.
     *
     * <p>Refuses to run on an install the harness did not start from empty.
     *
     * <p>This is here because of what happened without it. Twenty-two test classes call
     * this method, and the obvious way to run them is {@code connectedAndroidTest} against
     * whichever device is plugged in. On a developer's emulator that deletes fixtures. On
     * the phone somebody actually keeps their receipts on, it deletes a year of them, with
     * no prompt and no undo, and the test run reports success. Nothing in the suite itself
     * distinguishes those two devices, so the suite has to be the thing that checks.
     *
     * <p>The rule is narrow on purpose: the harness may wipe an install it first saw
     * empty, and may not wipe one that already had data in it. A device that is genuinely
     * for testing is empty the first time the suite runs on it and claimed from then on,
     * which costs nothing. A device with somebody's orders on it has never been claimed,
     * so the first wipe is the one that fails, before anything is deleted.
     */
    public static void wipe() {
        requireClaim();
        database().householdDao().deleteAll();
        locator().settings().clear();
        locator().currentHouseholdId(0L);
    }

    /**
     * Claims an empty install for the harness, or fails on one that holds data.
     *
     * <p>Deliberately not overridable by a flag or an environment variable. A guard with a
     * documented way past it is a guard that gets passed, usually at the moment somebody
     * is in a hurry, which is the same moment they are most likely to have the wrong
     * device plugged in.
     */
    private static void requireClaim() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences claim =
                context.getSharedPreferences(CLAIM_PREFS, Context.MODE_PRIVATE);
        if (claim.contains(CLAIMED_AT)) {
            return;
        }
        int households = database().householdDao().countSync();
        if (households > 0) {
            throw new IllegalStateException(
                    "Refusing to wipe: this install already holds " + households
                            + " group(s) and was not created by the test harness. "
                            + "These tests delete every household on the device. Run them "
                            + "on an emulator or on a device with no real data, or clear "
                            + "this app's storage first if the data here is disposable.");
        }
        claim.edit().putLong(CLAIMED_AT, System.currentTimeMillis()).apply();
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
