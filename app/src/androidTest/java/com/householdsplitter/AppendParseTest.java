package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.parse.model.ItemBounds;
import com.householdsplitter.core.parse.model.ParsedAdjustments;
import com.householdsplitter.core.parse.model.ParsedItem;
import com.householdsplitter.core.parse.model.ParsedOrder;
import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.DiscardedRow;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderImage;
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

    /** A parsed row read from the {@code index}-th screenshot of its batch. */
    private static ParsedItem fromScreenshot(String name, long cents, int index) {
        return ParsedItem.builder()
                .name(name)
                .rawOcrText(name)
                .lineTotalCents(cents)
                .scope(Scope.UNASSIGNED)
                .bounds(new ItemBounds(index, 100, 200, 900, 260))
                .build();
    }

    private void addImages(String... uris) {
        List<OrderImage> images = new ArrayList<>();
        int start = database.orderImageDao().getForOrderSync(orderId).size();
        for (int i = 0; i < uris.length; i++) {
            images.add(new OrderImage(orderId, uris[i], start + i));
        }
        database.orderImageDao().insertAll(images);
    }

    private int append(ParsedOrder parsed, List<String> uris, boolean readAll) throws Exception {
        AtomicInteger added = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);
        repository.appendParse(orderId, parsed, uris, readAll, (Result<Integer> result) -> {
            added.set(result.isOk() ? result.value() : -1);
            latch.countDown();
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS));
        return added.get();
    }

    private LineItem row(String name) {
        for (var row : database.lineItemDao().getForOrderSync(orderId)) {
            if (name.equals(row.item.name)) {
                return row.item;
            }
        }
        throw new AssertionError("no row called " + name);
    }

    /**
     * Only the new screenshots were read, so a row's screenshot number counts from the
     * first of them. It has to count from the order's first screenshot instead, or "View
     * on screenshot" opens the wrong page.
     */
    @Test
    public void aRowFromAnAddedScreenshotPointsAtThatScreenshot() throws Exception {
        addImages("content://shots/1", "content://shots/2", "content://shots/3");
        ParsedOrder parsed = ParsedOrder.builder()
                .items(Arrays.asList(fromScreenshot("Coffee beans", 899L, 0),
                        fromScreenshot("Tea bags", 415L, 1)))
                .adjustments(ParsedAdjustments.empty()).build();

        append(parsed, Arrays.asList("content://shots/4", "content://shots/5"), false);

        assertEquals("the order's fourth screenshot, not its first", 3,
                row("Coffee beans").sourceImageIndex);
        assertEquals(4, row("Tea bags").sourceImageIndex);
        List<OrderImage> images = database.orderImageDao().getForOrderSync(orderId);
        assertEquals(5, images.size());
    }

    /** Reading everything again stores only the screenshots the order did not have. */
    @Test
    public void readingAllAgainStoresOnlyTheNewScreenshots() throws Exception {
        addImages("content://shots/1", "content://shots/2");
        ParsedOrder parsed = ParsedOrder.builder()
                .items(Arrays.asList(fromScreenshot("Coffee beans", 899L, 2)))
                .adjustments(ParsedAdjustments.empty()).build();

        append(parsed, Arrays.asList("content://shots/1", "content://shots/2",
                "content://shots/3"), true);

        List<OrderImage> images = database.orderImageDao().getForOrderSync(orderId);
        assertEquals("no screenshot stored twice", 3, images.size());
        assertEquals("indices already count from the order's first screenshot", 2,
                row("Coffee beans").sourceImageIndex);
    }

    /**
     * The user renamed what the reader wrote. Reading the screenshot again produces the
     * reader's name, which no longer matches any row, and must not come back as a second.
     */
    @Test
    public void aRowTheUserRenamedIsNotReadBackInAgain() throws Exception {
        LineItem chicken = new LineItem();
        chicken.orderId = orderId;
        chicken.name = "Freshness Guaranteed Chicken";
        chicken.parsedName = "Freshness Guaranteed";
        chicken.parsedCents = 1166L;
        chicken.lineTotalCents = 1166L;
        chicken.scope = Scope.UNASSIGNED;
        chicken.position = 5;
        database.lineItemDao().insert(chicken);

        int added = append(parseOf(ParsedAdjustments.empty(), new String[][]{
                {"Freshness Guaranteed", "1166"}}), new ArrayList<>(), true);

        assertEquals(0, added);
    }

    /** Nor does a row the user deleted as wrong: that is exactly the row they did not want. */
    @Test
    public void aRowTheUserDeletedIsNotReadBackInAgain() throws Exception {
        DiscardedRow phantom = new DiscardedRow();
        phantom.orderId = orderId;
        phantom.name = "Boneless, Skinless Chicken Breasts";
        phantom.lineTotalCents = 4467L;
        phantom.discardedAt = 1L;
        database.discardedRowDao().insert(phantom);

        int added = append(parseOf(ParsedAdjustments.empty(), new String[][]{
                {"Boneless, Skinless Chicken Breasts", "4467"}}), new ArrayList<>(), true);

        assertEquals("the phantom row stays deleted", 0, added);
    }
}
