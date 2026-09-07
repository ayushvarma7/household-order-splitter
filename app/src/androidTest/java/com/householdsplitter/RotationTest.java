package com.householdsplitter;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.ui.MainActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * SPEC 12.5.7 and 7.1.6: rotation preserves state.
 *
 * <p>{@code recreate()} tears the Activity down and rebuilds it exactly as a configuration
 * change does, which is the same path a rotation takes, and a stricter one than turning the
 * device: it also proves the state survives the Activity being destroyed outright.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class RotationTest {

    @Before
    public void startClean() {
        TestData.wipe();
    }

    /** SPEC 7.1.6: the typed group name survives. */
    @Test
    public void groupSetupKeepsWhatWasTyped() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            onView(withId(R.id.groupNameInput))
                    .perform(replaceText("Fixture Group"), closeSoftKeyboard());
            settle();

            scenario.recreate();
            settle();

            onView(withId(R.id.groupNameInput)).check(matches(withText("Fixture Group")));
            onView(withId(R.id.continueButton)).check(matches(
                    androidx.test.espresso.matcher.ViewMatchers.isEnabled()));
        }
    }

    /** The member list is in the database, so it has to come back whatever happens. */
    @Test
    public void memberListSurvivesRecreation() {
        AppDatabase database = TestData.database();
        long householdId = database.householdDao().insert(new Household("Fixture Group", 1L));
        database.memberDao().insert(new Member(householdId, "Alpha", "#1F6FB2", 0));
        database.memberDao().insert(new Member(householdId, "Beta", "#B3261E", 1));
        TestData.locator().currentHouseholdId(householdId);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            scenario.recreate();
            settle();
            // Home renders from the same data after the rebuild. This household has no
            // orders yet, so the empty state of SPEC 7.3.5 is what should be showing, and
            // the group name is what proves the data came back.
            onView(withId(R.id.emptyState)).check(matches(isDisplayed()));
            onView(withId(R.id.memberSummary)).check(matches(withText("2 people")));
        }
    }

    /** Home survives, and does not bounce back to onboarding (SPEC 7.1.5). */
    @Test
    public void homeDoesNotFallBackToOnboardingAfterRecreation() {
        AppDatabase database = TestData.database();
        long householdId = database.householdDao().insert(new Household("Fixture Group", 1L));
        database.memberDao().insert(new Member(householdId, "Alpha", "#1F6FB2", 0));
        database.memberDao().insert(new Member(householdId, "Beta", "#B3261E", 1));
        TestData.locator().currentHouseholdId(householdId);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            for (int rotation = 0; rotation < 3; rotation++) {
                scenario.recreate();
                settle();
            }
            onView(withId(R.id.newOrderFab)).check(matches(isDisplayed()));
        }
    }

    private static void settle() {
        try {
            Thread.sleep(1200);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
