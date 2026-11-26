package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.householdsplitter.core.analytics.SpendingAnalytics;
import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.insight.InsightTree;
import com.householdsplitter.core.insight.ItemCategorizer;
import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.ItemAssignment;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderParticipant;
import com.householdsplitter.data.entity.OrderStatus;
import com.householdsplitter.di.ServiceLocator;
import com.householdsplitter.export.WorkbookService;
import com.householdsplitter.insight.InsightService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The look-inside screen and the Spending screen read the orders in the database the same
 * way: money a bill charged that no row accounts for has to show up in both, and an order
 * that is not finished has to be left out of both.
 *
 * <p>The grouping and the facts are covered by JVM tests in :core. This covers the part that
 * needs a database: that real stored rows, assignments and printed subtotals come out as the
 * right bubbles.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class InsightsTest {

    private AppDatabase database;
    private ServiceLocator locator;
    private long householdId;
    private long anaId;
    private long benId;

    @Before
    public void setUp() {
        TestData.wipe();
        database = TestData.database();
        locator = TestData.locator();
        householdId = database.householdDao().insert(new Household("Fixture Group", 1L));
        locator.currentHouseholdId(householdId);
        anaId = database.memberDao().insert(new Member(householdId, "Ana", "#1F6FB2", 0));
        benId = database.memberDao().insert(new Member(householdId, "Ben", "#B3261E", 1));
    }

    private static long thisMonth(int dayOfMonth) {
        ZoneId zone = ZoneId.systemDefault();
        return LocalDate.now(zone).withDayOfMonth(1).withDayOfMonth(dayOfMonth)
                .atTime(12, 0).atZone(zone).toInstant().toEpochMilli();
    }

    /** One finished order: a shared row for 10.00 and a row for Ana for 4.00. */
    private long finishedOrder(String label, long statedSubtotalCents) {
        Order order = new Order();
        order.householdId = householdId;
        order.label = label;
        order.orderDate = thisMonth(1);
        order.createdAt = order.orderDate;
        order.statedSubtotalCents = statedSubtotalCents;
        order.statedTotalCents = statedSubtotalCents;
        order.status = OrderStatus.ASSIGNED;
        long orderId = database.orderDao().insert(order);
        database.participantDao().insertAll(Arrays.asList(
                new OrderParticipant(orderId, anaId), new OrderParticipant(orderId, benId)));
        row(orderId, "Tortilla chips", 1000L, Scope.COMMON, 0);
        long personal = row(orderId, "Coffee beans", 400L, Scope.PERSONAL, 1);
        database.assignmentDao().insertAll(Arrays.asList(
                new ItemAssignment(personal, anaId, 1)));
        return orderId;
    }

    private long row(long orderId, String name, long cents, Scope scope, int position) {
        LineItem item = new LineItem();
        item.orderId = orderId;
        item.name = name;
        item.rawOcrText = name;
        item.lineTotalCents = cents;
        item.quantity = 1;
        item.scope = scope;
        item.position = position;
        return database.lineItemDao().insert(item);
    }

    private InsightService.Loaded load() throws Exception {
        AtomicReference<InsightService.Loaded> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        new InsightService(locator.orderRepository(), locator.executors())
                .load(householdId, loaded -> {
                    result.set(loaded);
                    done.countDown();
                });
        assertTrue("loading finished", done.await(10, TimeUnit.SECONDS));
        return result.get();
    }

    @Test
    public void everyRowBecomesABubbleSplitTheWayItWasAssigned() throws Exception {
        finishedOrder("Weekly shop", 1400L);

        InsightService.Loaded loaded = load();
        InsightTree tree = InsightTree.build(loaded.purchases, ZoneId.systemDefault(),
                InsightTree.latestMonth(loaded.purchases, ZoneId.systemDefault()));

        assertEquals(1400L, tree.root().thisCents());
        assertNull("rows add up to the bill, so nothing is missing", tree.missing());
        InsightTree.Node coffee = tree.find("Drinks/Coffee and tea/Coffee beans");
        assertEquals(400L, coffee.thisCents());
        assertEquals("a personal row is all Ana's", Long.valueOf(400L),
                coffee.thisByMember().get(anaId));
        assertNull(coffee.thisByMember().get(benId));
        InsightTree.Node chips = tree.find("Snacks/Salty/Tortilla chips");
        assertEquals("a shared row is split between both", 500L,
                (long) chips.thisByMember().get(anaId));
        assertEquals(500L, (long) chips.thisByMember().get(benId));
    }

    /** The bill printed 20.00 for rows that add up to 14.00, so 6.00 was never read. */
    @Test
    public void moneyTheReaderMissedShowsAsABubbleAndNamesTheOrder() throws Exception {
        long orderId = finishedOrder("Short order", 2000L);

        InsightService.Loaded loaded = load();
        InsightTree tree = InsightTree.build(loaded.purchases, ZoneId.systemDefault(),
                InsightTree.latestMonth(loaded.purchases, ZoneId.systemDefault()));

        InsightTree.Node missing = tree.missing();
        assertNotNull(missing);
        assertEquals(ItemCategorizer.MISSING, missing.name());
        assertEquals(600L, missing.thisCents());
        assertEquals("the picture adds up to what the bill charged", 2000L,
                tree.root().thisCents());
        assertEquals(1, loaded.missingOrders.size());
        assertEquals(orderId, loaded.missingOrders.get(0).orderId);
        assertEquals(600L, loaded.missingOrders.get(0).cents);
    }

    @Test
    public void anOrderStillWaitingForAnswersIsLeftOut() throws Exception {
        long orderId = finishedOrder("Unfinished", 2000L);
        row(orderId, "Mystery item", 500L, Scope.UNASSIGNED, 2);

        InsightService.Loaded loaded = load();

        assertTrue(loaded.purchases.isEmpty());
        assertTrue(loaded.missingOrders.isEmpty());
    }

    @Test
    public void theSpendingScreenReportsTheSameMissingMoney() throws Exception {
        finishedOrder("Short order", 2000L);

        AtomicReference<WorkbookService.Insight> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        locator.workbookService().analyse(householdId, insight -> {
            result.set(insight);
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));

        SpendingAnalytics.Report report = result.get().spending;
        assertEquals(600L, report.missingCents());
        assertEquals(1, report.missingOrders().size());
        assertEquals("Short order", report.missingOrders().get(0).label());
    }

    @Test
    public void anOrderWithNoRowsYetIsNotCalledShort() throws Exception {
        Order order = new Order();
        order.householdId = householdId;
        order.label = "Imported, not read";
        order.orderDate = thisMonth(1);
        order.createdAt = order.orderDate;
        order.statedSubtotalCents = 5000L;
        order.statedTotalCents = 5000L;
        order.status = OrderStatus.ASSIGNED;
        long orderId = database.orderDao().insert(order);
        database.participantDao().insertAll(Arrays.asList(
                new OrderParticipant(orderId, anaId)));

        assertTrue(load().missingOrders.isEmpty());
    }
}
