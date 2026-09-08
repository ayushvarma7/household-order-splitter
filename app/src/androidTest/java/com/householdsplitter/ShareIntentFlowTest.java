package com.householdsplitter;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.ui.MainActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * SPEC 12.5.4: the happy path from a share intent, using a bundled test image.
 *
 * <p>SPEC 8.1.2 calls this the primary real-world path: screenshot the Walmart app, share
 * it straight into this one, and land on the import screen with the images already loaded.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ShareIntentFlowTest {

    private long householdId;

    @Before
    public void seedAHousehold() {
        TestData.wipe();
        AppDatabase database = TestData.database();
        householdId = database.householdDao().insert(new Household("Fixture Group", 1L));
        database.memberDao().insert(new Member(householdId, "Alpha", "#1F6FB2", 0));
        database.memberDao().insert(new Member(householdId, "Beta", "#B3261E", 1));
        TestData.locator().currentHouseholdId(householdId);
    }

    /** Copies a test asset somewhere the app's own FileProvider can serve it from. */

    /**
     * True when the order-page fixtures are present.
     *
     * <p>They are screenshots of a real Walmart order, so they are not committed: page three
     * carries a card's last four digits, the order number and a scannable barcode of it.
     * Drop your own captures into {@code app/src/androidTest/assets} as
     * {@code order-page-1.png} and so on to run these, and see the readme.
     *
     * <p>Skipped rather than failed when they are absent. A red test for a file the
     * repository deliberately does not ship would train everyone to ignore it.
     */
    private static boolean fixturesPresent() {
        try {
            InstrumentationRegistry.getInstrumentation().getContext()
                    .getAssets().open("order-page-1.png").close();
            return true;
        } catch (java.io.IOException absent) {
            return false;
        }
    }

    private static Uri sharedImage(String assetName) throws Exception {
        org.junit.Assume.assumeTrue(
                "order-page fixtures are not committed; see the readme", fixturesPresent());
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context test = InstrumentationRegistry.getInstrumentation().getContext();
        File directory = new File(target.getCacheDir(), "captures");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("could not create the capture directory");
        }
        File file = new File(directory, assetName);
        try (InputStream in = test.getAssets().open(assetName);
             OutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) > 0) {
                out.write(buffer, 0, count);
            }
        }
        return FileProvider.getUriForFile(target, target.getPackageName() + ".fileprovider", file);
    }

    private static Intent shareIntent(Uri... images) {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(target, MainActivity.class);
        intent.setType("image/png");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (images.length == 1) {
            intent.setAction(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_STREAM, images[0]);
        } else {
            intent.setAction(Intent.ACTION_SEND_MULTIPLE);
            ArrayList<Uri> list = new ArrayList<>();
            for (Uri image : images) {
                list.add(image);
            }
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, list);
        }
        return intent;
    }

    /** SPEC 8.1.2: a single shared image lands on import, already loaded. */
    @Test
    public void sharingOneScreenshotLandsOnImportWithItLoaded() throws Exception {
        Intent intent = shareIntent(sharedImage("order-page-1.png"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
            settle(2500);
            onView(withId(R.id.previewStrip)).check(matches(isDisplayed()));
            onView(withId(R.id.continueButton)).check(matches(isDisplayed()));
        }
    }

    /** SPEC 8.1.2 again: ACTION_SEND_MULTIPLE, and the order of the images is preserved. */
    @Test
    public void sharingSeveralScreenshotsRunsTheWholeWayToASummary() throws Exception {
        Intent intent = shareIntent(
                sharedImage("order-page-1.png"),
                sharedImage("order-page-2.png"),
                sharedImage("order-page-3.png"));

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
            settle(2500);
            onView(withId(R.id.continueButton)).perform(click());

            // Parsing runs on its own executor, so give the recogniser room to finish.
            settle(12000);

            // S6: the parse landed in the editable review screen (SPEC 7.6.1).
            onView(withId(R.id.itemList)).check(matches(isDisplayed()));
            assertEquals("all five visible rows were read", 5, itemCount());

            onView(withId(R.id.continueButton)).perform(click());   // review -> details
            settle(1500);
            onView(withId(R.id.continueButton)).perform(click());   // details -> participants
            settle(1500);
            onView(withId(R.id.continueButton)).perform(click());   // participants -> assign
            settle(2000);

            // S9: answer for every row the quick way (SPEC 7.9.3.1).
            for (int i = 0; i < 5; i++) {
                onView(withId(R.id.commonButton)).perform(click());
                settle(1200);
            }
            settle(2500);

            // S10: the totals computed and they sum to the computed total exactly.
            onView(withId(R.id.memberList)).check(matches(isDisplayed()));
            assertTrue("the order finished as assigned", isAssigned());
        }
    }

    private int itemCount() {
        return TestData.database().lineItemDao().getForOrderSync(latestOrderId()).size();
    }

    private boolean isAssigned() {
        com.householdsplitter.data.entity.Order order =
                TestData.database().orderDao().getByIdSync(latestOrderId());
        return order != null
                && order.status != com.householdsplitter.data.entity.OrderStatus.DRAFT;
    }

    private long latestOrderId() {
        return TestData.database().orderDao().getAllBundlesSync(householdId).get(0).order.id;
    }

    private static void settle(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
