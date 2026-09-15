package com.householdsplitter;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.parse.StoreKind;
import com.householdsplitter.core.quality.ItemOrigin;
import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.ui.MainActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * That the report can actually be opened.
 *
 * <p>It could not, before. It sat behind eight taps on unstyled caption text, which is not
 * a feature anyone can use, and no test caught that because every test drove the service
 * directly rather than the screen. This one goes through the interface.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ParserReportReachableTest {

    @Before
    public void setUp() {
        TestData.wipe();
        AppDatabase database = TestData.database();
        long householdId = database.householdDao().insert(new Household("Fixture Group", 1L));

        Order order = new Order();
        order.householdId = householdId;
        order.label = "Sep 06 Amazon Fresh";
        order.orderDate = 1L;
        order.createdAt = 1L;
        order.store = StoreKind.AMAZON_FRESH;
        long orderId = database.orderDao().insert(order);

        LineItem read = new LineItem();
        read.orderId = orderId;
        read.name = "Amazon Grocery, Broccoli Florets, 12 Oz, Frozen";
        read.rawOcrText = read.name;
        read.lineTotalCents = 116L;
        read.scope = Scope.UNASSIGNED;
        read.origin = ItemOrigin.PARSED;
        read.parsedName = read.name;
        read.parsedCents = 116L;
        database.lineItemDao().insert(read);

        LineItem typed = new LineItem();
        typed.orderId = orderId;
        typed.name = "Plum Roma Tomato";
        typed.rawOcrText = "";
        typed.lineTotalCents = 174L;
        typed.scope = Scope.UNASSIGNED;
        typed.origin = ItemOrigin.MANUAL;
        database.lineItemDao().insert(typed);
    }

    private static void settle() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .waitForIdleSync();
        try {
            Thread.sleep(400L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .waitForIdleSync();
    }

    @Test
    public void theReportOpensFromSettingsAndNamesTheMissedRow() {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            settle();
            scenario.onActivity(activity -> androidx.navigation.Navigation
                    .findNavController(activity, R.id.nav_host)
                    .navigate(R.id.settingsFragment));
            settle();

            // The headline, on the Settings screen itself, without opening anything: one
            // row read correctly of two judged.
            onView(withId(R.id.parserReportSummary)).perform(scrollTo())
                    .check(matches(withText(containsString("50.0%"))));

            onView(withId(R.id.parserReportButton)).perform(scrollTo(), click());
            settle();

            // And the detail, naming the row the reader missed.
            onView(withId(R.id.reportText))
                    .check(matches(withText(containsString("Plum Roma Tomato"))));
            onView(withId(R.id.reportText))
                    .check(matches(withText(containsString("MISSED"))));
        }
    }
}
