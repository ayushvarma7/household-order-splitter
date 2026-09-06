package com.householdsplitter.core.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.calc.input.Adjustments;
import com.householdsplitter.core.calc.input.CalcLineItem;
import com.householdsplitter.core.calc.input.CalcMember;
import com.householdsplitter.core.calc.input.CalcOrder;
import com.householdsplitter.core.calc.result.MemberSplit;
import com.householdsplitter.core.calc.result.SplitResult;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SPEC 12.2, the acceptance test, reproducing one real order.
 *
 * <p>The four members are fixtures built inside this test and named P1 to P4. They exist
 * nowhere else in the repository: no seed, no resource, no layout default (SPEC 1.6,
 * SPEC 12.2).
 */
public class SplitCalculatorAcceptanceTest {

    private static final long P1 = 1L;
    private static final long P2 = 2L;
    private static final long P3 = 3L;
    private static final long P4 = 4L;

    private static List<CalcMember> fourParticipants() {
        return Arrays.asList(
                new CalcMember(P1, "P1", 0),
                new CalcMember(P2, "P2", 1),
                new CalcMember(P3, "P3", 2),
                new CalcMember(P4, "P4", 3));
    }

    private static CalcOrder acceptanceOrder() {
        List<CalcLineItem> items = new ArrayList<>();
        long id = 100L;

        // Common bucket: 33.55 across four heads, so 8.3875 each before rounding.
        for (long cents : new long[]{1794L, 564L, 196L, 117L, 684L}) {
            items.add(CalcLineItem.common(id++, "common-" + id, cents));
        }
        for (long cents : new long[]{762L, 646L}) {
            items.add(CalcLineItem.personal(id++, "p1-" + id, cents, P1));
        }
        for (long cents : new long[]{286L, 744L, 367L, 3374L}) {
            items.add(CalcLineItem.personal(id++, "p2-" + id, cents, P2));
        }
        for (long cents : new long[]{2868L, 322L}) {
            items.add(CalcLineItem.personal(id++, "p3-" + id, cents, P3));
        }
        items.add(CalcLineItem.personal(id++, "p4-" + id, 794L, P4));

        items.add(CalcLineItem.subset(id++, "three-way", 272L, P1, P2, P3));
        items.add(CalcLineItem.subset(id++, "two-way-a", 298L, P1, P4));
        items.add(CalcLineItem.subset(id++, "two-way-b", 300L, P1, P4));
        items.add(CalcLineItem.subset(id++, "two-way-c", 83L, P1, P4));

        return new CalcOrder(fourParticipants(), items, Adjustments.taxOnly(182L),
                14653L, AllocationMode.PROPORTIONAL);
    }

    /** The headline assertion of SPEC 12.2. */
    @Test
    public void fourMemberTotalsSumToTheBillExactly() {
        SplitResult result = SplitCalculator.calculate(acceptanceOrder());

        long sum = 0L;
        for (MemberSplit member : result.members()) {
            sum += member.finalCents();
        }
        assertEquals("per-member finals must sum to the printed bill", 14653L, sum);
        assertEquals(14653L, result.computedTotalCents());
        assertEquals("no delta against the stated total", 0L, result.deltaCents());
        assertTrue(result.matchesStatedTotal());
    }

    @Test
    public void commonBucketAndPerHead() {
        SplitResult result = SplitCalculator.calculate(acceptanceOrder());
        assertEquals(3355L, result.commonBucketCents());
        assertEquals(4, result.participantCount());

        // 33.55 over four heads: three at 8.39 and one at 8.38, summing exactly.
        long commonSum = 0L;
        for (MemberSplit member : result.members()) {
            commonSum += member.commonShareCents();
        }
        assertEquals(3355L, commonSum);
        assertEquals(839L, result.member(P1).commonShareCents());
        assertEquals(839L, result.member(P2).commonShareCents());
        assertEquals(839L, result.member(P3).commonShareCents());
        assertEquals(838L, result.member(P4).commonShareCents());
    }

    @Test
    public void preTaxTotalsMatchTheSpreadsheet() {
        SplitResult result = SplitCalculator.calculate(acceptanceOrder());
        assertEquals(14471L, result.preTaxTotalCents());

        // SPEC 12.2 quotes 26.78 / 57.00 / 41.19 / 19.73 from a float based sheet and
        // SPEC 6.5 says an individual may land a cent away. The sum may not.
        assertWithinOneCent(2678L, result.member(P1).preTaxCents());
        assertWithinOneCent(5700L, result.member(P2).preTaxCents());
        assertWithinOneCent(4119L, result.member(P3).preTaxCents());
        assertWithinOneCent(1973L, result.member(P4).preTaxCents());
    }

    @Test
    public void finalTotalsMatchTheSpreadsheetWithinOneCent() {
        SplitResult result = SplitCalculator.calculate(acceptanceOrder());
        assertWithinOneCent(2712L, result.member(P1).finalCents());
        assertWithinOneCent(5772L, result.member(P2).finalCents());
        assertWithinOneCent(4171L, result.member(P3).finalCents());
        assertWithinOneCent(1998L, result.member(P4).finalCents());
    }

    @Test
    public void taxIsFullyDistributed() {
        SplitResult result = SplitCalculator.calculate(acceptanceOrder());
        assertEquals(182L, result.adjustmentTotal(AdjustmentType.TAX));
        assertEquals(0L, result.adjustmentTotal(AdjustmentType.TIP));
        assertEquals(0L, result.adjustmentTotal(AdjustmentType.DELIVERY_FEE));
        assertEquals(0L, result.adjustmentTotal(AdjustmentType.OTHER_FEE));
    }

    /** SPEC 6.4.13, checked from outside as well as inside. */
    @Test
    public void postconditionHolds() {
        SplitResult result = SplitCalculator.calculate(acceptanceOrder());
        assertEquals(result.preTaxTotalCents() + result.adjustmentsTotalCents(),
                result.computedTotalCents());
    }

    @Test
    public void resultIsDeterministic() {
        SplitResult first = SplitCalculator.calculate(acceptanceOrder());
        for (int run = 0; run < 200; run++) {
            SplitResult again = SplitCalculator.calculate(acceptanceOrder());
            for (int i = 0; i < first.members().size(); i++) {
                assertEquals(first.members().get(i).finalCents(),
                        again.members().get(i).finalCents());
            }
        }
    }

    private static void assertWithinOneCent(long expected, long actual) {
        long difference = Math.abs(expected - actual);
        assertTrue("expected " + expected + " within a cent, got " + actual, difference <= 1L);
    }
}
