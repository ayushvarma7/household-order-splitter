package com.householdsplitter.core.browse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Which month an order belongs to, for grouping a list of them.
 *
 * <p>A flat newest-first list is fine at ten orders and useless at a hundred, where the
 * question is always "which month was that?". This answers it from the order's own date,
 * in the device's own time zone: a late-evening order must not be filed under the next month
 * because UTC has already rolled over.
 */
public final class MonthBucket {

    private MonthBucket() {
    }

    /**
     * A sortable, comparable key for the month a timestamp falls in, such as 202609.
     * Two orders in the same month share a key, whatever day they fell on.
     */
    public static int keyOf(long epochMillis, ZoneId zone) {
        LocalDate date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate();
        return date.getYear() * 100 + date.getMonthValue();
    }

    /** The {@link YearMonth} a timestamp falls in, for formatting a heading. */
    public static YearMonth of(long epochMillis, ZoneId zone) {
        return YearMonth.from(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate());
    }

    /**
     * True when the two timestamps are in the same month of the same year. Used to decide
     * whether a heading like "This month" applies rather than a month name.
     */
    public static boolean sameMonth(long a, long b, ZoneId zone) {
        return keyOf(a, zone) == keyOf(b, zone);
    }

    /** How many whole months {@code earlier} is before {@code later}, or 0 if it is not. */
    public static int monthsBetween(long earlier, long later, ZoneId zone) {
        YearMonth from = of(earlier, zone);
        YearMonth to = of(later, zone);
        int months = (to.getYear() - from.getYear()) * 12 + (to.getMonthValue()
                - from.getMonthValue());
        return Math.max(0, months);
    }
}
