package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.Observer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderParticipant;
import com.householdsplitter.data.entity.OrderStatus;
import com.householdsplitter.di.ServiceLocator;
import com.householdsplitter.ui.home.HomeRow;
import com.householdsplitter.ui.home.HomeViewModel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Searching, filtering, and month grouping on the home list. SPEC 7.3.2.
 *
 * <p>The matching and the month arithmetic are covered by JVM tests in :core. This covers
 * the part that needs a database: that searching reaches inside orders to their item names,
 * which is the whole reason the feature is worth having, and that the headings and their
 * per-month totals come out right.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class HomeBrowseTest {

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

    private static long monthsAgo(int months, int dayOfMonth) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate date = LocalDate.now(zone).withDayOfMonth(1).minusMonths(months)
                .withDayOfMonth(dayOfMonth);
        return date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli();
    }

    private long addOrder(String label, long date, long totalCents, OrderStatus status,
                          String... itemNames) {
        Order order = new Order();
        order.householdId = householdId;
        order.label = label;
        order.orderDate = date;
        order.createdAt = date;
        order.statedTotalCents = totalCents;
        order.status = status;
        long orderId = database.orderDao().insert(order);
        database.participantDao().insertAll(Arrays.asList(
                new OrderParticipant(orderId, anaId),
                new OrderParticipant(orderId, benId)));
        int position = 0;
        for (String name : itemNames) {
            LineItem item = new LineItem();
            item.orderId = orderId;
            item.name = name;
            item.rawOcrText = name;
            item.lineTotalCents = 100L;
            item.quantity = 1;
            item.scope = Scope.UNASSIGNED;
            item.position = position++;
            database.lineItemDao().insert(item);
        }
        return orderId;
    }

    @Test
    public void ordersAreGroupedUnderMonthHeadings() throws Exception {
        addOrder("This one", monthsAgo(0, 2), 1000L, OrderStatus.DRAFT);
        addOrder("Also this one", monthsAgo(0, 1), 2000L, OrderStatus.DRAFT);
        addOrder("Older", monthsAgo(1, 5), 3000L, OrderStatus.DRAFT);

        List<HomeRow> rows = rows(model(3));

        assertEquals(5, rows.size());
        assertTrue(rows.get(0).type == HomeRow.TYPE_HEADER);
        assertEquals(HomeRow.Heading.THIS_MONTH, rows.get(0).heading);
        assertEquals("This one", rows.get(1).order.order.label);
        assertEquals("Also this one", rows.get(2).order.order.label);
        assertTrue(rows.get(3).type == HomeRow.TYPE_HEADER);
        assertEquals(HomeRow.Heading.LAST_MONTH, rows.get(3).heading);
        assertEquals("Older", rows.get(4).order.order.label);
    }

    /** The heading carries the month's own count and total, not the whole list's. */
    @Test
    public void eachHeadingCountsAndTotalsItsOwnMonth() throws Exception {
        addOrder("A", monthsAgo(0, 2), 1000L, OrderStatus.DRAFT);
        addOrder("B", monthsAgo(0, 1), 2000L, OrderStatus.DRAFT);
        addOrder("C", monthsAgo(1, 5), 3000L, OrderStatus.DRAFT);

        List<HomeRow> rows = rows(model(3));

        assertEquals(2, rows.get(0).orderCount);
        assertEquals(3000L, rows.get(0).totalCents);
        assertEquals(1, rows.get(3).orderCount);
        assertEquals(3000L, rows.get(3).totalCents);
    }

    /**
     * The reason the feature is worth having. "Which order had the coffee beans?" is the
     * question a household asks months later, and the label never answers it.
     */
    @Test
    public void searchingFindsAnItemInsideAnOrder() throws Exception {
        addOrder("Weekly shop", monthsAgo(0, 2), 1000L, OrderStatus.DRAFT,
                "Coffee beans", "Whole milk");
        addOrder("Other shop", monthsAgo(0, 1), 2000L, OrderStatus.DRAFT, "Sourdough loaf");

        HomeViewModel model = model(2);
        onMain(() -> model.search("coffee"));

        List<HomeRow> rows = rows(model);
        assertEquals("one heading and one order", 2, rows.size());
        assertEquals("Weekly shop", rows.get(1).order.order.label);
    }

    @Test
    public void searchingMatchesTheOrderLabel() throws Exception {
        addOrder("Weekly shop", monthsAgo(0, 2), 1000L, OrderStatus.DRAFT, "Coffee beans");
        addOrder("Party supplies", monthsAgo(0, 1), 2000L, OrderStatus.DRAFT, "Napkins");

        HomeViewModel model = model(2);
        onMain(() -> model.search("party"));

        List<HomeRow> rows = rows(model);
        assertEquals(2, rows.size());
        assertEquals("Party supplies", rows.get(1).order.order.label);
    }

    @Test
    public void searchingMatchesAParticipantName() throws Exception {
        addOrder("Weekly shop", monthsAgo(0, 2), 1000L, OrderStatus.DRAFT, "Coffee beans");

        HomeViewModel model = model(1);
        onMain(() -> model.search("ana"));

        assertEquals(2, rows(model).size());
    }

    /** A heading whose only order was filtered out must go with it. */
    @Test
    public void anEmptiedMonthLosesItsHeading() throws Exception {
        addOrder("Open one", monthsAgo(0, 2), 1000L, OrderStatus.DRAFT, "Coffee beans");
        addOrder("Settled one", monthsAgo(1, 5), 2000L, OrderStatus.SETTLED, "Napkins");

        HomeViewModel model = model(2);
        onMain(() -> model.filterBy(HomeViewModel.Filter.SETTLED));

        List<HomeRow> rows = rows(model);
        assertEquals(2, rows.size());
        assertEquals(HomeRow.Heading.LAST_MONTH, rows.get(0).heading);
        assertEquals("Settled one", rows.get(1).order.order.label);
    }

    @Test
    public void theOpenFilterHidesSettledOrders() throws Exception {
        addOrder("Draft", monthsAgo(0, 3), 1000L, OrderStatus.DRAFT);
        addOrder("Assigned", monthsAgo(0, 2), 2000L, OrderStatus.ASSIGNED);
        addOrder("Settled", monthsAgo(0, 1), 3000L, OrderStatus.SETTLED);

        HomeViewModel model = model(3);
        onMain(() -> model.filterBy(HomeViewModel.Filter.OPEN));

        List<HomeRow> rows = rows(model);
        assertEquals("a heading and the two unsettled orders", 3, rows.size());
        assertEquals("Draft", rows.get(1).order.order.label);
        assertEquals("Assigned", rows.get(2).order.order.label);
    }

    /** Clearing the search brings everything back. */
    @Test
    public void clearingTheSearchRestoresTheList() throws Exception {
        addOrder("Weekly shop", monthsAgo(0, 2), 1000L, OrderStatus.DRAFT, "Coffee beans");
        addOrder("Other shop", monthsAgo(0, 1), 2000L, OrderStatus.DRAFT, "Napkins");

        HomeViewModel model = model(2);
        onMain(() -> model.search("coffee"));
        assertEquals(2, rows(model).size());

        onMain(() -> model.search(""));

        assertEquals(3, rows(model).size());
    }

    /**
     * "Nothing matches" and "you have no orders" need different wording, so the screen has
     * to be able to tell them apart.
     */
    @Test
    public void anEmptySearchResultIsDistinguishableFromAnEmptyHousehold() throws Exception {
        addOrder("Weekly shop", monthsAgo(0, 2), 1000L, OrderStatus.DRAFT, "Coffee beans");

        HomeViewModel model = model(1);
        assertFalse(model.isFilteredEmpty());

        onMain(() -> model.search("zzzz"));

        assertTrue(rows(model).isEmpty());
        assertTrue("the screen can say nothing matches instead",
                model.isFilteredEmpty());
        assertTrue(model.hasSearchOrFilter());
    }

    @Test
    public void aTrulyEmptyHouseholdIsNotReportedAsFiltered() throws Exception {
        HomeViewModel model = model(0);

        assertTrue(rows(model).isEmpty());
        assertFalse(model.isFilteredEmpty());
        assertFalse(model.hasSearchOrFilter());
    }

    /**
     * Builds the ViewModel and waits for the orders to arrive.
     *
     * <p>The row list is a MediatorLiveData, which only watches its sources while it is
     * itself observed. Observing it here is not test scaffolding for its own sake: it is
     * exactly what the fragment does, and without it nothing would ever be built.
     */
    private HomeViewModel model(int expectedOrders) throws Exception {
        CountDownLatch loaded = new CountDownLatch(1);
        HomeViewModel[] holder = new HomeViewModel[1];
        onMain(() -> {
            HomeViewModel model = new HomeViewModel(locator.householdRepository(),
                    locator.orderRepository());
            holder[0] = model;
            model.rows().observeForever(rows -> {
            });
            model.orders().observeForever(orders -> {
                if (orders != null && orders.size() == expectedOrders) {
                    loaded.countDown();
                }
            });
        });
        assertTrue("the orders never loaded", loaded.await(15, TimeUnit.SECONDS));
        // The item names arrive on a second query; let both settle.
        Thread.sleep(500L);
        return holder[0];
    }

    private List<HomeRow> rows(HomeViewModel model) throws Exception {
        Thread.sleep(200L);
        List<HomeRow> value = model.rows().getValue();
        assertNotNull(value);
        return value;
    }

    private void onMain(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> {
                    action.run();
                    done.countDown();
                });
        assertTrue(done.await(15, TimeUnit.SECONDS));
    }
}
