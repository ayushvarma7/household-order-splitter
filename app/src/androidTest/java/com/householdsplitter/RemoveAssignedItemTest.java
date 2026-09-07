package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import com.householdsplitter.data.entity.OrderParticipant;
import com.householdsplitter.data.relation.LineItemWithAssignments;
import com.householdsplitter.data.repo.OrderRepository;
import com.householdsplitter.util.Result;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Removing a row after it has been answered for, and putting it back.
 *
 * <p>The reader can invent a charge: a struck-through original or a savings note read as an
 * item. Until now the only way out was the review screen, which is behind you once you are
 * assigning.
 *
 * <p>What this has to get right is Undo. Deleting the row cascades its assignments away, so
 * an Undo that restored only the row would put back an item nobody is on, and the user would
 * believe their answer had come back when it had not.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class RemoveAssignedItemTest {

    private AppDatabase database;
    private OrderRepository repository;
    private long orderId;
    private long anaId;
    private long benId;

    @Before
    public void setUp() {
        TestData.wipe();
        database = TestData.database();
        repository = TestData.locator().orderRepository();

        long householdId = database.householdDao().insert(new Household("Flat 12", 1L));
        anaId = database.memberDao().insert(new Member(householdId, "Ana", "#1F6FB2", 0));
        benId = database.memberDao().insert(new Member(householdId, "Ben", "#B3261E", 1));

        Order order = new Order();
        order.householdId = householdId;
        order.label = "Weekly shop";
        order.orderDate = 1L;
        order.createdAt = 1L;
        orderId = database.orderDao().insert(order);
        database.participantDao().insertAll(Arrays.asList(
                new OrderParticipant(orderId, anaId),
                new OrderParticipant(orderId, benId)));
    }

    private long addItem(String name, long cents, int position, Scope scope, long... memberIds) {
        LineItem item = new LineItem();
        item.orderId = orderId;
        item.name = name;
        item.rawOcrText = name;
        item.lineTotalCents = cents;
        item.quantity = 1;
        item.scope = scope;
        item.position = position;
        long id = database.lineItemDao().insert(item);
        if (memberIds.length > 0) {
            ItemAssignment[] assignments = new ItemAssignment[memberIds.length];
            for (int i = 0; i < memberIds.length; i++) {
                assignments[i] = new ItemAssignment(id, memberIds[i], 1);
            }
            database.assignmentDao().insertAll(Arrays.asList(assignments));
        }
        return id;
    }

    /** The case the feature exists for: a phantom row read off a savings note. */
    @Test
    public void anAnsweredRowCanBeRemoved() throws Exception {
        addItem("Fresh Gala Apples, 3 lb Bag", 324L, 0, Scope.COMMON);
        long phantom = addItem("GALA APPLES $1.20 from savings", 444L, 1,
                Scope.SUBSET, anaId);

        Result<OrderRepository.DeletedItem> result = remove(phantom);

        assertTrue(result.error(), result.isOk());
        List<LineItemWithAssignments> rows = database.lineItemDao().getForOrderSync(orderId);
        assertEquals(1, rows.size());
        assertEquals("Fresh Gala Apples, 3 lb Bag", rows.get(0).item.name);
    }

    /** Deleting the row cascades its assignments, so they have to be captured first. */
    @Test
    public void theAnswersComeBackWithTheRow() throws Exception {
        addItem("Milk", 342L, 0, Scope.COMMON);
        long removed = addItem("Coffee beans", 1299L, 1, Scope.SUBSET, anaId, benId);

        Result<OrderRepository.DeletedItem> result = remove(removed);
        assertTrue(result.isOk());
        assertEquals("captured before the delete", 2, result.value().assignments.size());

        restore(result.value());

        List<LineItemWithAssignments> rows = database.lineItemDao().getForOrderSync(orderId);
        assertEquals(2, rows.size());
        LineItemWithAssignments back = rows.get(1);
        assertEquals("Coffee beans", back.item.name);
        assertEquals(Scope.SUBSET, back.item.scope);
        assertEquals("an item nobody is on would be worse than no Undo at all",
                2, back.assignments.size());
    }

    /** The restored row keeps its id, so it lands back in the same place in the order. */
    @Test
    public void theRowComesBackInItsOriginalPosition() throws Exception {
        addItem("First", 100L, 0, Scope.COMMON);
        long middle = addItem("Middle", 200L, 1, Scope.COMMON);
        addItem("Last", 300L, 2, Scope.COMMON);

        Result<OrderRepository.DeletedItem> result = remove(middle);
        assertTrue(result.isOk());
        restore(result.value());

        List<LineItemWithAssignments> rows = database.lineItemDao().getForOrderSync(orderId);
        assertEquals(3, rows.size());
        assertEquals("First", rows.get(0).item.name);
        assertEquals("Middle", rows.get(1).item.name);
        assertEquals("Last", rows.get(2).item.name);
        assertEquals(middle, rows.get(1).item.id);
    }

    /** An unassigned row removes just as well; the answers list simply comes back empty. */
    @Test
    public void anUnansweredRowCanBeRemovedToo() throws Exception {
        addItem("Milk", 342L, 0, Scope.COMMON);
        long unanswered = addItem("Mystery row", 999L, 1, Scope.UNASSIGNED);

        Result<OrderRepository.DeletedItem> result = remove(unanswered);

        assertTrue(result.isOk());
        assertTrue(result.value().assignments.isEmpty());
        assertEquals(1, database.lineItemDao().getForOrderSync(orderId).size());
    }

    @Test
    public void removingARowThatIsAlreadyGoneIsRefused() throws Exception {
        Result<OrderRepository.DeletedItem> result = remove(999_999L);
        assertFalse(result.isOk());
    }

    /** Removing a phantom charge should bring the order back into balance. */
    @Test
    public void theOrderTotalsDropByExactlyTheRemovedAmount() throws Exception {
        addItem("Fresh Gala Apples, 3 lb Bag", 324L, 0, Scope.COMMON);
        long phantom = addItem("GALA APPLES $1.20 from savings", 444L, 1, Scope.COMMON);

        assertEquals(768L, sumOfItems());

        remove(phantom);

        assertEquals(324L, sumOfItems());
    }

    private long sumOfItems() {
        long total = 0L;
        for (LineItemWithAssignments row : database.lineItemDao().getForOrderSync(orderId)) {
            total += row.item.lineTotalCents;
        }
        return total;
    }

    private Result<OrderRepository.DeletedItem> remove(long lineItemId) throws Exception {
        AtomicReference<Result<OrderRepository.DeletedItem>> captured = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        repository.deleteItemForUndo(lineItemId, result -> {
            captured.set(result);
            latch.countDown();
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS));
        return captured.get();
    }

    private void restore(OrderRepository.DeletedItem deleted) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        repository.restoreItem(deleted, result -> latch.countDown());
        assertTrue(latch.await(15, TimeUnit.SECONDS));
    }
}
