package com.householdsplitter.core.calc.result;

/** One member's slice of one non-common item. SPEC 7.10.2 renders these line by line. */
public final class ItemShare {

    private final long lineItemId;
    private final String itemName;
    private final long cents;
    private final int shares;
    private final int wayCount;

    public ItemShare(long lineItemId, String itemName, long cents, int shares, int wayCount) {
        this.lineItemId = lineItemId;
        this.itemName = itemName;
        this.cents = cents;
        this.shares = shares;
        this.wayCount = wayCount;
    }

    public long lineItemId() {
        return lineItemId;
    }

    public String itemName() {
        return itemName;
    }

    public long cents() {
        return cents;
    }

    /** SPEC 7.9.6: 2 when the member took a "x2" share. */
    public int shares() {
        return shares;
    }

    /** How many members the item was divided between. */
    public int wayCount() {
        return wayCount;
    }
}
