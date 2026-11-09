package com.householdsplitter;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.ParcelFileDescriptor;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.DraftStep;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderStatus;
import com.householdsplitter.ui.MainActivity;
import com.householdsplitter.ui.home.HomeFragment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Back from a screen leaves you on a working screen.
 *
 * <p>On Android 16 with predictive back enabled, Fragment 1.8.5 and Transition 1.5.0 ran
 * the screen change as a seekable transition, and a Back key event, which carries no
 * gesture progress, left it waiting for an end that never came. The screen you went back
 * to was stuck in AWAITING_ENTER_EFFECTS and the one you left stayed painted over it, so
 * the app looked frozen until it was restarted. The Back button in three-button
 * navigation sends exactly that key event. Fixed by Fragment 1.8.9 and Transition 1.5.1.
 *
 * <p>Checked by lifecycle rather than by finding a label on screen: in the frozen state the
 * screen underneath could still count as displayed while sitting invisible. Verified to
 * fail on the old library versions and pass on the new ones.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class BackNavigationTest {

    @Before
    public void setUp() {
        TestData.wipePastIntro();
        AppDatabase database = TestData.database();
        long householdId = database.householdDao().insert(new Household("Fixture Group", 1L));
        database.memberDao().insert(new Member(householdId, "Alpha", "#1F6FB2", 0));
        database.memberDao().insert(new Member(householdId, "Beta", "#B3261E", 1));

        Order order = new Order();
        order.householdId = householdId;
        order.label = "Fixture draft";
        order.orderDate = 1L;
        order.createdAt = 1L;
        order.status = OrderStatus.DRAFT;
        order.draftStep = DraftStep.REVIEW;
        long orderId = database.orderDao().insert(order);

        LineItem item = new LineItem();
        item.orderId = orderId;
        item.name = "Whole milk";
        item.rawOcrText = item.name;
        item.lineTotalCents = 342L;
        item.scope = Scope.UNASSIGNED;
        database.lineItemDao().insert(item);
    }

    @Test
    public void backFromTheItemListLeavesHomeWorking() {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            settle();
            // Opened the way a person opens it, by tapping the draft on Home. Navigating to
            // the item list programmatically instead did not reproduce the freeze.
            onView(withText("Fixture draft")).perform(click());
            settle();

            systemBack();
            settle();

            AtomicReference<Fragment> shown = new AtomicReference<>();
            AtomicReference<Integer> containerChildren = new AtomicReference<>();
            scenario.onActivity(activity -> {
                NavHostFragment host = (NavHostFragment) activity.getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host);
                assertNotNull(host);
                shown.set(host.getChildFragmentManager().getPrimaryNavigationFragment());
                View container = host.getView();
                containerChildren.set(container instanceof ViewGroup
                        ? ((ViewGroup) container).getChildCount() : -1);
            });

            Fragment home = shown.get();
            assertTrue("Back should land on Home, not " + home, home instanceof HomeFragment);
            assertTrue("Home never finished arriving: its transition did not end",
                    home.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED));
            assertNotNull(home.getView());
            assertEquals("Home is drawn at full opacity", 1f, home.getView().getAlpha(), 0.001f);
            assertEquals("the screen that was left is no longer in the container",
                    Integer.valueOf(1), containerChildren.get());
        }
    }

    /**
     * Back as the system delivers it, through the input stack, which is what a Back button
     * or {@code input keyevent} sends. Espresso's {@code pressBack()} reaches the activity
     * by another route and did not reproduce the freeze, so a test using it passed on the
     * broken libraries too.
     */
    private static void systemBack() {
        ParcelFileDescriptor output = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand("input keyevent KEYCODE_BACK");
        try (InputStream stream =
                     new ParcelFileDescriptor.AutoCloseInputStream(output)) {
            byte[] buffer = new byte[256];
            while (stream.read(buffer) != -1) {
                // drain, so the command has finished before the test looks
            }
        } catch (IOException ignored) {
            // The key was sent or it was not; the assertions say which.
        }
    }

    private static void settle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        try {
            Thread.sleep(800L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
