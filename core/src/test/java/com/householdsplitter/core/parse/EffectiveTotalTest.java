package com.householdsplitter.core.parse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.parse.model.ParsedAdjustments;
import com.householdsplitter.core.parse.model.Reconciliation;

import org.junit.Test;

/** An order whose total was never read still costs what its figures add up to. */
public class EffectiveTotalTest {

    private static Reconciliation of(long items, long subtotal, long tax, long tip,
                                     long discount, long total) {
        return Reconciler.reconcile(items,
                new ParsedAdjustments(subtotal, tax, 0L, tip, 0L, discount, total));
    }

    @Test
    public void aPrintedTotalIsTakenAsItStands() {
        Reconciliation check = of(9_000L, 9_000L, 500L, 0L, 0L, 9_400L);
        assertEquals("even when the arithmetic disagrees", 9_400L, check.effectiveTotalCents());
        assertTrue(check.totalIsStated());
    }

    /** The Oct 15 case: no total screenshot, the subtotal typed in by hand. */
    @Test
    public void aTypedSubtotalWithNoTotalGivesTheSubtotalPlusCharges() {
        Reconciliation check = of(13_772L, 13_772L, 0L, 0L, 0L, 0L);
        assertEquals(13_772L, check.effectiveTotalCents());
        assertFalse(check.totalIsStated());

        Reconciliation taxed = of(13_772L, 13_772L, 125L, 300L, 100L, 0L);
        assertEquals(13_772L + 125L + 300L - 100L, taxed.effectiveTotalCents());
    }

    @Test
    public void withNoPrintedFiguresAtAllTheRowsStandIn() {
        assertEquals(4_000L + 200L, of(4_000L, 0L, 200L, 0L, 0L, 0L).effectiveTotalCents());
    }

    /** Leaving the total unset must not make the reconciliation start disagreeing. */
    @Test
    public void anUnsetTotalStillCountsAsNothingToCompareAgainst() {
        Reconciliation check = of(13_772L, 13_772L, 0L, 0L, 0L, 0L);
        assertTrue(check.totalMatches());
        assertFalse(check.allMatched());
    }

    @Test
    public void anOrderWithNothingInItCostsNothing() {
        assertEquals(0L, of(0L, 0L, 0L, 0L, 0L, 0L).effectiveTotalCents());
    }
}
