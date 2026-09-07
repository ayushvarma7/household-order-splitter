package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.suggest.AssignmentMemoryService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SPEC 9, against the real database.
 *
 * <p>The rule that matters most here is SPEC 7.9.13 and 9.4: there is no shipped list of
 * which groceries are usually shared. A household that has confirmed nothing gets no
 * suggestions at all, and every suggestion it does get traces to an answer somebody gave.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class AssignmentMemoryTest {

    private AppDatabase database;
    private AssignmentMemoryService memory;
    private long householdId;
    private long alpha;
    private long beta;

    @Before
    public void setUp() {
        TestData.wipe();
        database = TestData.database();
        memory = TestData.locator().assignmentMemory();
        householdId = database.householdDao().insert(new Household("Fixture Group", 1L));
        alpha = database.memberDao().insert(new Member(householdId, "Alpha", "#1F6FB2", 0));
        beta = database.memberDao().insert(new Member(householdId, "Beta", "#B3261E", 1));
    }

    /** A fresh household knows nothing, because the app ships with no item knowledge. */
    @Test
    public void aFreshHouseholdSuggestsNothing() throws Exception {
        assertNull(suggestFor("Great Value Whole Milk, 1 Gal"));
        assertNull(suggestFor("Bananas"));
    }

    /** SPEC 9.2 and 9.3: what was confirmed once comes back next time. */
    @Test
    public void aConfirmedAnswerComesBack() throws Exception {
        remember("Great Value Whole Milk, 1 Gal", Scope.PERSONAL, alpha);

        AssignmentMemoryService.Suggestion suggestion =
                suggestFor("Great Value Whole Milk, 1 Gal");
        assertNotNull(suggestion);
        assertEquals(Scope.PERSONAL, suggestion.scope);
        assertEquals(Arrays.asList(alpha), suggestion.memberIds);
    }

    /**
     * SPEC 9.1: the same product written slightly differently is the same product. Walmart
     * varies the trailing size fragment between orders, which is exactly the case the
     * normalisation exists for.
     */
    @Test
    public void theSameProductMatchesAcrossADifferentSizeFragment() throws Exception {
        remember("Great Value Whole Milk, 1 Gal", Scope.SUBSET, alpha, beta);

        AssignmentMemoryService.Suggestion suggestion = suggestFor("great value whole milk");
        assertNotNull("a differently written size must not defeat the match", suggestion);
        assertEquals(Scope.SUBSET, suggestion.scope);
        assertEquals(2, suggestion.memberIds.size());
    }

    /** A different product must not inherit another one's answer. */
    @Test
    public void aDifferentProductGetsNoSuggestion() throws Exception {
        remember("Great Value Whole Milk, 1 Gal", Scope.PERSONAL, alpha);
        assertNull(suggestFor("Sourdough Loaf"));
    }

    /** SPEC 9.3: the most recent answer wins when they conflict. */
    @Test
    public void theMostRecentAnswerWins() throws Exception {
        remember("Bananas", Scope.PERSONAL, alpha);
        remember("Bananas", Scope.PERSONAL, beta);

        AssignmentMemoryService.Suggestion suggestion = suggestFor("Bananas");
        assertNotNull(suggestion);
        assertEquals(Arrays.asList(beta), suggestion.memberIds);
    }

    /** COMMON is remembered as a scope, with no member list, per SPEC 5.9. */
    @Test
    public void commonIsRememberedWithoutMembers() throws Exception {
        remember("Washing up liquid", Scope.COMMON);

        AssignmentMemoryService.Suggestion suggestion = suggestFor("Washing up liquid");
        assertNotNull(suggestion);
        assertEquals(Scope.COMMON, suggestion.scope);
        assertTrue(suggestion.memberIds.isEmpty());
    }

    /** An excluded row is not an answer about who something is for, so it is not learned. */
    @Test
    public void excludedRowsAreNotLearned() throws Exception {
        remember("Refunded thing", Scope.EXCLUDED, alpha);
        assertNull(suggestFor("Refunded thing"));
    }

    /** SPEC 9.5: clearing the history really clears it. */
    @Test
    public void clearingTheHistoryRemovesEverything() throws Exception {
        remember("Bananas", Scope.COMMON);
        assertNotNull(suggestFor("Bananas"));

        CountDownLatch latch = new CountDownLatch(1);
        memory.clear(householdId, ignored -> latch.countDown());
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        assertNull(suggestFor("Bananas"));
    }

    private void remember(String name, Scope scope, long... memberIds) throws Exception {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (long id : memberIds) {
            ids.add(id);
        }
        memory.remember(householdId, name, scope, ids);
        // The write goes through the disk executor; give it a beat to land.
        Thread.sleep(500);
    }

    private AssignmentMemoryService.Suggestion suggestFor(String name) throws Exception {
        AtomicReference<AssignmentMemoryService.Suggestion> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        memory.suggestFor(householdId, name, suggestion -> {
            result.set(suggestion);
            latch.countDown();
        });
        assertTrue("the lookup should finish promptly", latch.await(10, TimeUnit.SECONDS));
        return result.get();
    }
}
