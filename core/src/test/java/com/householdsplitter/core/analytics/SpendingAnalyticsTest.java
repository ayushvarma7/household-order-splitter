package com.householdsplitter.core.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.calc.AllocationMode;
import com.householdsplitter.core.calc.SplitCalculator;
import com.householdsplitter.core.calc.input.Adjustments;
import com.householdsplitter.core.calc.input.CalcLineItem;
import com.householdsplitter.core.calc.input.CalcMember;
import com.householdsplitter.core.calc.input.CalcOrder;
import com.householdsplitter.core.calc.result.SplitResult;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/** Member names here are test fixtures and appear nowhere in the app (SPEC 1.6). */
public class SpendingAnalyticsTest {

    private static final long A = 1L;
    private static final long B = 2L;

    private static SplitResult order(long sharedCents, long alphaCents, long betaCents,
                                     long taxCents) {
        List<CalcMember> members = Arrays.asList(
                new CalcMember(A, "Alpha", 0), new CalcMember(B, "Beta", 1));
        List<CalcLineItem> items = new ArrayList<>();
        items.add(CalcLineItem.common(1L, "Shared", sharedCents));
        if (alphaCents > 0) {
            items.add(CalcLineItem.personal(2L, "Coffee", alphaCents, A));
        }
        if (betaCents > 0) {
            items.add(CalcLineItem.personal(3L, "Tea", betaCents, B));
        }
        long stated = sharedCents + alphaCents + betaCents + taxCents;
        return SplitCalculator.calculate(new CalcOrder(members, items,
                Adjustments.taxOnly(taxCents), stated, AllocationMode.PROPORTIONAL));
    }

    private static long at(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day);
        return calendar.getTimeInMillis();
    }

    @Test
    public void emptyHouseholdReportsNothing() {
        SpendingAnalytics.Report report = SpendingAnalytics.analyse(new ArrayList<>());
        assertTrue(report.isEmpty());
        assertEquals(0, report.orderCount());
        assertEquals(0L, report.totalSpentCents());
    }

    @Test
    public void totalsAreTheSumOfWhatPeopleWereAskedToPay() {
        List<SpendingAnalytics.OrderPoint> orders = Arrays.asList(
                new SpendingAnalytics.OrderPoint(at(2026, 7, 4), "Jul 04", order(1000L, 500L, 300L, 100L)),
                new SpendingAnalytics.OrderPoint(at(2026, 8, 9), "Aug 09", order(2000L, 100L, 900L, 200L)));

        SpendingAnalytics.Report report = SpendingAnalytics.analyse(orders);
        assertEquals(2, report.orderCount());
        assertEquals(1900L + 3200L, report.totalSpentCents());

        long fromMembers = 0L;
        for (SpendingAnalytics.MemberTotal member : report.members()) {
            fromMembers += member.totalCents();
        }
        assertEquals("the analytics can never disagree with the split",
                report.totalSpentCents(), fromMembers);
    }

    @Test
    public void averageAndLargestOrder() {
        List<SpendingAnalytics.OrderPoint> orders = Arrays.asList(
                new SpendingAnalytics.OrderPoint(at(2026, 7, 4), "Small", order(1000L, 0L, 0L, 0L)),
                new SpendingAnalytics.OrderPoint(at(2026, 8, 9), "Big", order(5000L, 0L, 0L, 0L)));

        SpendingAnalytics.Report report = SpendingAnalytics.analyse(orders);
        assertEquals(3000L, report.averageOrderCents());
        assertEquals(5000L, report.largestOrderCents());
        assertEquals("Big", report.largestOrderLabel());
    }

    @Test
    public void membersAreRankedAndSharesAreExactPermille() {
        List<SpendingAnalytics.OrderPoint> orders = Arrays.asList(
                new SpendingAnalytics.OrderPoint(at(2026, 7, 4), "Jul", order(0L, 7500L, 2500L, 0L)));

        SpendingAnalytics.Report report = SpendingAnalytics.analyse(orders);
        assertEquals("Alpha", report.members().get(0).name());
        assertEquals(7500L, report.members().get(0).totalCents());
        assertEquals(750, report.members().get(0).sharePermille());
        assertEquals(250, report.members().get(1).sharePermille());
    }

    @Test
    public void monthsAreGroupedOldestFirst() {
        List<SpendingAnalytics.OrderPoint> orders = Arrays.asList(
                new SpendingAnalytics.OrderPoint(at(2026, 8, 20), "Aug b", order(1000L, 0L, 0L, 0L)),
                new SpendingAnalytics.OrderPoint(at(2026, 7, 4), "Jul", order(2000L, 0L, 0L, 0L)),
                new SpendingAnalytics.OrderPoint(at(2026, 8, 2), "Aug a", order(3000L, 0L, 0L, 0L)));

        SpendingAnalytics.Report report = SpendingAnalytics.analyse(orders);
        assertEquals(2, report.months().size());
        assertEquals("2026-07", report.months().get(0).month());
        assertEquals(2000L, report.months().get(0).totalCents());
        assertEquals("2026-08", report.months().get(1).month());
        assertEquals(4000L, report.months().get(1).totalCents());
        assertEquals(2, report.months().get(1).orderCount());
    }

    /** Something bought once is not a habit; twice or more is worth surfacing. */
    @Test
    public void frequentItemsNeedMoreThanOneAppearance() {
        List<SpendingAnalytics.OrderPoint> orders = Arrays.asList(
                new SpendingAnalytics.OrderPoint(at(2026, 7, 4), "Jul", order(0L, 500L, 300L, 0L)),
                new SpendingAnalytics.OrderPoint(at(2026, 8, 4), "Aug", order(0L, 500L, 0L, 0L)));

        SpendingAnalytics.Report report = SpendingAnalytics.analyse(orders);
        assertTrue(report.frequentItems().contains("coffee"));
        assertTrue("bought once, so not a habit", !report.frequentItems().contains("tea"));
    }

    @Test
    public void sharedAndPersonalSpendAreSeparated() {
        List<SpendingAnalytics.OrderPoint> orders = Arrays.asList(
                new SpendingAnalytics.OrderPoint(at(2026, 7, 4), "Jul", order(1000L, 400L, 600L, 0L)));

        SpendingAnalytics.Report report = SpendingAnalytics.analyse(orders);
        assertEquals(1000L, report.commonSpendCents());
        assertEquals(1000L, report.personalSpendCents());
    }
}
