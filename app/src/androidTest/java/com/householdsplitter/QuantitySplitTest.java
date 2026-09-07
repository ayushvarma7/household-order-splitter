package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.ItemAssignment;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.relation.LineItemWithAssignments;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Splitting a multi-quantity row into one row per item. SPEC 11.1.
 *
 * <p>A two-pack bought for one person and one for another cannot be answered on a single
 * row, and the alternative is the user deleting the row and typing two by hand. The thing
 * this has to get exactly right is the money: the parts must add back to the original to
 * the penny, because the order's own subtotal check (SPEC 6.4) will catch it if they do not.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class QuantitySplitTest {

    private AppDatabase database;
    private OrderRepository repository;
    private long orderId;
    private long alphaId;
    private long betaId;

    @Before
    public void setUp() {
        TestData.wipe();
        database = TestData.database();
        repository = TestData.locator().orderRepository();
        long householdId = database.householdDao().insert(new Household("Fixture Group", 1L));
        alphaId = database.memberDao().insert(new Member(householdId, "Alpha", "#1F6FB2", 0));
        betaId = database.memberDao().insert(new Member(householdId, "Beta", "#B3261E", 1));

        Order order = new Order();
        order.householdId = householdId;
        order.label = "Fixture order";
        order.orderDate = 1L;
        order.createdAt = 1L;
        orderId = database.orderDao().insert(order);
    }

    private long addItem(String name, long cents, int quantity, int position) {
        LineItem item = new LineItem();
        item.orderId = orderId;
        item.name = name;
        item.rawOcrText = name;
        item.lineTotalCents = cents;
        item.quantity = quantity;
        item.scope = Scope.UNASSIGNED;
        item.position = position;
        item.needsReview = quantity > 1;
        item.reviewReasonsCsv = quantity > 1 ? "QUANTITY_ABOVE_ONE" : null;
        return database.lineItemDao().insert(item);
    }

    @Test
    public void aTwoPackBecomesTwoRows() throws Exception {
        long id = addItem("Yogurt 2 pack", 504L, 2, 0);

        assertEquals(2, split(id));

        List<LineItemWithAssignments> rows = database.lineItemDao().getForOrderSync(orderId);
        assertEquals(2, rows.size());
        for (LineItemWithAssignments row : rows) {
            assertEquals(1, row.item.quantity);
            assertEquals(252L, row.item.lineTotalCents);
            assertEquals("Yogurt 2 pack", row.item.name);
        }
    }

    /**
     * The case a naive division loses a penny on. 505 over two is not 252 twice, and the
     * missing cent would surface later as a subtotal that does not reconcile.
     */
    @Test
    public void anOddAmountKeepsEveryPenny() throws Exception {
        long id = addItem("Yogurt 2 pack", 505L, 2, 0);

        split(id);

        List<LineItemWithAssignments> rows = database.lineItemDao().getForOrderSync(orderId);
        long sum = 0L;
        List<Long> amounts = new ArrayList<>();
        for (LineItemWithAssignments row : rows) {
            sum += row.item.lineTotalCents;
            amounts.add(row.item.lineTotalCents);
        }
        assertEquals("the parts add back to the original", 505L, sum);
        assertTrue("the extra penny goes to the first row, not nowhere",
                amounts.containsAll(Arrays.asList(253L, 252L)));
    }

    /** A three-way division of an amount that does not divide evenly. */
    @Test
    public void aThreePackKeepsEveryPenny() throws Exception {
        long id = addItem("Soap 3 pack", 1000L, 3, 0);

        assertEquals(3, split(id));

        long sum = 0L;
        for (LineItemWithAssignments row : database.lineItemDao().getForOrderSync(orderId)) {
            sum += row.item.lineTotalCents;
        }
        assertEquals(1000L, sum);
    }

    /** The rows go where the original was, and the rest of the order moves down. */
    @Test
    public void laterRowsShiftDownToMakeRoom() throws Exception {
        addItem("Milk", 342L, 1, 0);
        long id = addItem("Yogurt 2 pack", 504L, 2, 1);
        addItem("Bread", 250L, 1, 2);

        split(id);

        List<LineItemWithAssignments> rows = database.lineItemDao().getForOrderSync(orderId);
        assertEquals(4, rows.size());
        // getForOrderSync is ordered by position, so the order of names is the assertion.
        assertEquals("Milk", rows.get(0).item.name);
        assertEquals("Yogurt 2 pack", rows.get(1).item.name);
        assertEquals("Yogurt 2 pack", rows.get(2).item.name);
        assertEquals("Bread", rows.get(3).item.name);
        for (int i = 0; i < rows.size(); i++) {
            assertEquals("positions stay contiguous", i, rows.get(i).item.position);
        }
    }

    /** The "quantity above one" warning is what the split resolves, so it must clear. */
    @Test
    public void theQuantityWarningClears() throws Exception {
        long id = addItem("Yogurt 2 pack", 504L, 2, 0);
        assertTrue(database.lineItemDao().getByIdSync(id).needsReview);

        split(id);

        for (LineItemWithAssignments row : database.lineItemDao().getForOrderSync(orderId)) {
            assertFalse(row.item.needsReview);
        }
    }

    /**
     * Deleting the original cascades its assignments away. Carrying them over is what keeps
     * an answered item answered; without it the split would silently unassign it and SPEC
     * 6.4's unassigned check would block the summary.
     */
    @Test
    public void anAnsweredRowStaysAnswered() throws Exception {
        long id = addItem("Yogurt 2 pack", 504L, 2, 0);
        LineItem stored = database.lineItemDao().getByIdSync(id);
        stored.scope = Scope.SUBSET;
        database.lineItemDao().update(stored);
        database.assignmentDao().insertAll(Arrays.asList(
                new ItemAssignment(id, alphaId, 1),
                new ItemAssignment(id, betaId, 2)));

        split(id);

        List<LineItemWithAssignments> rows = database.lineItemDao().getForOrderSync(orderId);
        assertEquals(2, rows.size());
        for (LineItemWithAssignments row : rows) {
            assertEquals(Scope.SUBSET, row.item.scope);
            assertEquals("both members carried across", 2, row.assignments.size());
            for (ItemAssignment assignment : row.assignments) {
                assertEquals("the share weights carried across too",
                        assignment.memberId == betaId ? 2 : 1, assignment.shares);
            }
        }
    }

    /** Nothing to split, and saying so beats creating a pointless duplicate row. */
    @Test
    public void aSingleItemRowIsRejected() throws Exception {
        long id = addItem("Milk", 342L, 1, 0);

        Result<Integer> result = attempt(id);

        assertFalse(result.isOk());
        assertEquals(1, database.lineItemDao().getForOrderSync(orderId).size());
    }

    @Test
    public void aMissingRowIsRejected() throws Exception {
        Result<Integer> result = attempt(999_999L);
        assertFalse(result.isOk());
    }

    private int split(long lineItemId) throws Exception {
        Result<Integer> result = attempt(lineItemId);
        assertTrue("split failed: " + result.error(), result.isOk());
        return result.value();
    }

    private Result<Integer> attempt(long lineItemId) throws Exception {
        AtomicReference<Result<Integer>> captured = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        repository.splitByQuantity(lineItemId, result -> {
            captured.set(result);
            latch.countDown();
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS));
        return captured.get();
    }
}
