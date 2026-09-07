package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.householdsplitter.core.calc.AllocationMode;
import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.calc.SplitCalculator;
import com.householdsplitter.core.calc.UnassignedItemsException;
import com.householdsplitter.core.calc.result.SplitResult;
import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.DraftStep;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.ItemAssignment;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderParticipant;
import com.householdsplitter.data.mapper.CalcMapper;
import com.householdsplitter.data.relation.OrderBundle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SPEC 12.5.5 and 12.5.6, plus the persistence rules of SPEC 5.10 and 7.8.5, exercised
 * against a real Room database on a device.
 *
 * <p>Member names here are fixtures created inside the test and appear nowhere in the app
 * (SPEC 1.6, SPEC 12.2).
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class OrderFlowTest {

    private AppDatabase database;
    private long householdId;
    private long alpha;
    private long beta;

    @Before
    public void setUp() {
        TestData.wipe();
        database = TestData.database();
        householdId = database.householdDao().insert(new Household("Fixture Group", 1L));
        alpha = database.memberDao().insert(new Member(householdId, "Alpha", "#1F77B4", 0));
        beta = database.memberDao().insert(new Member(householdId, "Beta", "#D62728", 1));
    }

    private long newOrder() {
        Order order = new Order();
        order.householdId = householdId;
        order.label = "Fixture order";
        order.orderDate = 1L;
        order.createdAt = 1L;
        order.taxCents = 50L;
        order.statedTotalCents = 1050L;
        long orderId = database.orderDao().insert(order);
        database.participantDao().replaceForOrder(orderId, Arrays.asList(alpha, beta));
        return orderId;
    }

    private long addItem(long orderId, String name, long cents, Scope scope, int position) {
        LineItem item = new LineItem();
        item.orderId = orderId;
        item.name = name;
        item.rawOcrText = name;
        item.lineTotalCents = cents;
        item.scope = scope;
        item.position = position;
        return database.lineItemDao().insert(item);
    }

    /** SPEC 12.5.6 and 7.10.6: an unanswered row blocks the summary and names itself. */
    @Test
    public void unassignedItemBlocksTheSummaryAndIsListed() {
        long orderId = newOrder();
        addItem(orderId, "Shared thing", 600L, Scope.COMMON, 0);
        addItem(orderId, "Who is this for", 400L, Scope.UNASSIGNED, 1);

        OrderBundle bundle = database.orderDao().getBundleSync(orderId);
        try {
            SplitCalculator.calculate(CalcMapper.toCalcOrder(bundle, AllocationMode.PROPORTIONAL));
            fail("the summary must not compute while a row is unanswered");
        } catch (UnassignedItemsException blocked) {
            assertEquals(1, blocked.itemNames().size());
            assertEquals("Who is this for", blocked.itemNames().get(0));
        }

        // And the query behind the blocking panel finds the same row.
        List<LineItem> unassigned = database.lineItemDao().getUnassignedSync(orderId);
        assertEquals(1, unassigned.size());
        assertEquals("Who is this for", unassigned.get(0).name);
    }

    /** SPEC 12.5.5 and 7.9.14: the draft resumes on the item it stopped at. */
    @Test
    public void draftProgressSurvivesAKill() {
        long orderId = newOrder();
        for (int i = 0; i < 5; i++) {
            addItem(orderId, "Item " + i, 100L + i, Scope.UNASSIGNED, i);
        }
        database.orderDao().updateProgress(orderId, DraftStep.ASSIGN, 3);

        // A relaunch reads the same row back, because it is in the database, not in memory.
        Order reloaded = database.orderDao().getByIdSync(orderId);
        assertNotNull(reloaded);
        assertEquals(DraftStep.ASSIGN, reloaded.draftStep);
        assertEquals(3, reloaded.draftItemPosition);
    }

    /** SPEC 5.9: writing COMMON clears the row's assignments so it tracks the participants. */
    @Test
    public void commonItemsKeepNoAssignmentRows() {
        long orderId = newOrder();
        long itemId = addItem(orderId, "Shared thing", 600L, Scope.SUBSET, 0);
        database.assignmentDao().insertAll(Arrays.asList(new ItemAssignment(itemId, alpha, 1)));

        database.assignmentDao().deleteForItem(itemId);
        database.lineItemDao().updateScope(itemId, Scope.COMMON);

        assertTrue(database.assignmentDao().getForItemSync(itemId).isEmpty());
    }

    /**
     * SPEC 7.8.5: dropping a participant drops their assignments and leaves their items
     * needing an answer again, rather than silently charging nobody.
     */
    @Test
    public void removingAParticipantUnassignsTheirItems() {
        long orderId = newOrder();
        long itemId = addItem(orderId, "Beta's thing", 400L, Scope.PERSONAL, 0);
        database.assignmentDao().insertAll(Arrays.asList(new ItemAssignment(itemId, beta, 1)));

        database.participantDao().replaceForOrder(orderId, Arrays.asList(alpha));
        database.assignmentDao().deleteForMemberInOrder(orderId, beta);
        for (Long orphan : database.assignmentDao().itemsLeftWithoutAssigneesSync(orderId)) {
            database.lineItemDao().updateScope(orphan, Scope.UNASSIGNED);
        }

        assertEquals(Scope.UNASSIGNED, database.lineItemDao().getByIdSync(itemId).scope);
    }

    /** SPEC 5.10 and 11.7: a member with history is archived, so old orders still name them. */
    @Test
    public void memberWithHistoryIsArchivedNotDeleted() {
        long orderId = newOrder();
        long itemId = addItem(orderId, "Beta's thing", 400L, Scope.PERSONAL, 0);
        database.assignmentDao().insertAll(Arrays.asList(new ItemAssignment(itemId, beta, 1)));

        assertTrue(database.memberDao().historyCountSync(beta) > 0);
        database.memberDao().archive(beta);

        assertEquals(1, database.memberDao().activeCountSync(householdId));
        // Still present, and still named, for the order that used them.
        assertEquals("Beta", database.memberDao().getByIdSync(beta).name);
    }

    /** The whole chain on a device: totals sum exactly to the computed total (SPEC 6.4.13). */
    @Test
    public void endToEndTotalsSumExactly() {
        long orderId = newOrder();
        addItem(orderId, "Shared thing", 600L, Scope.COMMON, 0);
        long mine = addItem(orderId, "Alpha's thing", 400L, Scope.PERSONAL, 1);
        database.assignmentDao().insertAll(Arrays.asList(new ItemAssignment(mine, alpha, 1)));

        OrderBundle bundle = database.orderDao().getBundleSync(orderId);
        SplitResult result = SplitCalculator.calculate(
                CalcMapper.toCalcOrder(bundle, AllocationMode.PROPORTIONAL));

        long sum = 0L;
        List<Long> perMember = new ArrayList<>();
        for (com.householdsplitter.core.calc.result.MemberSplit member : result.members()) {
            sum += member.finalCents();
            perMember.add(member.finalCents());
        }
        assertEquals(result.computedTotalCents(), sum);
        assertEquals(1050L, sum);
        assertTrue("matches the stated total", result.matchesStatedTotal());
        assertEquals(2, perMember.size());
    }

    /**
     * SPEC 7.6.9: a row the parser could not name must block the review screen.
     *
     * <p>It carries a placeholder rather than an empty name, because dropping the charge
     * would leave the totals short with nothing to point at. The placeholder is not a name,
     * so it has to block just as an empty one would.
     */
    @Test
    public void aRowWhoseNameWasNotReadBlocksTheReviewScreen() {
        long orderId = newOrder();
        long unnamed = addItem(orderId,
                com.householdsplitter.core.parse.model.ParsedItem.NAME_NOT_READ,
                745L, Scope.UNASSIGNED, 0);

        LineItem row = database.lineItemDao().getByIdSync(unnamed);
        org.junit.Assert.assertFalse("an unidentified charge must not be splittable",
                row.isReadyForSplitting());

        row.name = "Sourdough loaf";
        database.lineItemDao().update(row);
        org.junit.Assert.assertTrue(
                database.lineItemDao().getByIdSync(unnamed).isReadyForSplitting());
    }

    /** An empty name blocks too, and so does a zero price. */
    @Test
    public void emptyNamesAndZeroPricesBlock() {
        long orderId = newOrder();
        long blank = addItem(orderId, "", 500L, Scope.UNASSIGNED, 0);
        long free = addItem(orderId, "Something", 0L, Scope.UNASSIGNED, 1);

        org.junit.Assert.assertFalse(
                database.lineItemDao().getByIdSync(blank).isReadyForSplitting());
        org.junit.Assert.assertFalse(
                database.lineItemDao().getByIdSync(free).isReadyForSplitting());
    }

    /** SPEC 5.3: the unique index makes the duplicate-import guard real. */
    @Test
    public void duplicateOrderNumberIsRejectedByTheIndex() {
        Order first = new Order();
        first.householdId = householdId;
        first.label = "First";
        first.externalOrderNo = "1000001-12345678";
        database.orderDao().insert(first);

        Order second = new Order();
        second.householdId = householdId;
        second.label = "Second";
        second.externalOrderNo = "1000001-12345678";
        try {
            database.orderDao().insert(second);
            fail("the unique index should have rejected a second order with the same number");
        } catch (RuntimeException expected) {
            assertNotNull(database.orderDao().findByExternalOrderNoSync("1000001-12345678"));
        }

        // Two orders with no number at all are fine: SQLite allows repeated NULLs.
        Order third = new Order();
        third.householdId = householdId;
        third.label = "Third";
        database.orderDao().insert(third);
        Order fourth = new Order();
        fourth.householdId = householdId;
        fourth.label = "Fourth";
        database.orderDao().insert(fourth);
    }
}
