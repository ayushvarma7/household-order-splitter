package com.householdsplitter.core.parse.model;

/**
 * The advisory money checks of SPEC 8.9. Advisory means advisory: nothing here blocks the
 * user (SPEC 7.7.4, SPEC 8.9.4), and none of it is ever checked against the
 * "N items delivered" count (SPEC 8.5.4, SPEC 8.9.5).
 */
public final class Reconciliation {

    private final long itemsSubtotalCents;
    private final long statedSubtotalCents;
    private final long computedTotalCents;
    private final long statedTotalCents;

    public Reconciliation(long itemsSubtotalCents, long statedSubtotalCents,
                          long computedTotalCents, long statedTotalCents) {
        this.itemsSubtotalCents = itemsSubtotalCents;
        this.statedSubtotalCents = statedSubtotalCents;
        this.computedTotalCents = computedTotalCents;
        this.statedTotalCents = statedTotalCents;
    }

    /** SPEC 8.9.1. */
    public long itemsSubtotalCents() {
        return itemsSubtotalCents;
    }

    public long statedSubtotalCents() {
        return statedSubtotalCents;
    }

    /** statedSubtotal + tax + fees + tip - discount (SPEC 8.9.3). */
    public long computedTotalCents() {
        return computedTotalCents;
    }

    public long statedTotalCents() {
        return statedTotalCents;
    }

    /** SPEC 8.9.2. */
    public long subtotalDeltaCents() {
        return itemsSubtotalCents - statedSubtotalCents;
    }

    /** SPEC 8.9.3. */
    public long totalDeltaCents() {
        return computedTotalCents - statedTotalCents;
    }

    /**
     * True when the rows agree with the printed subtotal, or when none was printed.
     *
     * <p>Nothing printed is not a mismatch. A bill that states no subtotal is making no
     * claim for the rows to disagree with, and reporting "items add to $84.20 but the bill
     * says $0.00" on a perfectly read bill is a warning about the reader's own gap in
     * knowledge dressed up as a warning about the user's money.
     */
    public boolean subtotalMatches() {
        return statedSubtotalCents == 0L || subtotalDeltaCents() == 0L;
    }

    /**
     * True when the arithmetic reaches the printed total, or when none was printed.
     *
     * <p>Same reasoning as {@link #subtotalMatches()}. A till receipt whose total line the
     * recogniser could not read is a gap in what this app knows, and reporting it as
     * "off by $84.20" states a disagreement with a figure that was never obtained.
     */
    public boolean totalMatches() {
        return statedTotalCents == 0L || totalDeltaCents() == 0L;
    }

    /**
     * What the bill charged for beyond what the rows add up to, or 0.
     *
     * <p>The printed subtotal is the better yardstick, because it is the rows' own total
     * before tax and fees; the printed total is used when only that was read. Rows adding up
     * to more than the bill is a different problem (a row too many) and is not "missing", so
     * it is not reported here. This is the amount behind the "Missing $X" flag on the home
     * list, which is the rows-short case of the same checks.
     */
    public long missingCents() {
        if (statedSubtotalCents != 0L) {
            return Math.max(0L, -subtotalDeltaCents());
        }
        if (statedTotalCents != 0L) {
            return Math.max(0L, -totalDeltaCents());
        }
        return 0L;
    }

    /** True only when there is something to compare against and it agrees. */
    public boolean allMatched() {
        return statedTotalCents != 0L && totalMatches() && subtotalMatches();
    }

    /**
     * What the order costs: the printed total when one was read or typed, otherwise what
     * the subtotal, tax and fees come to.
     *
     * <p>A zero printed total means the figure was never obtained, not that the order was
     * free, so showing it as $0.00 states something false. An order imported without its
     * last screenshot, with the subtotal typed in by hand, is the common way to get here.
     * The stored total is left at zero all the same, so the checks above still treat it as
     * a figure nobody printed rather than one the arithmetic can be checked against.
     */
    public long effectiveTotalCents() {
        return statedTotalCents != 0L ? statedTotalCents : computedTotalCents;
    }

    /** True when {@link #effectiveTotalCents()} is a printed figure rather than a sum. */
    public boolean totalIsStated() {
        return statedTotalCents != 0L;
    }
}
