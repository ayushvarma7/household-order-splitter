package com.householdsplitter.core.browse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.parse.Reconciler;
import com.householdsplitter.core.parse.model.ParsedAdjustments;
import com.householdsplitter.core.parse.model.Reconciliation;

import org.junit.Test;

/** The mark on the home list means what the details screen means. */
public class OrderFlagTest {

    /** subtotal, tax, total; the fees a grocery order carries are not what is at issue. */
    private static Reconciliation check(long itemsCents, long statedSubtotal, long tax,
                                        long statedTotal) {
        return Reconciler.reconcile(itemsCents, new ParsedAdjustments(
                statedSubtotal, tax, 0L, 0L, 0L, 0L, statedTotal));
    }

    @Test
    public void anOrderThatAddsUpIsNotMarked() {
        OrderFlag flag = OrderFlag.of(check(9_000L, 9_000L, 500L, 9_500L), 6);
        assertFalse(flag.isFlagged());
        assertEquals(OrderFlag.Kind.NONE, flag.kind());
    }

    /** The common case: a row was missed, and every printed figure still adds up. */
    @Test
    public void rowsShortOfThePrintedSubtotalAreMarked() {
        OrderFlag flag = OrderFlag.of(check(8_692L, 9_000L, 500L, 9_500L), 6);
        assertTrue(flag.isFlagged());
        assertEquals(OrderFlag.Kind.ROWS_SHORT, flag.kind());
        assertEquals(-308L, flag.deltaCents());
    }

    @Test
    public void arithmeticThatMissesThePrintedTotalIsMarked() {
        OrderFlag flag = OrderFlag.of(check(9_000L, 9_000L, 500L, 9_800L), 6);
        assertTrue(flag.isFlagged());
        assertEquals(OrderFlag.Kind.TOTAL_OFF, flag.kind());
        assertEquals(-300L, flag.deltaCents());
    }

    /** A total that was never read is a gap in what the app knows, not a disagreement. */
    @Test
    public void nothingPrintedIsNotADisagreement() {
        assertFalse(OrderFlag.of(check(8_692L, 0L, 500L, 0L), 6).isFlagged());
    }

    /** A bill with no printed subtotal reconciles against its own rows, as restaurants need. */
    @Test
    public void aRestaurantBillWithNoSubtotalLineStillReconciles() {
        assertFalse(OrderFlag.of(check(9_000L, 0L, 500L, 9_500L), 4).isFlagged());
        assertTrue(OrderFlag.of(check(8_000L, 0L, 500L, 9_500L), 4).isFlagged());
    }

    /** An imported bill nobody has read yet is not an order whose rows are short. */
    @Test
    public void anOrderWithNoRowsYetIsNotMarked() {
        assertFalse(OrderFlag.of(check(0L, 9_000L, 500L, 9_500L), 0).isFlagged());
    }

    @Test
    public void aMissingReconciliationIsNotMarked() {
        assertFalse(OrderFlag.of(null, 6).isFlagged());
    }
}
