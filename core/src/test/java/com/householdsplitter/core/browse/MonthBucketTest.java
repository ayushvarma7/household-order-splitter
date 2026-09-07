package com.householdsplitter.core.browse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

public class MonthBucketTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private static long at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute)
                .atZone(NEW_YORK).toInstant().toEpochMilli();
    }

    @Test
    public void theKeyIsTheYearAndMonth() {
        assertEquals(202609, MonthBucket.keyOf(at(2026, 9, 7, 13, 0), NEW_YORK));
        assertEquals(202601, MonthBucket.keyOf(at(2026, 1, 31, 13, 0), NEW_YORK));
    }

    @Test
    public void theKeySortsChronologically() {
        assertTrue(MonthBucket.keyOf(at(2026, 9, 1, 12, 0), NEW_YORK)
                > MonthBucket.keyOf(at(2026, 8, 31, 12, 0), NEW_YORK));
        assertTrue(MonthBucket.keyOf(at(2027, 1, 1, 12, 0), NEW_YORK)
                > MonthBucket.keyOf(at(2026, 12, 31, 12, 0), NEW_YORK));
    }

    /**
     * The reason the zone is a parameter. A 9pm order on the last day of the month is still
     * that month's order, even though UTC has already rolled over.
     */
    @Test
    public void aLateEveningOrderStaysInItsOwnMonth() {
        long lateSeptember = at(2026, 9, 30, 21, 30);

        assertEquals(202609, MonthBucket.keyOf(lateSeptember, NEW_YORK));
        assertEquals("this is the bug being guarded against",
                202610, MonthBucket.keyOf(lateSeptember, ZoneId.of("UTC")));
    }

    @Test
    public void sameMonthIgnoresTheDay() {
        assertTrue(MonthBucket.sameMonth(
                at(2026, 9, 1, 0, 30), at(2026, 9, 30, 23, 30), NEW_YORK));
        assertFalse(MonthBucket.sameMonth(
                at(2026, 9, 30, 23, 30), at(2026, 10, 1, 0, 30), NEW_YORK));
    }

    @Test
    public void monthsBetweenCountsWholeMonths() {
        assertEquals(1, MonthBucket.monthsBetween(
                at(2026, 8, 31, 12, 0), at(2026, 9, 1, 12, 0), NEW_YORK));
        assertEquals(0, MonthBucket.monthsBetween(
                at(2026, 9, 1, 12, 0), at(2026, 9, 30, 12, 0), NEW_YORK));
    }

    /** Across a year boundary, which a naive month subtraction gets wrong. */
    @Test
    public void monthsBetweenCrossesTheNewYear() {
        assertEquals(2, MonthBucket.monthsBetween(
                at(2026, 12, 15, 12, 0), at(2027, 2, 1, 12, 0), NEW_YORK));
        assertEquals(12, MonthBucket.monthsBetween(
                at(2026, 3, 1, 12, 0), at(2027, 3, 1, 12, 0), NEW_YORK));
    }

    /** A future date is not a negative number of months ago. */
    @Test
    public void aFutureDateIsClampedToZero() {
        assertEquals(0, MonthBucket.monthsBetween(
                at(2027, 1, 1, 12, 0), at(2026, 1, 1, 12, 0), NEW_YORK));
    }

    @Test
    public void ofReturnsTheYearMonth() {
        assertEquals(YearMonth.of(2026, 9), MonthBucket.of(at(2026, 9, 7, 13, 0), NEW_YORK));
    }
}
