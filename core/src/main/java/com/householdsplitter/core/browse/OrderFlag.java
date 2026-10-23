package com.householdsplitter.core.browse;

import com.householdsplitter.core.parse.model.Reconciliation;

/**
 * Whether one order's figures disagree with each other, for the list on Home.
 *
 * <p>The details screen already says this, loudly, once an order is open. Saying it only
 * there means the user has to open every order to find out which one is wrong, which on a
 * month of shopping is the whole list. So the same verdict is computed here and shown as a
 * mark beside the row.
 *
 * <p>Deliberately the same verdict, from the same {@link Reconciliation}, rather than a
 * second rule that happens to usually agree. A list that marks an order and a screen that
 * then says everything is fine is worse than no mark, and two separate definitions of
 * "off" is how that happens.
 */
public final class OrderFlag {

    public enum Kind {
        /** The figures agree, or there is nothing yet to disagree. */
        NONE,
        /**
         * The arithmetic does not reach the printed total.
         *
         * <p>Something is missing or mistyped among the items, the tax, the fees or the
         * tip, and the bill itself says so.
         */
        TOTAL_OFF,
        /**
         * The arithmetic reaches the printed total, but the rows do not reach the printed
         * subtotal.
         *
         * <p>This is the quieter and more common failure: a row the reader missed, where
         * every stated figure still adds up because the subtotal it was given was printed
         * rather than counted.
         */
        ROWS_SHORT
    }

    public static final OrderFlag NONE = new OrderFlag(Kind.NONE, 0L);

    private final Kind kind;
    private final long deltaCents;

    private OrderFlag(Kind kind, long deltaCents) {
        this.kind = kind;
        this.deltaCents = deltaCents;
    }

    /**
     * @param check     the order's reconciliation, computed exactly as the details screen does
     * @param itemCount how many rows the order has at all
     */
    public static OrderFlag of(Reconciliation check, int itemCount) {
        // An order with no rows yet is not an order whose rows are short. A bill that has
        // been imported but not yet read would otherwise be marked wrong for the one
        // reason the user already knows about and is on their way to fixing.
        if (check == null || itemCount <= 0) {
            return NONE;
        }
        if (!check.totalMatches()) {
            return new OrderFlag(Kind.TOTAL_OFF, check.totalDeltaCents());
        }
        if (!check.subtotalMatches()) {
            return new OrderFlag(Kind.ROWS_SHORT, check.subtotalDeltaCents());
        }
        return NONE;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isFlagged() {
        return kind != Kind.NONE;
    }

    /** Signed: negative when the order is short of what the bill says. */
    public long deltaCents() {
        return deltaCents;
    }
}
