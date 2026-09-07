package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.parse.model.ParsedAdjustments;
import com.householdsplitter.core.parse.model.ParsedItem;
import com.householdsplitter.core.parse.model.ParsedOrder;
import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.repo.OrderRepository;
import com.householdsplitter.util.Result;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adding more screenshots to an order that already exists. SPEC 8.1.3.
 *
 * <p>This is what makes "you may have missed a screenshot" actionable rather than merely
 * true. The two things it has to get right are de-duplicating against rows already stored,
 * because a user adding a screenshot overlaps what they already captured, and not
 * overwriting a figure they have corrected by hand.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class AppendParseTest {

    private AppDatabase database;
    private OrderRepository repository;
    private long householdId;
    private long orderId;

    @Before
    public void setUp() {
        TestData.wipe();
        database = TestData.database();
        repository = TestData.locator().orderRepository();
        householdId = database.householdDao().insert(new Household("Fixture Group", 1L));
        database.memberDao().insert(new Member(householdId, "Alpha", "#1F6FB2", 0));
        database.memberDao().insert(new Member(householdId, "Beta", "#B3261E", 1));

        Order order = new Order();
        order.householdId = householdId;
        order.label = "Fixture order";
        order.orderDate = 1L;
        order.createdAt = 1L;
        orderId = database.orderDao().insert(order);

        addExisting("Whole milk", 342L);
        addExisting("Sourdough loaf", 250L);
    }

    private void addExisting(String name, long cents) {
        LineItem item = new LineItem();
        item.orderId = orderId;
        item.name = name;
        item.rawOcrText = name;
        item.lineTotalCents = cents;
        item.scope = Scope.UNASSIGNED;
        item.position = database.lineItemDao().maxPositionSync(orderId) + 1;
        database.lineItemDao().insert(item);
    }

    private static ParsedOrder parseOf(ParsedAdjustments adjustments, String[][] rows) {
        List<ParsedItem> items = new ArrayList<>();
        for (String[] row : rows) {
            items.add(ParsedItem.builder()
                    .name(row[0])
                    .rawOcrText(row[0])
                    .lineTotalCents(Long.parseLong(row[1]))
                    .scope(Scope.UNASSIGNED)
                    .build());
        }
        return ParsedOrder.builder().items(items).adjustments(adjustments).build();
    }

    /** The whole point: new rows join the order rather than starting another one. */
    @Test
    public void newRowsAreAdded() throws Exception {
        int added = append(parseOf(ParsedAdjustments.empty(), new String[][]{
                {"Coffee beans", "899"}, {"Washing up liquid", "180"}}));

        assertEquals(2, added);
        assertEquals(4, database.lineItemDao().getForOrderSync(orderId).size());
    }

    /**
     * A user adding a screenshot overlaps what they already captured, and SPEC 8.7.3's
     * dedupe only ever sees one parse at a time, so the overlap has to be caught here.
     */
    @Test
    public void rowsAlreadyStoredAreNotAddedTwice() throws Exception {
        int added = append(parseOf(ParsedAdjustments.empty(), new String[][]{
                {"Whole milk", "342"},        // already there
                {"Sourdough loaf", "250"},    // already there
                {"Coffee beans", "899"}}));   // new

        assertEquals("only the genuinely new row", 1, added);
        assertEquals(3, database.lineItemDao().getForOrderSync(orderId).size());
    }

    /** The match ignores case and spacing, the way the same row read twice would differ. */
    @Test
    public void theDuplicateCheckToleratesSpacingAndCase() throws Exception {
        int added = append(parseOf(ParsedAdjustments.empty(), new String[][]{
                {"  whole   MILK ", "342"}}));

        assertEquals(0, added);
        assertEquals(2, database.lineItemDao().getForOrderSync(orderId).size());
    }

    /** Same name, different price, is a different charge and must be kept. */
    @Test
    public void aDifferentPriceIsADifferentRow() throws Exception {
        int added = append(parseOf(ParsedAdjustments.empty(), new String[][]{
                {"Whole milk", "399"}}));

        assertEquals(1, added);
        assertEquals(3, database.lineItemDao().getForOrderSync(orderId).size());
    }

    /** A missing figure gets filled in from the new screenshot. */
    @Test
    public void missingTotalsAreFilledIn() throws Exception {
        append(parseOf(new ParsedAdjustments(5352L, 50L, 0L, 0L, 0L, 0L, 5402L),
                new String[][]{{"Coffee beans", "899"}}));

        Order order = database.orderDao().getByIdSync(orderId);
        assertEquals(5352L, order.statedSubtotalCents);
        assertEquals(50L, order.taxCents);
        assertEquals(5402L, order.statedTotalCents);
    }

    /** A figure the user corrected by hand outranks a later parse. */
    @Test
    public void aCorrectedTotalIsNotOverwritten() throws Exception {
        Order order = database.orderDao().getByIdSync(orderId);
        order.statedSubtotalCents = 9999L;
        database.orderDao().update(order);

        append(parseOf(new ParsedAdjustments(5352L, 0L, 0L, 0L, 0L, 0L, 0L),
                new String[][]{{"Coffee beans", "899"}}));

        assertEquals("the user's correction stands", 9999L,
                database.orderDao().getByIdSync(orderId).statedSubtotalCents);
    }

    /** Appending nothing new is not an error, it just reports zero. */
    @Test
    public void appendingNothingNewIsNotAFailure() throws Exception {
        assertEquals(0, append(parseOf(ParsedAdjustments.empty(), new String[][]{
                {"Whole milk", "342"}})));
        assertTrue(database.lineItemDao().getForOrderSync(orderId).size() == 2);
    }

    private int append(ParsedOrder parsed) throws Exception {
        AtomicInteger added = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);
        repository.appendParse(orderId, parsed, new ArrayList<>(),
                (Result<Integer> result) -> {
                    added.set(result.isOk() ? result.value() : -1);
                    latch.countDown();
                });
        assertTrue(latch.await(15, TimeUnit.SECONDS));
        return added.get();
    }
}
