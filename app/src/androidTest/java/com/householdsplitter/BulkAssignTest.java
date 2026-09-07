package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.repo.OrderRepository;
import com.householdsplitter.suggest.AssignmentMemoryService;
import com.householdsplitter.util.Result;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Answering for many rows in one action.
 *
 * <p>The loop in SPEC 7.9 is one item at a time, which is right for deciding but wrong for a
 * decision already made. These cover the two shortcuts: one answer applied to a selection,
 * and every remembered answer applied at once.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class BulkAssignTest {

    private AppDatabase database;
    private OrderRepository repository;
    private AssignmentMemoryService memory;
    private long householdId;
    private long alpha;
    private long beta;
    private long orderId;
    private final List<Long> itemIds = new ArrayList<>();

    @Before
    public void setUp() {
        TestData.wipe();
        database = TestData.database();
        repository = TestData.locator().orderRepository();
        memory = TestData.locator().assignmentMemory();

        householdId = database.householdDao().insert(new Household("Fixture Group", 1L));
        alpha = database.memberDao().insert(new Member(householdId, "Alpha", "#1F6FB2", 0));
        beta = database.memberDao().insert(new Member(householdId, "Beta", "#B3261E", 1));

        Order order = new Order();
        order.householdId = householdId;
        order.label = "Fixture order";
        order.orderDate = 1L;
        order.createdAt = 1L;
        orderId = database.orderDao().insert(order);
        database.participantDao().replaceForOrder(orderId, Arrays.asList(alpha, beta));

        itemIds.clear();
        String[] names = {"Whole milk", "Sourdough loaf", "Coffee beans", "Washing up liquid"};
        for (int i = 0; i < names.length; i++) {
            LineItem item = new LineItem();
            item.orderId = orderId;
            item.name = names[i];
            item.rawOcrText = names[i];
            item.lineTotalCents = 100L * (i + 1);
            item.scope = Scope.UNASSIGNED;
            item.position = i;
            itemIds.add(database.lineItemDao().insert(item));
        }
    }

    /** One answer, four rows, one action. */
    @Test
    public void oneAnswerAppliesToEveryRowSelected() throws Exception {
        assertEquals(4, applyMany(itemIds, Scope.COMMON, Collections.emptyList()));

        for (Long id : itemIds) {
            assertEquals(Scope.COMMON, database.lineItemDao().getByIdSync(id).scope);
        }
        assertEquals(0, database.lineItemDao().unassignedCountSync(orderId));
    }

    /** SPEC 5.9: a COMMON row keeps no assignment rows, however it got there. */
    @Test
    public void bulkCommonLeavesNoAssignmentRows() throws Exception {
        applyMany(itemIds, Scope.PERSONAL, Collections.singletonList(alpha));
        assertEquals(1, database.assignmentDao().getForItemSync(itemIds.get(0)).size());

        applyMany(itemIds, Scope.COMMON, Collections.emptyList());
        for (Long id : itemIds) {
            assertTrue(database.assignmentDao().getForItemSync(id).isEmpty());
        }
    }

    @Test
    public void bulkPersonalAssignsEveryRowToThatPerson() throws Exception {
        applyMany(itemIds, Scope.PERSONAL, Collections.singletonList(beta));

        for (Long id : itemIds) {
            assertEquals(Scope.PERSONAL, database.lineItemDao().getByIdSync(id).scope);
            assertEquals(1, database.assignmentDao().getForItemSync(id).size());
            assertEquals(beta, database.assignmentDao().getForItemSync(id).get(0).memberId);
        }
    }

    /** Only the rows selected change; the rest are left alone. */
    @Test
    public void bulkOnlyTouchesTheSelection() throws Exception {
        applyMany(Arrays.asList(itemIds.get(0), itemIds.get(1)), Scope.COMMON,
                Collections.emptyList());

        assertEquals(Scope.COMMON, database.lineItemDao().getByIdSync(itemIds.get(0)).scope);
        assertEquals(Scope.UNASSIGNED, database.lineItemDao().getByIdSync(itemIds.get(2)).scope);
        assertEquals(2, database.lineItemDao().unassignedCountSync(orderId));
    }

    /**
     * The repeat-shop shortcut: answers confirmed on a previous order come back in one
     * action, and rows with nothing remembered are left for the user.
     */
    @Test
    public void everyRememberedAnswerAppliesAtOnce() throws Exception {
        remember("Whole milk", Scope.COMMON);
        remember("Coffee beans", Scope.PERSONAL, alpha);
        // Nothing remembered for the loaf or the washing up liquid.

        List<String> names = new ArrayList<>();
        for (Long id : itemIds) {
            names.add(database.lineItemDao().getByIdSync(id).name);
        }

        List<AssignmentMemoryService.Suggestion> suggestions = suggestAll(names);
        assertEquals(4, suggestions.size());

        List<Scope> scopes = new ArrayList<>();
        List<List<Long>> members = new ArrayList<>();
        for (AssignmentMemoryService.Suggestion suggestion : suggestions) {
            scopes.add(suggestion == null ? null : suggestion.scope);
            members.add(suggestion == null ? null : suggestion.memberIds);
        }

        AtomicInteger applied = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);
        repository.applyRememberedAnswers(itemIds, scopes, members,
                (Result<Integer> result) -> {
                    applied.set(result.isOk() ? result.value() : -1);
                    latch.countDown();
                });
        assertTrue(latch.await(15, TimeUnit.SECONDS));

        assertEquals("two rows were remembered, two were not", 2, applied.get());
        assertEquals(Scope.COMMON, database.lineItemDao().getByIdSync(itemIds.get(0)).scope);
        assertEquals(Scope.PERSONAL, database.lineItemDao().getByIdSync(itemIds.get(2)).scope);
        // The unremembered rows are still the user's to answer, not guessed at.
        assertEquals(Scope.UNASSIGNED, database.lineItemDao().getByIdSync(itemIds.get(1)).scope);
        assertEquals(Scope.UNASSIGNED, database.lineItemDao().getByIdSync(itemIds.get(3)).scope);
    }

    /** A household with no history applies nothing, because there is nothing to apply. */
    @Test
    public void nothingRememberedAppliesNothing() throws Exception {
        List<String> names = Arrays.asList("Whole milk", "Sourdough loaf");
        for (AssignmentMemoryService.Suggestion suggestion : suggestAll(names)) {
            org.junit.Assert.assertNull(suggestion);
        }
    }

    private int applyMany(List<Long> ids, Scope scope, List<Long> memberIds) throws Exception {
        AtomicInteger changed = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);
        repository.assignMany(ids, scope, memberIds, null, (Result<Integer> result) -> {
            changed.set(result.isOk() ? result.value() : -1);
            latch.countDown();
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS));
        return changed.get();
    }

    private void remember(String name, Scope scope, long... memberIds) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (long id : memberIds) {
            ids.add(id);
        }
        memory.remember(householdId, name, scope, ids);
        Thread.sleep(400);
    }

    private List<AssignmentMemoryService.Suggestion> suggestAll(List<String> names)
            throws Exception {
        final List<AssignmentMemoryService.Suggestion>[] holder = new List[1];
        CountDownLatch latch = new CountDownLatch(1);
        memory.suggestForAll(householdId, names, suggestions -> {
            holder[0] = suggestions;
            latch.countDown();
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS));
        return holder[0];
    }
}
