package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderParticipant;
import com.householdsplitter.data.entity.OrderStatus;
import com.householdsplitter.widget.BalancesWidgetProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The widget's real update path, end to end.
 *
 * <p>{@link BalancesWidgetTest} checks that the views render correctly once built. This
 * checks the part that cannot be checked any other way: that they get built and delivered
 * at all.
 *
 * <p>Reading the balances means reading every settled order off the disk, so
 * {@code onUpdate} hands the work to an executor and holds the broadcast open with
 * {@code goAsync}. Get that wrong and the receiver returns before there is anything to draw:
 * no crash, no log, just a widget that sits blank on somebody's home screen forever. So the
 * test binds a real widget id, sends the real broadcast, and waits for what the launcher
 * would have been given.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class BalancesWidgetUpdateTest {

    /** Any id the platform is not already using as a host. */
    private static final int TEST_HOST_ID = 0x5150;

    private Context context;
    private AppDatabase database;
    private RecordingHost host;
    private int appWidgetId = -1;

    /** Captures what the launcher would have been handed. */
    private static final class RecordingHost extends AppWidgetHost {

        final AtomicReference<RemoteViews> latest = new AtomicReference<>();
        final CountDownLatch received = new CountDownLatch(1);

        RecordingHost(Context context) {
            super(context, TEST_HOST_ID);
        }

        @Override
        protected AppWidgetHostView onCreateView(Context context, int appWidgetId,
                                                 AppWidgetProviderInfo appWidget) {
            return new AppWidgetHostView(context) {
                @Override
                public void updateAppWidget(RemoteViews remoteViews) {
                    super.updateAppWidget(remoteViews);
                    if (remoteViews != null) {
                        latest.set(remoteViews);
                        received.countDown();
                    }
                }
            };
        }
    }

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        TestData.wipe();
        database = TestData.database();
        seed();

        // Binding a widget id is normally the launcher's privilege.
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity();

        host = new RecordingHost(context);
        appWidgetId = host.allocateAppWidgetId();
        ComponentName provider = new ComponentName(context, BalancesWidgetProvider.class);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        assertTrue("could not bind a widget id",
                manager.bindAppWidgetIdIfAllowed(appWidgetId, provider));

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            host.startListening();
            host.createView(context, appWidgetId, manager.getAppWidgetInfo(appWidgetId));
        });
    }

    @After
    public void tearDown() {
        if (host != null) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(host::stopListening);
            if (appWidgetId >= 0) {
                host.deleteAppWidgetId(appWidgetId);
            }
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Ana paid $90 and Ben paid $30 for two orders shared three ways, so each owes $40:
     * Ana is $50 up, Ben $10 down, Chen $40 down. The widget should say so.
     */
    private void seed() {
        long householdId = database.householdDao().insert(new Household("Flat 12", 1L));
        TestData.locator().currentHouseholdId(householdId);
        long ana = database.memberDao().insert(new Member(householdId, "Ana", "#1F6FB2", 0));
        long ben = database.memberDao().insert(new Member(householdId, "Ben", "#B3261E", 1));
        long chen = database.memberDao().insert(new Member(householdId, "Chen", "#1B5E20", 2));

        addSettledOrder(householdId, "Weekly shop", ana, 9000L, ana, ben, chen);
        addSettledOrder(householdId, "Costco run", ben, 3000L, ana, ben, chen);
    }

    private void addSettledOrder(long householdId, String label, long payerId, long totalCents,
                                 long... memberIds) {
        Order order = new Order();
        order.householdId = householdId;
        order.label = label;
        order.orderDate = 1_700_000_000_000L;
        order.createdAt = 1_700_000_000_000L;
        order.statedTotalCents = totalCents;
        order.statedSubtotalCents = totalCents;
        order.status = OrderStatus.SETTLED;
        order.payerMemberId = payerId;
        long orderId = database.orderDao().insert(order);

        OrderParticipant[] participants = new OrderParticipant[memberIds.length];
        for (int i = 0; i < memberIds.length; i++) {
            participants[i] = new OrderParticipant(orderId, memberIds[i]);
        }
        database.participantDao().insertAll(Arrays.asList(participants));

        LineItem item = new LineItem();
        item.orderId = orderId;
        item.name = "Groceries";
        item.rawOcrText = "Groceries";
        item.quantity = 1;
        item.lineTotalCents = totalCents;
        item.scope = Scope.COMMON;
        item.position = 0;
        database.lineItemDao().insert(item);
    }

    @Test
    public void theWidgetIsPopulatedFromTheRealBalances() throws Exception {
        BalancesWidgetProvider.refresh(context);

        assertTrue("the widget was never updated: goAsync is not holding the broadcast open",
                host.received.await(30, TimeUnit.SECONDS));

        View applied = applyOnMain(host.latest.get());
        assertEquals("Flat 12", text(applied, R.id.widgetTitle));

        String first = text(applied, R.id.row1);
        String second = text(applied, R.id.row2);
        assertEquals(View.VISIBLE, visibility(applied, R.id.row1));
        assertEquals(View.VISIBLE, visibility(applied, R.id.row2));
        assertEquals("only two transfers are needed",
                View.GONE, visibility(applied, R.id.row3));
        assertEquals(View.GONE, visibility(applied, R.id.widgetMessage));

        // Chen owes $40 and Ben $10, both to Ana, who is $50 up.
        String both = first + "\n" + second;
        assertTrue(both, both.contains("Chen pays Ana $40.00"));
        assertTrue(both, both.contains("Ben pays Ana $10.00"));
    }

    private View applyOnMain(RemoteViews views) throws Exception {
        assertNotNull(views);
        AtomicReference<View> holder = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                holder.set(views.apply(context, new android.widget.FrameLayout(context))));
        return holder.get();
    }

    private String text(View root, int id) {
        TextView view = root.findViewById(id);
        assertNotNull("no view for id " + id, view);
        return view.getText().toString();
    }

    private int visibility(View root, int id) {
        View view = root.findViewById(id);
        assertNotNull("no view for id " + id, view);
        return view.getVisibility();
    }
}
