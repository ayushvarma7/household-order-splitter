package com.householdsplitter.core.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.householdsplitter.core.calc.input.Adjustments;
import com.householdsplitter.core.calc.input.CalcAssignment;
import com.householdsplitter.core.calc.input.CalcLineItem;
import com.householdsplitter.core.calc.input.CalcMember;
import com.householdsplitter.core.calc.input.CalcOrder;
import com.householdsplitter.core.calc.result.MemberSplit;
import com.householdsplitter.core.calc.result.SplitResult;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** SPEC 12.3. Members are test fixtures named A to D and appear nowhere else (SPEC 1.6). */
public class SplitCalculatorTest {

    private static final long A = 1L;
    private static final long B = 2L;
    private static final long C = 3L;

    private static List<CalcMember> members(int count) {
        List<CalcMember> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new CalcMember(i + 1L, "Member" + (i + 1), i));
        }
        return list;
    }

    /** 12.3.1: two participants only, common bucket halved. */
    @Test
    public void twoParticipantsHalveTheCommonBucket() {
        CalcOrder order = new CalcOrder(members(2),
                Arrays.asList(CalcLineItem.common(1L, "shared", 1000L)),
                Adjustments.none(), 1000L, AllocationMode.PROPORTIONAL);

        SplitResult result = SplitCalculator.calculate(order);
        assertEquals(1000L, result.commonBucketCents());
        assertEquals(500L, result.member(A).finalCents());
        assertEquals(500L, result.member(B).finalCents());
        assertEquals(1000L, result.computedTotalCents());
    }

    /** An odd bucket still sums exactly, one member simply carries the extra cent. */
    @Test
    public void oddCommonBucketStillSumsExactly() {
        CalcOrder order = new CalcOrder(members(3),
                Arrays.asList(CalcLineItem.common(1L, "shared", 1000L)),
                Adjustments.none(), 1000L, AllocationMode.PROPORTIONAL);

        SplitResult result = SplitCalculator.calculate(order);
        assertEquals(334L, result.member(A).finalCents());
        assertEquals(333L, result.member(B).finalCents());
        assertEquals(333L, result.member(C).finalCents());
        assertEquals(1000L, result.computedTotalCents());
    }

    /** 12.3.2: the equal allocation setting instead of proportional (SPEC 7.14.1). */
    @Test
    public void equalAllocationIgnoresPreTaxWeights() {
        List<CalcLineItem> items = Arrays.asList(
                CalcLineItem.personal(1L, "expensive", 9000L, A),
                CalcLineItem.personal(2L, "cheap", 1000L, B));

        CalcOrder proportional = new CalcOrder(members(2), items,
                Adjustments.taxOnly(100L), 10100L, AllocationMode.PROPORTIONAL);
        SplitResult byWeight = SplitCalculator.calculate(proportional);
        assertEquals(90L, byWeight.member(A).adjustmentShare(AdjustmentType.TAX));
        assertEquals(10L, byWeight.member(B).adjustmentShare(AdjustmentType.TAX));

        CalcOrder equal = new CalcOrder(members(2), items,
                Adjustments.taxOnly(100L), 10100L, AllocationMode.EQUAL);
        SplitResult evenly = SplitCalculator.calculate(equal);
        assertEquals(50L, evenly.member(A).adjustmentShare(AdjustmentType.TAX));
        assertEquals(50L, evenly.member(B).adjustmentShare(AdjustmentType.TAX));
        assertEquals(10100L, evenly.computedTotalCents());
    }

    /** 12.3.3: zero tax and zero fees must not divide by zero (SPEC 11.5). */
    @Test
    public void zeroAdjustments() {
        CalcOrder order = new CalcOrder(members(3),
                Arrays.asList(CalcLineItem.common(1L, "shared", 900L)),
                Adjustments.none(), 900L, AllocationMode.PROPORTIONAL);

        SplitResult result = SplitCalculator.calculate(order);
        assertEquals(0L, result.adjustmentsTotalCents());
        for (MemberSplit member : result.members()) {
            assertEquals(300L, member.finalCents());
            assertEquals(0L, member.adjustmentShare(AdjustmentType.TAX));
        }
        assertFalse(result.equalFallbackUsed());
    }

    /** 12.3.4 and SPEC 11.4: a discount larger than the common bucket. */
    @Test
    public void discountLargerThanCommonBucketGoesNegative() {
        CalcOrder order = new CalcOrder(members(2),
                Arrays.asList(CalcLineItem.common(1L, "shared", 500L)),
                new Adjustments(0L, 0L, 0L, 0L, 900L), -400L, AllocationMode.PROPORTIONAL);

        SplitResult result = SplitCalculator.calculate(order);
        assertEquals(-400L, result.commonBucketCents());
        assertEquals(-200L, result.member(A).finalCents());
        assertEquals(-200L, result.member(B).finalCents());
        assertEquals(-400L, result.computedTotalCents());
    }

    /**
     * A negative pre-tax total cannot be used as a proportional weight, so the allocation
     * falls back to equal and says so. SPEC 6.4.9 names the zero case; SPEC 11.4 creates
     * the negative one.
     */
    @Test
    public void negativePreTaxFallsBackToEqualAllocation() {
        CalcOrder order = new CalcOrder(members(2),
                Arrays.asList(CalcLineItem.common(1L, "shared", 100L)),
                new Adjustments(50L, 0L, 0L, 0L, 900L), -750L, AllocationMode.PROPORTIONAL);

        SplitResult result = SplitCalculator.calculate(order);
        assertTrue("the fallback must be flagged, not hidden", result.equalFallbackUsed());
        assertEquals(25L, result.member(A).adjustmentShare(AdjustmentType.TAX));
        assertEquals(25L, result.member(B).adjustmentShare(AdjustmentType.TAX));
        assertEquals(-750L, result.computedTotalCents());
    }

    @Test
    public void zeroPreTaxWithNonZeroTaxFallsBackToEqual() {
        CalcOrder order = new CalcOrder(members(2),
                Collections.<CalcLineItem>emptyList(),
                Adjustments.taxOnly(101L), 101L, AllocationMode.PROPORTIONAL);

        SplitResult result = SplitCalculator.calculate(order);
        assertTrue(result.equalFallbackUsed());
        assertEquals(101L, result.computedTotalCents());
        assertEquals(51L, result.member(A).finalCents());
        assertEquals(50L, result.member(B).finalCents());
    }

    /** 12.3.5: a weighted x2 share on one item (SPEC 7.9.6). */
    @Test
    public void doubledShareTakesTwiceAsMuch() {
        CalcLineItem item = new CalcLineItem(1L, "big pack", 300L, Scope.SUBSET,
                Arrays.asList(new CalcAssignment(A, 2), new CalcAssignment(B, 1)));

        CalcOrder order = new CalcOrder(members(2), Arrays.asList(item),
                Adjustments.none(), 300L, AllocationMode.PROPORTIONAL);

        SplitResult result = SplitCalculator.calculate(order);
        assertEquals(200L, result.member(A).finalCents());
        assertEquals(100L, result.member(B).finalCents());
        assertEquals(2, result.member(A).itemShares().get(0).shares());
        assertEquals(300L, result.computedTotalCents());
    }

    /** 12.3.6: an EXCLUDED item contributes nothing (SPEC 6.4.2). */
    @Test
    public void excludedItemContributesNothing() {
        CalcOrder order = new CalcOrder(members(2),
                Arrays.asList(
                        CalcLineItem.common(1L, "shared", 1000L),
                        CalcLineItem.excluded(2L, "refunded", 5000L)),
                Adjustments.none(), 1000L, AllocationMode.PROPORTIONAL);

        SplitResult result = SplitCalculator.calculate(order);
        assertEquals(1000L, result.itemSubtotalCents());
        assertEquals(1000L, result.computedTotalCents());
    }

    /** 12.3.7: an unassigned item raises the blocking condition of SPEC 7.10.6. */
    @Test
    public void unassignedItemBlocksTheSummary() {
        CalcOrder order = new CalcOrder(members(2),
                Arrays.asList(
                        CalcLineItem.common(1L, "shared", 1000L),
                        new CalcLineItem(2L, "who is this for", 400L, Scope.UNASSIGNED, null)),
                Adjustments.none(), 1400L, AllocationMode.PROPORTIONAL);

        try {
            SplitCalculator.calculate(order);
            fail("expected UnassignedItemsException");
        } catch (UnassignedItemsException blocked) {
            assertEquals(Collections.singletonList(2L), blocked.lineItemIds());
            assertEquals(Collections.singletonList("who is this for"), blocked.itemNames());
        }
    }

    /** A SUBSET item with nobody on it is unanswered too, not a free item. */
    @Test
    public void subsetWithoutAssignmentsBlocks() {
        CalcOrder order = new CalcOrder(members(2),
                Arrays.asList(new CalcLineItem(7L, "orphan", 400L, Scope.SUBSET, null)),
                Adjustments.none(), 400L, AllocationMode.PROPORTIONAL);
        try {
            SplitCalculator.calculate(order);
            fail("expected UnassignedItemsException");
        } catch (UnassignedItemsException blocked) {
            assertEquals(1, blocked.lineItemIds().size());
        }
    }

    /** SPEC 6.4.1. */
    @Test
    public void noParticipantsAborts() {
        CalcOrder order = new CalcOrder(Collections.<CalcMember>emptyList(),
                Arrays.asList(CalcLineItem.common(1L, "shared", 100L)),
                Adjustments.none(), 100L, AllocationMode.PROPORTIONAL);
        try {
            SplitCalculator.calculate(order);
            fail("expected NoParticipantsException");
        } catch (NoParticipantsException expected) {
            // SPEC 6.4.1
        }
    }

    /** SPEC 11.6: one participant takes the whole bucket. */
    @Test
    public void singleParticipantTakesEverything() {
        CalcOrder order = new CalcOrder(members(1),
                Arrays.asList(CalcLineItem.common(1L, "shared", 1337L)),
                Adjustments.taxOnly(63L), 1400L, AllocationMode.PROPORTIONAL);

        SplitResult result = SplitCalculator.calculate(order);
        assertEquals(1400L, result.member(A).finalCents());
        assertTrue(result.matchesStatedTotal());
    }

    /** SPEC 6.4.12: a mismatch is reported, never corrected. */
    @Test
    public void deltaAgainstStatedTotalIsReportedNotAbsorbed() {
        CalcOrder order = new CalcOrder(members(2),
                Arrays.asList(CalcLineItem.common(1L, "shared", 1000L)),
                Adjustments.none(), 1050L, AllocationMode.PROPORTIONAL);

        SplitResult result = SplitCalculator.calculate(order);
        assertEquals(1000L, result.computedTotalCents());
        assertEquals(-50L, result.deltaCents());
        assertFalse(result.matchesStatedTotal());
    }

    /** SPEC 7.8.5: an assignment naming a non-participant is a broken invariant. */
    @Test
    public void assignmentToNonParticipantIsRejected() {
        CalcOrder order = new CalcOrder(members(2),
                Arrays.asList(CalcLineItem.personal(1L, "stray", 100L, 99L)),
                Adjustments.none(), 100L, AllocationMode.PROPORTIONAL);
        try {
            SplitCalculator.calculate(order);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("not a participant"));
        }
    }

    /** Every adjustment type together, still exact. */
    @Test
    public void allFourAdjustmentsSumExactly() {
        CalcOrder order = new CalcOrder(members(3),
                Arrays.asList(
                        CalcLineItem.common(1L, "shared", 1000L),
                        CalcLineItem.personal(2L, "mine", 777L, B)),
                new Adjustments(123L, 499L, 250L, 77L, 0L), 2726L, AllocationMode.PROPORTIONAL);

        SplitResult result = SplitCalculator.calculate(order);
        assertEquals(123L, result.adjustmentTotal(AdjustmentType.TAX));
        assertEquals(499L, result.adjustmentTotal(AdjustmentType.DELIVERY_FEE));
        assertEquals(250L, result.adjustmentTotal(AdjustmentType.TIP));
        assertEquals(77L, result.adjustmentTotal(AdjustmentType.OTHER_FEE));
        assertEquals(2726L, result.computedTotalCents());
        assertTrue(result.matchesStatedTotal());
    }
}
