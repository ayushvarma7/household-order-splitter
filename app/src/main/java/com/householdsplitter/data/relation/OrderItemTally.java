package com.householdsplitter.data.relation;

/**
 * What one order's rows add up to, for the home list.
 *
 * <p>Home shows the total printed on the bill. Whether that total is the one the rows
 * actually reach is a different question, and it is the one worth answering on a list:
 * an order whose figures disagree is the order you want to open. Answering it needs the
 * rows, and a hundred orders must not mean a hundred queries, so this is a single grouped
 * pass that the list observes alongside the orders themselves.
 */
public class OrderItemTally {

    public long orderId;

    /** The rows that charge somebody, or are still waiting to. Excluded rows are left out. */
    public long chargeableCents;

    /** How many rows there are at all, so an order with none is not called short. */
    public int itemCount;

    /** Rows nobody has answered for yet. */
    public int unansweredCount;
}
