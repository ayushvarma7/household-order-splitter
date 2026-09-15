package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.parse.StoreKind;
import com.householdsplitter.core.quality.ItemOrigin;
import com.householdsplitter.core.quality.ParserScorecard;
import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.repo.OrderRepository;
import com.householdsplitter.quality.ParserQualityService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * That the reader's score is actually being recorded, end to end through the database.
 *
 * <p>Written because the feature reported nothing after a real order: everything below
 * passes in isolation, so this exists to say which link in the chain is the one that does
 * not hold on a real device.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class ParserQualityTrackingTest {

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

        Order order = new Order();
        order.householdId = householdId;
        order.label = "Sep 06 Amazon Fresh";
        order.orderDate = 1L;
        order.createdAt = 1L;
        order.store = StoreKind.AMAZON_FRESH;
        orderId = database.orderDao().insert(order);
    }

    private LineItem parsedRow(String name, long cents) {
        LineItem item = new LineItem();
        item.orderId = orderId;
        item.name = name;
        item.rawOcrText = name;
        item.lineTotalCents = cents;
        item.scope = Scope.UNASSIGNED;
        item.origin = ItemOrigin.PARSED;
        item.parsedName = name;
        item.parsedCents = cents;
        item.id = database.lineItemDao().insert(item);
        return item;
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue("repository callback never fired", latch.await(5, TimeUnit.SECONDS));
    }

    private ParserQualityService.Report report() {
        return TestData.locator().parserQualityService().buildReport(householdId);
    }

    @Test
    public void aRowLeftAloneCountsAsReadCorrectly() {
        parsedRow("Amazon Grocery, Broccoli Florets, 12 Oz, Frozen", 116L);
        ParserScorecard card = report().overall;

        assertEquals(1, card.judged());
        assertEquals(1, card.keptAsRead());
        assertEquals(1000, card.accuracyPermille());
    }

    @Test
    public void aHandTypedRowIsRecordedAsMissed() throws InterruptedException {
        parsedRow("Amazon Grocery, Broccoli Florets, 12 Oz, Frozen", 116L);

        // Exactly what the Add item button does: a blank row with MANUAL origin, filled in
        // by the edit sheet and saved.
        LineItem typed = new LineItem();
        typed.orderId = orderId;
        typed.origin = ItemOrigin.MANUAL;
        typed.name = "Plum Roma Tomato";
        typed.lineTotalCents = 174L;
        typed.scope = Scope.UNASSIGNED;

        CountDownLatch saved = new CountDownLatch(1);
        repository.saveItem(typed, result -> saved.countDown());
        await(saved);

        assertTrue("saveItem must hand back the new id, or nothing can be diagnosed",
                typed.id != 0L);

        LineItem stored = database.lineItemDao().getByIdSync(typed.id);
        assertNotNull(stored);
        assertEquals("the row must survive the round trip as the user's, not the reader's",
                ItemOrigin.MANUAL, stored.origin);

        ParserQualityService.Report report = report();
        assertEquals("it belongs in the missed list", 1, report.addedByHand.size());
        assertEquals("Plum Roma Tomato", report.addedByHand.get(0).name);
        assertEquals(2, report.overall.judged());
        assertEquals(500, report.overall.accuracyPermille());
        assertEquals(500, report.overall.missedPermille());
    }

    @Test
    public void anEditedRowIsRecordedAsCorrected() throws InterruptedException {
        LineItem row = parsedRow("Amazon Grocery, Red Onions, 2 Lb", 348L);

        row.lineTotalCents = 399L;
        CountDownLatch saved = new CountDownLatch(1);
        repository.saveItem(row, result -> saved.countDown());
        await(saved);

        ParserQualityService.Report report = report();
        assertEquals(1, report.corrected.size());
        assertEquals(0, report.overall.keptAsRead());
        assertEquals("found, but wrong about the money", 1000,
                report.overall.correctedPermille());
    }

    @Test
    public void aDeletedRowIsRecordedAsInvented() throws InterruptedException {
        LineItem row = parsedRow("Item(s) Subtotal", 3893L);

        CountDownLatch deleted = new CountDownLatch(1);
        repository.deleteItem(row, result -> deleted.countDown());
        await(deleted);

        ParserQualityService.Report report = report();
        assertEquals("the evidence survives the row", 1, report.discarded.size());
        assertEquals("Item(s) Subtotal", report.discarded.get(0).name);
        assertEquals(StoreKind.AMAZON_FRESH, report.discarded.get(0).store);
        assertEquals(1000, report.overall.inventedPermille());
    }

    @Test
    public void anUndoneDeletionIsNotHeldAgainstTheReader() throws InterruptedException {
        LineItem row = parsedRow("Banana Bunch (4-5 Count)", 99L);

        CountDownLatch deleted = new CountDownLatch(1);
        repository.deleteItem(row, result -> deleted.countDown());
        await(deleted);
        assertEquals(1, report().discarded.size());

        CountDownLatch restored = new CountDownLatch(1);
        repository.restoreItem(row, result -> restored.countDown());
        await(restored);

        assertEquals("taking a deletion back means it never happened",
                0, report().discarded.size());
        assertEquals(1000, report().overall.accuracyPermille());
    }

    @Test
    public void theScoreIsBrokenDownByStore() throws InterruptedException {
        Order walmart = new Order();
        walmart.householdId = householdId;
        walmart.label = "Sep 03 Walmart";
        walmart.orderDate = 1L;
        walmart.createdAt = 1L;
        walmart.store = StoreKind.WALMART;
        long walmartId = database.orderDao().insert(walmart);

        parsedRow("Amazon Grocery, Broccoli Florets", 116L);

        LineItem onWalmart = new LineItem();
        onWalmart.orderId = walmartId;
        onWalmart.name = "Great Value Milk";
        onWalmart.rawOcrText = "Great Value Milk";
        onWalmart.lineTotalCents = 289L;
        onWalmart.scope = Scope.UNASSIGNED;
        onWalmart.origin = ItemOrigin.MANUAL;
        database.lineItemDao().insert(onWalmart);

        ParserQualityService.Report report = report();
        assertEquals(1000, report.byStore.get(StoreKind.AMAZON_FRESH).accuracyPermille());
        assertEquals(0, report.byStore.get(StoreKind.WALMART).accuracyPermille());
        assertEquals("and comparing the two is the point of splitting them",
                500, report.overall.accuracyPermille());
    }
}
