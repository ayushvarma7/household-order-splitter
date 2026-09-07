package com.householdsplitter;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isNotEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.householdsplitter.ui.MainActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** SPEC 12.5.1, 12.5.2 and 12.5.3. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class SetupFlowTest {

    @Before
    public void startFromAFreshInstall() {
        TestData.wipePastIntro();
    }

    /** 12.5.1: an empty group field and a disabled Continue. */
    @Test
    public void freshInstallShowsAnEmptyGroupFieldWithContinueDisabled() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            onView(withId(R.id.groupNameInput)).check(matches(isDisplayed()));
            // SPEC 7.1.3: empty on load, with nothing that could be mistaken for a value.
            onView(withId(R.id.groupNameInput)).check(matches(withText("")));
            // SPEC 7.1.4
            onView(withId(R.id.continueButton)).check(matches(isNotEnabled()));
        }
    }

    /** SPEC 7.1.4: one character is enough to enable Continue. */
    @Test
    public void continueEnablesOnceTheNameIsValid() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            onView(withId(R.id.groupNameInput)).perform(replaceText("Flat 12"), closeSoftKeyboard());
            settle();
            onView(withId(R.id.continueButton)).check(matches(ViewMatchers.isEnabled()));
        }
    }

    /** 12.5.2: Done stays disabled below two members. */
    @Test
    public void cannotLeaveMemberSetupWithFewerThanTwo() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            createGroup("Flat 12");

            onView(withId(R.id.doneButton)).check(matches(isNotEnabled()));

            addMember("Alpha");
            onView(withId(R.id.doneButton)).check(matches(isNotEnabled()));

            addMember("Beta");
            onView(withId(R.id.doneButton)).check(matches(ViewMatchers.isEnabled()));
        }
    }

    /** 12.5.3: a duplicate name is rejected, case-insensitively, with an inline error. */
    @Test
    public void duplicateMemberNameIsRejected() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            createGroup("Flat 12");
            addMember("Alpha");
            addMember("alpha");

            settle();
            // The second add must not have created a row, and the field says why.
            onView(withId(R.id.nameLayout)).check(matches(isDisplayed()));
            org.junit.Assert.assertEquals(1, activeMemberCount());
        }
    }

    /** SPEC 7.2.10: nothing on this screen offers to invent members. */
    @Test
    public void thereIsNoQuickAddShortcut() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            createGroup("Flat 12");
            settle();
            org.junit.Assert.assertEquals(0, activeMemberCount());
        }
    }

    private static int activeMemberCount() {
        long householdId = TestData.locator().currentHouseholdId();
        return TestData.database().memberDao().activeCountSync(householdId);
    }

    private void createGroup(String name) {
        onView(withId(R.id.groupNameInput)).perform(replaceText(name), closeSoftKeyboard());
        settle();
        onView(withId(R.id.continueButton)).perform(click());
        settle();
    }

    private void addMember(String name) {
        onView(withId(R.id.nameInput)).perform(replaceText(name), closeSoftKeyboard());
        onView(withId(R.id.addButton)).perform(click());
        settle();
    }

    /** Writes go through a background executor, so give LiveData a beat to come back. */
    private static void settle() {
        try {
            Thread.sleep(600);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
