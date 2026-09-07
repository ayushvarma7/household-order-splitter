package com.householdsplitter.ui.home;

import com.householdsplitter.data.relation.OrderWithMembers;

import java.time.YearMonth;

/**
 * One row of the home list: either a month heading or an order.
 *
 * <p>A flat newest-first list is fine at ten orders and useless at a hundred, where the
 * question is always "which month was that?". The headings answer it without the user having
 * to read dates.
 *
 * <p>A header carries the facts, not the words. Turning them into text needs the string
 * resources and the currency format, which belong to the view, so the adapter does it.
 */
public final class HomeRow {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_ORDER = 1;

    /** Which wording a heading should use. */
    public enum Heading {
        /** The month the device is in now. */
        THIS_MONTH,
        LAST_MONTH,
        /** A month earlier in the current year, where the year adds nothing. */
        MONTH,
        /** A month in an earlier year, where the year is the whole point. */
        MONTH_WITH_YEAR
    }

    public final int type;

    // Header fields.
    public final Heading heading;
    public final YearMonth month;
    public final int orderCount;
    public final long totalCents;

    // Order-row field.
    public final OrderWithMembers order;

    private HomeRow(int type, Heading heading, YearMonth month, int orderCount,
                    long totalCents, OrderWithMembers order) {
        this.type = type;
        this.heading = heading;
        this.month = month;
        this.orderCount = orderCount;
        this.totalCents = totalCents;
        this.order = order;
    }

    public static HomeRow header(Heading heading, YearMonth month, int orderCount, long totalCents) {
        return new HomeRow(TYPE_HEADER, heading, month, orderCount, totalCents, null);
    }

    public static HomeRow of(OrderWithMembers order) {
        return new HomeRow(TYPE_ORDER, null, null, 0, 0L, order);
    }

    /** Stable enough for DiffUtil: a month appears once, an order has an id. */
    public String key() {
        return type == TYPE_HEADER ? "h:" + month : "o:" + order.order.id;
    }
}
