package com.householdsplitter;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.graphics.Bitmap;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.parse.StoreKind;
import com.householdsplitter.core.quality.ItemOrigin;
import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.ItemAssignment;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderParticipant;
import com.householdsplitter.data.entity.OrderStatus;
import com.householdsplitter.ui.MainActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.io.FileOutputStream;

/**
 * Seeds a believable household and captures the store listing screenshots.
 *
 * <p>Not a test of anything, which is why it asserts nothing. It exists because store
 * screenshots taken by hand drift: somebody re-shoots one screen, the totals no longer
 * agree with the screen beside it, and the listing quietly shows an app whose arithmetic
 * does not add up. Generating them from one seeded database means every figure on every
 * shot is the same order.
 *
 * <p>Run it deliberately, never as part of the suite:
 * {@code ./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.householdsplitter.ScreenshotCaptureTest}
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ScreenshotCaptureTest {

    private static void shoot(String name) {
        try {
            Thread.sleep(700L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().takeScreenshot();
        // The app's own files dir, which always exists and is readable with run-as.
        // getExternalFilesDir returned null here and the write went nowhere silently.
        File dir = new File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getFilesDir(), "screenshots");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("could not create " + dir);
        }
        File out_file = new File(dir, name + ".png");
        try (FileOutputStream out = new FileOutputStream(out_file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception failed) {
            throw new RuntimeException("could not write " + name, failed);
        }
        if (out_file.length() == 0L) {
            throw new IllegalStateException("wrote an empty file for " + name);
        }
    }

    /** Ayush, Ben and Chen, and two orders whose numbers agree with each other. */
    private long seed() {
        TestData.wipePastIntro();
        AppDatabase db = TestData.database();
        long household = db.householdDao().insert(new Household("Flat 12", 1L));
        long ayush = db.memberDao().insert(new Member(household, "Ayush", "#2E6FD6", 0));
        long ben = db.memberDao().insert(new Member(household, "Ben", "#B5503F", 1));
        long chen = db.memberDao().insert(new Member(household, "Chen", "#1F7A5A", 2));

        long amazon = order(db, household, "Sep 06 Amazon Fresh", StoreKind.AMAZON_FRESH,
                1788696000000L, 3893L, 4005L, 112L);
        participants(db, amazon, ayush, ben, chen);
        item(db, amazon, "Amazon Grocery, Chicken Breast Fillets, Boneless Skinless", 726L, 0,
                ItemOrigin.PARSED, ayush, ben);
        item(db, amazon, "Amazon Grocery, Broccoli Florets, 12 Oz, Frozen", 116L, 1,
                ItemOrigin.PARSED, ayush, ben, chen);
        item(db, amazon, "Amazon Grocery, Sliced Strawberries, 16 Oz, Frozen", 590L, 2,
                ItemOrigin.PARSED, chen);
        item(db, amazon, "Plum Roma Tomato", 174L, 3, ItemOrigin.PARSED, ayush, ben, chen);
        item(db, amazon, "Banana Bunch (4-5 Count)", 99L, 4, ItemOrigin.PARSED, ben);
        item(db, amazon, "Amazon Grocery, Old Fashioned Oats, 18 Oz", 278L, 5,
                ItemOrigin.PARSED, ayush);
        item(db, amazon, "Amazon Grocery, Whole Milk Plain Yogurt, 32 Oz", 598L, 6,
                ItemOrigin.PARSED, chen);
        item(db, amazon, "Amazon Grocery, Multigrain Bread, 24 Oz", 462L, 7,
                ItemOrigin.PARSED, ayush, ben, chen);
        item(db, amazon, "Tide Liquid Laundry Detergent, Original Scent", 1794L, 8,
                ItemOrigin.PARSED, ayush, ben, chen);

        long walmart = order(db, household, "Sep 03 Walmart", StoreKind.WALMART,
                1788436800000L, 4412L, 4907L, 295L);
        participants(db, walmart, ayush, ben, chen);
        item(db, walmart, "Great Value Whole Milk, 1 Gallon", 348L, 0,
                ItemOrigin.PARSED, ayush, ben, chen);
        item(db, walmart, "Freshness Guaranteed Rotisserie Chicken", 697L, 1,
                ItemOrigin.PARSED, ben);
        item(db, walmart, "Great Value Large White Eggs, 18 Count", 428L, 2,
                ItemOrigin.PARSED, ayush, chen);
        item(db, walmart, "Marketside Organic Baby Spinach, 5 oz", 298L, 3,
                ItemOrigin.PARSED, chen);
        item(db, walmart, "Oatly Original Oat Milk, 64 fl oz", 549L, 4,
                ItemOrigin.PARSED, chen);
        item(db, walmart, "Barilla Penne Pasta, 16 oz", 178L, 5,
                ItemOrigin.PARSED, ayush, ben, chen);
        item(db, walmart, "Colgate Total Toothpaste", 414L, 6, ItemOrigin.MANUAL, ayush);
        return household;
    }

    private long order(AppDatabase db, long household, String label, StoreKind store,
                       long date, long subtotal, long total, long tax) {
        Order order = new Order();
        order.householdId = household;
        order.label = label;
        order.store = store;
        order.orderDate = date;
        order.createdAt = date;
        order.status = OrderStatus.ASSIGNED;
        order.statedSubtotalCents = subtotal;
        order.statedTotalCents = total;
        order.taxCents = tax;
        return db.orderDao().insert(order);
    }

    private void participants(AppDatabase db, long orderId, long... members) {
        List<OrderParticipant> rows = new ArrayList<>();
        for (long m : members) {
            rows.add(new OrderParticipant(orderId, m));
        }
        db.participantDao().insertAll(rows);
    }

    private void item(AppDatabase db, long orderId, String name, long cents, int position,
                      ItemOrigin origin, long... members) {
        LineItem row = new LineItem();
        row.orderId = orderId;
        row.name = name;
        row.rawOcrText = name;
        row.lineTotalCents = cents;
        row.position = position;
        row.origin = origin;
        if (origin == ItemOrigin.PARSED) {
            row.parsedName = name;
            row.parsedCents = cents;
        }
        // COMMON when everyone is on it, SUBSET for a few, PERSONAL for one. The scope
        // has to agree with the assignments or the summary screen shows a total the rows
        // do not explain, which is exactly the thing a store screenshot must not do.
        row.scope = members.length == 0 ? Scope.UNASSIGNED
                : members.length == 1 ? Scope.PERSONAL
                : members.length >= 3 ? Scope.COMMON : Scope.SUBSET;
        long id = db.lineItemDao().insert(row);
        List<ItemAssignment> assignments = new ArrayList<>();
        for (long m : members) {
            assignments.add(new ItemAssignment(id, m, 1));
        }
        db.assignmentDao().insertAll(assignments);
    }

    @Test
    public void captureTheListingScreenshots() {
        seed();
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            shoot("20-home-orders");

            // The store picker, which is the whole reason a second shop could be added
            // without the reader having to guess which one it is looking at.
            onView(withId(R.id.newOrderFab)).perform(click());
            shoot("16-store-picker");
            androidx.test.espresso.Espresso.pressBack();

            scenario.onActivity(a -> androidx.navigation.Navigation
                    .findNavController(a, R.id.nav_host).navigate(R.id.analyticsFragment));
            shoot("21-spending");

            scenario.onActivity(a -> androidx.navigation.Navigation
                    .findNavController(a, R.id.nav_host).navigate(R.id.settingsFragment));
            shoot("22-settings");

            // The colour schemes, which live on the same card as light and dark.
            onView(withId(R.id.paletteList)).perform(scrollTo());
            shoot("24-themes");

            onView(withId(R.id.parserReportButton)).perform(scrollTo(), click());
            shoot("23-parser-report");
        }
    }
}
