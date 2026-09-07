package com.householdsplitter;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.ui.MainActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The introduction, and which screen a launch actually lands on.
 *
 * <p>There are three cases and only two of them existed before: a household means Home
 * (SPEC 7.3.1), no household means S1 (SPEC 7.1.1), and now no household with the
 * introduction unread means the introduction. The third has to not disturb the other two,
 * and the introduction has to appear exactly once, which is what this checks.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class WelcomeFlowTest {

    @Before
    public void startFromAnUntouchedInstall() {
        TestData.wipe();
    }

    @Test
    public void anUntouchedInstallOpensTheIntroduction() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            onView(withId(R.id.pager)).check(matches(isDisplayed()));
            onView(withText(R.string.welcome_1_title)).check(matches(isDisplayed()));
        }
    }

    /** Somebody who already knows what this is should not have to page through it. */
    @Test
    public void skipGoesStraightToGroupSetup() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            onView(withId(R.id.skipButton)).perform(click());
            settle();
            onView(withId(R.id.groupNameInput)).check(matches(isDisplayed()));
        }
        assertTrue("and it is not offered again",
                TestData.locator().settings().welcomeSeen());
    }

    @Test
    public void pagingThroughReachesGroupSetup() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            onView(withId(R.id.nextButton)).perform(click());
            settle();
            onView(withText(R.string.welcome_2_title)).check(matches(isDisplayed()));

            onView(withId(R.id.nextButton)).perform(click());
            settle();
            onView(withText(R.string.welcome_3_title)).check(matches(isDisplayed()));

            // The last page's button reads "Get started" and leaves the introduction.
            onView(withId(R.id.nextButton)).perform(click());
            settle();
            onView(withId(R.id.groupNameInput)).check(matches(isDisplayed()));
        }
    }

    /** Back from the second page returns to the first rather than leaving. */
    @Test
    public void backStepsThroughThePages() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            onView(withId(R.id.nextButton)).perform(click());
            settle();
            onView(withId(R.id.backButton)).perform(click());
            settle();
            onView(withText(R.string.welcome_1_title)).check(matches(isDisplayed()));
        }
    }

    /**
     * Read once, never again. The flag is what records that, rather than the presence of a
     * household: somebody who reads the introduction and closes the app before naming their
     * group should land on setup next time.
     */
    @Test
    public void aSecondLaunchGoesStraightToSetup() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            onView(withId(R.id.skipButton)).perform(click());
            settle();
        }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            onView(withId(R.id.groupNameInput)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void anUntouchedInstallHasNotSeenTheIntroduction() {
        assertFalse(TestData.locator().settings().welcomeSeen());
    }

    /** SPEC 7.3.1 is untouched: an existing household still opens on Home. */
    @Test
    public void anExistingHouseholdStillOpensOnHome() {
        long householdId = TestData.database().householdDao()
                .insert(new Household("Flat 12", 1L));
        TestData.database().memberDao()
                .insert(new Member(householdId, "Ana", "#1F6FB2", 0));
        TestData.database().memberDao()
                .insert(new Member(householdId, "Ben", "#B3261E", 1));

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            onView(withId(R.id.newOrderFab)).check(matches(isDisplayed()));
        }
    }

    private static void settle() {
        try {
            Thread.sleep(900L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
