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

    public boolean subtotalMatches() {
        return subtotalDeltaCents() == 0L;
    }

    public boolean totalMatches() {
        return totalDeltaCents() == 0L;
    }

    /** True only when there is something to compare against and it agrees. */
    public boolean allMatched() {
        return statedTotalCents != 0L && totalMatches() && subtotalMatches();
    }
}
