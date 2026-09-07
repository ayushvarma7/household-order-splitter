package com.householdsplitter.core.suggest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Member ids here are test fixtures. */
public class StandingRulesTest {

    private static final long A = 1L;
    private static final long B = 2L;
    private static final long C = 3L;
    private static final List<Long> EVERYONE = Arrays.asList(A, B, C);

    @Test
    public void noRulesChangesNothing() {
        StandingRules.Outcome outcome =
                StandingRules.apply("Beer, 6 pack", EVERYONE, EVERYONE, Collections.emptyList());
        assertEquals(EVERYONE, outcome.members());
        assertFalse(outcome.changedAnything());
    }

    /** The case the feature exists for. */
    @Test
    public void anExcludeRuleTakesThatPersonOff() {
        StandingRules.Outcome outcome = StandingRules.apply("Beer, 6 pack", EVERYONE, EVERYONE,
                Arrays.asList(new StandingRules.Rule(B, "beer", StandingRules.Kind.EXCLUDE)));

        assertEquals(Arrays.asList(A, C), outcome.members());
        assertEquals(Arrays.asList(B), outcome.removed());
        assertTrue(outcome.changedAnything());
    }

    @Test
    public void aRuleThatDoesNotMatchIsIgnored() {
        StandingRules.Outcome outcome = StandingRules.apply("Whole milk", EVERYONE, EVERYONE,
                Arrays.asList(new StandingRules.Rule(B, "beer", StandingRules.Kind.EXCLUDE)));
        assertEquals(EVERYONE, outcome.members());
        assertFalse(outcome.changedAnything());
    }

    @Test
    public void matchingIsCaseInsensitiveAndPartial() {
        StandingRules.Outcome outcome = StandingRules.apply(
                "Great Value Light BEER, 12 Pack", EVERYONE, EVERYONE,
                Arrays.asList(new StandingRules.Rule(B, "beer", StandingRules.Kind.EXCLUDE)));
        assertEquals(Arrays.asList(A, C), outcome.members());
    }

    @Test
    public void anIncludeRulePutsThatPersonOn() {
        StandingRules.Outcome outcome = StandingRules.apply("Oat milk", Arrays.asList(A),
                EVERYONE,
                Arrays.asList(new StandingRules.Rule(C, "oat milk", StandingRules.Kind.INCLUDE)));

        assertEquals(Arrays.asList(A, C), outcome.members());
        assertEquals(Arrays.asList(C), outcome.added());
    }

    /** A household that has said somebody is never on a thing means it. */
    @Test
    public void excludeBeatsIncludeOnAConflict() {
        StandingRules.Outcome outcome = StandingRules.apply("Beer", EVERYONE, EVERYONE,
                Arrays.asList(
                        new StandingRules.Rule(B, "beer", StandingRules.Kind.INCLUDE),
                        new StandingRules.Rule(B, "beer", StandingRules.Kind.EXCLUDE)));

        assertFalse(outcome.members().contains(B));
        assertEquals(Arrays.asList(B), outcome.removed());
        assertTrue(outcome.added().isEmpty());
    }

    /** SPEC 7.8.5: a rule cannot put somebody on an order they are not in on. */
    @Test
    public void aRuleCannotAddANonParticipant() {
        StandingRules.Outcome outcome = StandingRules.apply("Oat milk", Arrays.asList(A),
                Arrays.asList(A, B),
                Arrays.asList(new StandingRules.Rule(C, "oat milk", StandingRules.Kind.INCLUDE)));

        assertEquals(Arrays.asList(A), outcome.members());
        assertTrue(outcome.added().isEmpty());
    }

    /**
     * A rule must never empty an item. That would turn a charge somebody owes into one
     * nobody owes, and the order would stop adding up.
     */
    @Test
    public void rulesNeverLeaveAnItemWithNobodyOnIt() {
        StandingRules.Outcome outcome = StandingRules.apply("Beer", Arrays.asList(B),
                EVERYONE,
                Arrays.asList(new StandingRules.Rule(B, "beer", StandingRules.Kind.EXCLUDE)));

        assertEquals("the user's own choice stands rather than nobody paying",
                Arrays.asList(B), outcome.members());
        assertFalse(outcome.changedAnything());
    }

    @Test
    public void anEmptyKeywordMatchesNothing() {
        StandingRules.Outcome outcome = StandingRules.apply("Beer", EVERYONE, EVERYONE,
                Arrays.asList(new StandingRules.Rule(B, "   ", StandingRules.Kind.EXCLUDE)));
        assertEquals(EVERYONE, outcome.members());
    }
}
