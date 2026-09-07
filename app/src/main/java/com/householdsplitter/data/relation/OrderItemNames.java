package com.householdsplitter.data.relation;

/**
 * Every item name in one order, run together, so a search can look inside orders.
 *
 * <p>"Which order had the coffee beans in it?" is the question a household actually asks
 * months later, and the order label ("Weekly shop") never answers it.
 */
public class OrderItemNames {

    public long orderId;

    /** Null when the order has no items yet. */
    public String names;
}
