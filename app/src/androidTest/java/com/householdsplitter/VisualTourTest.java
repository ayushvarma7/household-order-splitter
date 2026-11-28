package com.householdsplitter;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.DisplayMetrics;

import androidx.navigation.Navigation;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.householdsplitter.data.entity.Order;
import com.householdsplitter.ui.MainActivity;
import com.householdsplitter.ui.parsing.ParsingArgs;
import com.householdsplitter.ui.summary.SummaryFragment;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Walks every screen with seeded data and saves a screenshot of each, for checking layouts
 * across Android versions and screen sizes. Asserts nothing: a screen that cannot be
 * reached is written to {@code failures.txt} beside the images and the tour carries on.
 *
 * <p>Run it deliberately, never as part of the suite:
 * {@code adb shell am instrument -w -e class com.householdsplitter.VisualTourTest
 * com.householdsplitter.test/androidx.test.runner.AndroidJUnitRunner}
 * then pull {@code files/tour} with {@code run-as}.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class VisualTourTest {

    private final StringBuilder failures = new StringBuilder();

    private static File dir() {
        File dir = new File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getFilesDir(), "tour");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("could not create " + dir);
        }
        return dir;
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void shoot(String name) {
        pause(900L);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        // takeScreenshot returns null now and then on a busy emulator; a retry gets it.
        Bitmap bitmap = null;
        for (int attempt = 0; attempt < 6 && bitmap == null; attempt++) {
            if (attempt > 0) {
                pause(1000L);
            }
            bitmap = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().takeScreenshot();
        }
        if (bitmap == null) {
            throw new IllegalStateException("no screenshot for " + name);
        }
        try (FileOutputStream out = new FileOutputStream(new File(dir(), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception failed) {
            throw new RuntimeException("could not write " + name, failed);
        }
    }

    /** Scrolls the screen up by about half its height and shoots again. */
    private static void shootScrolled(String name) {
        DisplayMetrics m = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getResources().getDisplayMetrics();
        int x = m.widthPixels / 2;
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                "input swipe " + x + " " + (m.heightPixels * 3 / 4) + " " + x + " "
                        + (m.heightPixels / 4) + " 400");
        pause(800L);
        shoot(name);
    }

    private void go(ActivityScenario<MainActivity> scenario, String name, int destination,
                    Bundle args, boolean scrolled) {
        try {
            scenario.onActivity(a -> Navigation.findNavController(a, R.id.nav_host)
                    .navigate(destination, args));
            shoot(name);
            if (scrolled) {
                shootScrolled(name + "-scrolled");
            }
        } catch (Throwable failed) {
            failures.append(name).append(": ").append(failed).append('\n');
        }
    }

    private static Bundle order(long id) {
        Bundle args = new Bundle();
        args.putLong(ParsingArgs.ARG_ORDER_ID, id);
        return args;
    }

    /**
     * Three earlier shops at other stores, buying the same milk and eggs as the Walmart
     * order, so Explore's patterns have a price to track, pairs to connect and days to
     * shade. Each order's stated total is the sum of its rows, so none shows a warning.
     */
    private static void earlierShops(long household,
                                     List<com.householdsplitter.data.entity.Member> members) {
        if (members.size() < 3) {
            return;
        }
        long a = members.get(0).id;
        long b = members.get(1).id;
        long c = members.get(2).id;
        Object[][] shops = {
            {"Aug 13 Costco", com.householdsplitter.core.parse.StoreKind.COSTCO, 1786622400000L,
                new Object[][]{{"Great Value Whole Milk, 1 Gallon", 299L, new long[]{a, b, c}},
                    {"Great Value Large White Eggs, 18 Count", 389L, new long[]{a, c}},
                    {"Kirkland Coffee Beans, 2.5 lb", 1899L, new long[]{a, b}}}},
            {"Aug 20 Target", com.householdsplitter.core.parse.StoreKind.TARGET, 1787227200000L,
                new Object[][]{{"Great Value Whole Milk, 1 Gallon", 349L, new long[]{a, b, c}},
                    {"Great Value Large White Eggs, 18 Count", 429L, new long[]{a, c}},
                    {"Tortilla Chips", 499L, new long[]{b, c}}}},
            {"Aug 27 Aldi", com.householdsplitter.core.parse.StoreKind.ALDI, 1787832000000L,
                new Object[][]{{"Great Value Whole Milk, 1 Gallon", 279L, new long[]{a, b, c}},
                    {"Great Value Large White Eggs, 18 Count", 359L, new long[]{a, c}},
                    {"Bananas", 129L, new long[]{b}}}},
        };
        com.householdsplitter.data.db.AppDatabase db = TestData.database();
        for (Object[] shop : shops) {
            long total = 0L;
            for (Object[] row : (Object[][]) shop[3]) {
                total += (Long) row[1];
            }
            Order order = new Order();
            order.householdId = household;
            order.label = (String) shop[0];
            order.store = (com.householdsplitter.core.parse.StoreKind) shop[1];
            order.orderDate = (Long) shop[2];
            order.createdAt = (Long) shop[2];
            order.status = com.householdsplitter.data.entity.OrderStatus.SETTLED;
            order.statedSubtotalCents = total;
            order.statedTotalCents = total;
            long orderId = db.orderDao().insert(order);
            List<com.householdsplitter.data.entity.OrderParticipant> people =
                    new java.util.ArrayList<>();
            for (long m : new long[]{a, b, c}) {
                people.add(new com.householdsplitter.data.entity.OrderParticipant(orderId, m));
            }
            db.participantDao().insertAll(people);
            int position = 0;
            for (Object[] row : (Object[][]) shop[3]) {
                long[] who = (long[]) row[2];
                com.householdsplitter.data.entity.LineItem item =
                        new com.householdsplitter.data.entity.LineItem();
                item.orderId = orderId;
                item.name = (String) row[0];
                item.rawOcrText = item.name;
                item.lineTotalCents = (Long) row[1];
                item.position = position++;
                item.origin = com.householdsplitter.core.quality.ItemOrigin.PARSED;
                item.parsedName = item.name;
                item.parsedCents = item.lineTotalCents;
                item.scope = who.length == 1 ? com.householdsplitter.core.calc.Scope.PERSONAL
                        : who.length >= 3 ? com.householdsplitter.core.calc.Scope.COMMON
                        : com.householdsplitter.core.calc.Scope.SUBSET;
                long itemId = db.lineItemDao().insert(item);
                List<com.householdsplitter.data.entity.ItemAssignment> assignments =
                        new java.util.ArrayList<>();
                for (long m : who) {
                    assignments.add(new com.householdsplitter.data.entity.ItemAssignment(
                            itemId, m, 1));
                }
                db.assignmentDao().insertAll(assignments);
            }
        }
    }

    private void scrollShoot(String name, int viewId) {
        try {
            androidx.test.espresso.Espresso.onView(
                    androidx.test.espresso.matcher.ViewMatchers.withId(viewId))
                    .perform(androidx.test.espresso.action.ViewActions.scrollTo());
            shoot(name);
        } catch (Throwable failed) {
            failures.append(name).append(": ").append(failed).append('\n');
        }
    }

    /** Reuses the store-listing household so both sets of images show the same orders. */
    private static long seed() throws Exception {
        Method seed = ScreenshotCaptureTest.class.getDeclaredMethod("seed");
        seed.setAccessible(true);
        return (long) seed.invoke(new ScreenshotCaptureTest());
    }

    @Test
    public void tourEveryScreen() throws Exception {
        // First launch: the introduction, then the two setup screens.
        TestData.wipe();
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            shoot("01-welcome-1");
            try {
                androidx.test.espresso.Espresso.onView(
                        androidx.test.espresso.matcher.ViewMatchers.withId(R.id.nextButton))
                        .perform(androidx.test.espresso.action.ViewActions.click());
                shoot("02-welcome-2");
                androidx.test.espresso.Espresso.onView(
                        androidx.test.espresso.matcher.ViewMatchers.withId(R.id.nextButton))
                        .perform(androidx.test.espresso.action.ViewActions.click());
                shoot("03-welcome-3");
            } catch (Throwable failed) {
                failures.append("welcome pages: ").append(failed).append('\n');
            }
            // The setup screens from the same launch. A third launch in a row never reports
            // its activity on an Android 10 emulator, and the harness waits on it forever.
            go(scenario, "04-setup-group", R.id.setupGroupFragment, null, false);
            go(scenario, "05-setup-members", R.id.setupMembersFragment, null, false);

        // Everything else from this same launch too. The sample household goes in behind
        // the running app, and the app is moved to Orders by navigation, not a relaunch:
        // an Android 10 emulator never reports a second launch's activity, so a relaunch
        // leaves the harness waiting forever.
        long household = seed();
        List<Order> orders = TestData.database().orderDao().getAllSync(household);
        long first = orders.isEmpty() ? 0L : orders.get(0).id;
        long second = orders.size() < 2 ? first : orders.get(1).id;
        // Notes on the first order, so the summary shows both kinds.
        TestData.database().orderDao().updateNote(first,
                "Ben covered the bag fee. Settle up by Friday.");
        List<com.householdsplitter.data.entity.Member> members =
                TestData.database().memberDao().getAllSync(household);
        if (members.size() > 1) {
            TestData.database().orderDao().upsertMemberNote(
                    new com.householdsplitter.data.entity.MemberNote(first, members.get(1).id,
                            "Paying in cash"));
        }
        earlierShops(household, members);
        TestData.locator().currentHouseholdId(household);

        {
            scenario.onActivity(a -> {
                androidx.navigation.NavController nav = Navigation.findNavController(a, R.id.nav_host);
                nav.navigate(R.id.homeFragment, null, new androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(nav.getGraph().getId(), true).build());
            });
            shoot("10-home");
            try {
                androidx.test.espresso.Espresso.onView(
                        androidx.test.espresso.matcher.ViewMatchers.withId(R.id.newOrderFab))
                        .perform(androidx.test.espresso.action.ViewActions.click());
                shoot("11-store-picker");
                androidx.test.espresso.Espresso.pressBack();
            } catch (Throwable failed) {
                failures.append("11-store-picker: ").append(failed).append('\n');
            }
            go(scenario, "12-explore", R.id.insightsFragment, null, true);
            scrollShoot("12b-patterns-web", R.id.webTile);
            scrollShoot("12c-patterns-calendar", R.id.calendarTile);
            scrollShoot("12d-patterns-price", R.id.priceTile);
            go(scenario, "13-spending", R.id.analyticsFragment, null, true);
            go(scenario, "14-import", R.id.importFragment, null, false);
            Bundle costco = new Bundle();
            costco.putString(ParsingArgs.ARG_STORE, "COSTCO");
            go(scenario, "14b-import-costco", R.id.importFragment, costco, false);
            go(scenario, "15-review", R.id.reviewItemsFragment, order(second), true);
            go(scenario, "16-participants", R.id.participantsFragment, order(second), false);
            go(scenario, "17-assign", R.id.assignFragment, order(second), false);
            go(scenario, "18-bulk-assign", R.id.bulkAssignFragment, order(second), false);
            go(scenario, "19-summary", R.id.summaryFragment, order(first), true);
            try {
                androidx.test.espresso.Espresso.onView(
                        androidx.test.espresso.matcher.ViewMatchers.withId(R.id.orderNoteButton))
                        .perform(androidx.test.espresso.action.ViewActions.scrollTo(),
                                androidx.test.espresso.action.ViewActions.click());
                shoot("19b-note-dialog");
                androidx.test.espresso.Espresso.pressBack();
                androidx.test.espresso.Espresso.pressBack();
            } catch (Throwable failed) {
                failures.append("19b-note-dialog: ").append(failed).append('\n');
            }
            Bundle readOnly = order(second);
            readOnly.putBoolean(SummaryFragment.ARG_READ_ONLY, true);
            go(scenario, "20-summary-settled", R.id.summaryFragment, readOnly, false);
            go(scenario, "21-assignments", R.id.assignmentsFragment, order(first), false);
            go(scenario, "22-groups", R.id.groupsFragment, null, false);
            go(scenario, "23-edit-group", R.id.editGroupFragment, null, false);
            go(scenario, "24-profile", R.id.profileFragment, null, false);
            go(scenario, "25-rules", R.id.rulesFragment, null, false);
            go(scenario, "26-settings", R.id.settingsFragment, null, true);
        }
        }

        try (FileWriter out = new FileWriter(new File(dir(), "failures.txt"))) {
            out.write(failures.toString());
        }
    }
}
