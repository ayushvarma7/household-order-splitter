package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.householdsplitter.core.suggest.StandingRules;
import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.MemberRule;
import com.householdsplitter.suggest.RuleService;
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
 * Standing rules, stored and applied.
 *
 * <p>The matching itself is covered by the JVM tests in :core. This checks the parts that
 * need a database: that a rule survives being written, that the snapshot the assign screen
 * consults is refreshed when one is added or deleted, and that a duplicate is refused rather
 * than firing twice.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class StandingRulesTest {

    private AppDatabase database;
    private RuleService rules;
    private long householdId;
    private long alphaId;
    private long betaId;
    private long gammaId;

    @Before
    public void setUp() {
        TestData.wipe();
        database = TestData.database();
        rules = TestData.locator().ruleService();
        householdId = database.householdDao().insert(new Household("Fixture Group", 1L));
        alphaId = database.memberDao().insert(new Member(householdId, "Alpha", "#1F6FB2", 0));
        betaId = database.memberDao().insert(new Member(householdId, "Beta", "#B3261E", 1));
        gammaId = database.memberDao().insert(new Member(householdId, "Gamma", "#1B5E20", 2));
        refresh();
    }

    @Test
    public void aFreshInstallHasNoRules() {
        assertFalse("nothing is seeded, per SPEC 7.9.13", rules.hasRules());
        assertTrue(rules.snapshot().isEmpty());
    }

    @Test
    public void anAddedRuleTakesEffectWithoutAReload() throws Exception {
        add(betaId, "beer", MemberRule.Kind.EXCLUDE);

        StandingRules.Outcome outcome = rules.apply("Light Beer, 12 pk",
                Arrays.asList(alphaId, betaId, gammaId),
                Arrays.asList(alphaId, betaId, gammaId));

        assertEquals(Arrays.asList(alphaId, gammaId), outcome.members());
        assertEquals(Arrays.asList(betaId), outcome.removed());
    }

    /** The screen names the keyword rather than saying "a rule", so it has to be findable. */
    @Test
    public void theMatchingRuleCanBeNamed() throws Exception {
        add(betaId, "beer", MemberRule.Kind.EXCLUDE);

        MemberRule matched = rules.firstMatch(betaId, "Great Value Light Beer",
                MemberRule.Kind.EXCLUDE);

        assertTrue(matched != null);
        assertEquals("beer", matched.keyword);
    }

    /** A duplicate would fire twice and read as though the app had misunderstood. */
    @Test
    public void aDuplicateIsRefused() throws Exception {
        assertTrue(add(betaId, "beer", MemberRule.Kind.EXCLUDE).isOk());

        Result<Long> second = add(betaId, "BEER", MemberRule.Kind.EXCLUDE);

        assertFalse(second.isOk());
        assertEquals(1, rules.snapshot().size());
    }

    /** The same keyword for a different person is a different rule. */
    @Test
    public void theSameKeywordForSomeoneElseIsAllowed() throws Exception {
        add(betaId, "beer", MemberRule.Kind.EXCLUDE);

        assertTrue(add(gammaId, "beer", MemberRule.Kind.EXCLUDE).isOk());
        assertEquals(2, rules.snapshot().size());
    }

    @Test
    public void anEmptyKeywordIsRefused() throws Exception {
        assertFalse(add(betaId, "   ", MemberRule.Kind.EXCLUDE).isOk());
        assertTrue(rules.snapshot().isEmpty());
    }

    @Test
    public void anOverlongKeywordIsRefused() throws Exception {
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i <= RuleService.MAX_KEYWORD; i++) {
            tooLong.append("a");
        }
        assertFalse(add(betaId, tooLong.toString(), MemberRule.Kind.EXCLUDE).isOk());
    }

    @Test
    public void aDeletedRuleStopsApplying() throws Exception {
        Result<Long> added = add(betaId, "beer", MemberRule.Kind.EXCLUDE);
        assertTrue(added.isOk());

        CountDownLatch latch = new CountDownLatch(1);
        rules.delete(householdId, added.value(), result -> latch.countDown());
        assertTrue(latch.await(15, TimeUnit.SECONDS));

        assertFalse(rules.hasRules());
        StandingRules.Outcome outcome = rules.apply("Light Beer",
                Arrays.asList(alphaId, betaId, gammaId),
                Arrays.asList(alphaId, betaId, gammaId));
        assertFalse(outcome.changedAnything());
    }

    /** Removing a member has to take their rules with them, or the rule outlives the person. */
    @Test
    public void deletingAMemberDeletesTheirRules() throws Exception {
        add(betaId, "beer", MemberRule.Kind.EXCLUDE);

        database.memberDao().delete(database.memberDao().getByIdSync(betaId));
        refresh();

        assertTrue("the foreign key cascade covers this", rules.snapshot().isEmpty());
    }

    private Result<Long> add(long memberId, String keyword, MemberRule.Kind kind)
            throws Exception {
        AtomicReference<Result<Long>> captured = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        rules.add(householdId, memberId, keyword, kind, result -> {
            captured.set(result);
            latch.countDown();
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS));
        return captured.get();
    }

    private void refresh() {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            rules.refresh(householdId, loaded -> latch.countDown());
            assertTrue(latch.await(15, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }
}
