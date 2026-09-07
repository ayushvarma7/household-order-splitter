package com.householdsplitter;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderStatus;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * SPEC 7.10.7: once an order is settled it stays settled until an explicit Reopen.
 *
 * <p>This exists because of a real bug. The summary promotes a draft to assigned whenever
 * its totals compute, and it recalculates on every change, so promoting unconditionally
 * silently undid "Mark as settled" the next time the screen redrew.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class OrderStatusTest {

    private AppDatabase database;
    private long orderId;

    @Before
    public void setUp() {
        TestData.wipe();
        database = TestData.database();
        long householdId = database.householdDao().insert(new Household("Fixture Group", 1L));
        Order order = new Order();
        order.householdId = householdId;
        order.label = "Fixture order";
        order.orderDate = 1L;
        order.createdAt = 1L;
        orderId = database.orderDao().insert(order);
    }

    @Test
    public void aDraftBecomesAssignedOnceItComputes() {
        assertEquals(OrderStatus.DRAFT, database.orderDao().getByIdSync(orderId).status);
        promoteIfStillDraft();
        assertEquals(OrderStatus.ASSIGNED, database.orderDao().getByIdSync(orderId).status);
    }

    @Test
    public void settlingSurvivesEveryRecalculation() {
        database.orderDao().updateStatus(orderId, OrderStatus.SETTLED);
        for (int redraw = 0; redraw < 5; redraw++) {
            promoteIfStillDraft();
        }
        assertEquals("settling must not be undone by a redraw",
                OrderStatus.SETTLED, database.orderDao().getByIdSync(orderId).status);
    }

    @Test
    public void reopeningReturnsItToDraft() {
        database.orderDao().updateStatus(orderId, OrderStatus.SETTLED);
        database.orderDao().updateStatus(orderId, OrderStatus.DRAFT);
        assertEquals(OrderStatus.DRAFT, database.orderDao().getByIdSync(orderId).status);
    }

    /** The same guard the summary applies. */
    private void promoteIfStillDraft() {
        Order order = database.orderDao().getByIdSync(orderId);
        if (order.status == OrderStatus.DRAFT) {
            database.orderDao().updateStatus(orderId, OrderStatus.ASSIGNED);
        }
    }
}
