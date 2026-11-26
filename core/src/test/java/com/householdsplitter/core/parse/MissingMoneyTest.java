package com.householdsplitter.core.parse;

import static org.junit.Assert.assertEquals;

import com.householdsplitter.core.analytics.SpendingAnalytics;
import com.householdsplitter.core.calc.AllocationMode;
import com.householdsplitter.core.calc.SplitCalculator;
import com.householdsplitter.core.calc.input.Adjustments;
import com.householdsplitter.core.calc.input.CalcLineItem;
import com.householdsplitter.core.calc.input.CalcMember;
import com.householdsplitter.core.calc.input.CalcOrder;
import com.householdsplitter.core.calc.result.SplitResult;
import com.householdsplitter.core.parse.model.ParsedAdjustments;
import com.householdsplitter.core.parse.model.Reconciliation;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** The money a bill charged that no row accounts for. */
public class MissingMoneyTest {

    private static Reconciliation of(long items, long subtotal, long total) {
        return Reconciler.reconcile(items,
                new ParsedAdjustments(subtotal, 0L, 0L, 0L, 0L, 0L, total));
    }

    @Test
    public void rowsShortOfThePrintedSubtotalAreTheMissingMoney() {
        assertEquals(2_500L, of(9_000L, 11_500L, 11_500L).missingCents());
    }

    @Test
    public void thePrintedTotalIsUsedWhenNoSubtotalWasRead() {
        assertEquals(1_200L, of(9_000L, 0L, 10_200L).missingCents());
    }

    @Test
    public void rowsThatAddUpOrOvershootAreNotMissingAnything() {
        assertEquals(0L, of(9_000L, 9_000L, 9_000L).missingCents());
        // A row too many is a different problem and is not reported as missing money.
        assertEquals(0L, of(9_500L, 9_000L, 9_000L).missingCents());
    }

    @Test
    public void nothingPrintedMeansNothingToCompareWith() {
        assertEquals(0L, of(9_000L, 0L, 0L).missingCents());
    }

    @Test
    public void theSpendingReportKeepsMissingMoneyApartFromWhatWasRead() {
        SplitResult result = SplitCalculator.calculate(new CalcOrder(
                Arrays.asList(new CalcMember(1L, "Alpha", 0)),
                Collections.singletonList(CalcLineItem.common(1L, "Rice", 1_000L)),
                Adjustments.none(), 1_000L, AllocationMode.PROPORTIONAL));
        SpendingAnalytics.Report report = SpendingAnalytics.analyse(Arrays.asList(
                new SpendingAnalytics.OrderPoint(1_000L, "Small", result, 7L, 300L),
                new SpendingAnalytics.OrderPoint(2_000L, "Big", result, 8L, 900L),
                new SpendingAnalytics.OrderPoint(3_000L, "Clean", result, 9L, 0L)));

        assertEquals(1_200L, report.missingCents());
        assertEquals(3_000L, report.totalSpentCents());
        assertEquals(4_200L, report.totalWithMissingCents());
        assertEquals(2, report.missingOrders().size());
        assertEquals("the biggest gap comes first", 8L, report.missingOrders().get(0).orderId());
    }

    @Test
    public void aNegativeGapIsNeverCounted() {
        SplitResult result = SplitCalculator.calculate(new CalcOrder(
                Arrays.asList(new CalcMember(1L, "Alpha", 0)),
                Collections.singletonList(CalcLineItem.common(1L, "Rice", 1_000L)),
                Adjustments.none(), 1_000L, AllocationMode.PROPORTIONAL));

        SpendingAnalytics.Report report = SpendingAnalytics.analyse(Collections.singletonList(
                new SpendingAnalytics.OrderPoint(1_000L, "Odd", result, 1L, -500L)));

        assertEquals(0L, report.missingCents());
    }
}
