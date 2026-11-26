package com.householdsplitter.core.insight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.calc.AllocationMode;
import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.calc.input.Adjustments;
import com.householdsplitter.core.calc.input.CalcAssignment;
import com.householdsplitter.core.calc.input.CalcLineItem;
import com.householdsplitter.core.calc.input.CalcMember;
import com.householdsplitter.core.calc.input.CalcOrder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Member names here are test fixtures and appear nowhere in the app (SPEC 1.6). */
public class PurchaseTest {

    private static final List<CalcMember> THREE = Arrays.asList(
            new CalcMember(1L, "Alpha", 0), new CalcMember(2L, "Beta", 1),
            new CalcMember(3L, "Gamma", 2));

    private static CalcOrder order(List<CalcMember> members, CalcLineItem... items) {
        return new CalcOrder(members, Arrays.asList(items), Adjustments.none(), 0L,
                AllocationMode.PROPORTIONAL);
    }

    @Test
    public void aSharedRowIsSplitSoThePartsAddUpToThePrice() {
        List<Purchase> rows = Purchase.fromOrder(0L,
                order(THREE, CalcLineItem.common(1L, "Rice", 100L)));

        assertEquals(1, rows.size());
        long sum = 0L;
        for (long share : rows.get(0).centsByMember().values()) {
            sum += share;
        }
        assertEquals(100L, sum);
        assertEquals(3, rows.get(0).centsByMember().size());
    }

    @Test
    public void aPersonalRowBelongsToOnePerson() {
        List<Purchase> rows = Purchase.fromOrder(0L,
                order(THREE, CalcLineItem.personal(1L, "Coffee", 899L, 2L)));

        assertEquals(Collections.singletonMap(2L, 899L), rows.get(0).centsByMember());
    }

    @Test
    public void sharesInASubsetFollowTheirWeights() {
        CalcLineItem item = new CalcLineItem(1L, "Pizza", 900L, Scope.SUBSET,
                Arrays.asList(new CalcAssignment(1L, 2), new CalcAssignment(3L, 1)));

        List<Purchase> rows = Purchase.fromOrder(0L, order(THREE, item));

        assertEquals(Long.valueOf(600L), rows.get(0).centsByMember().get(1L));
        assertEquals(Long.valueOf(300L), rows.get(0).centsByMember().get(3L));
    }

    @Test
    public void anOrderWithAnUnansweredRowContributesNothing() {
        List<Purchase> rows = Purchase.fromOrder(0L, order(THREE,
                CalcLineItem.common(1L, "Rice", 100L),
                new CalcLineItem(2L, "Mystery", 500L, Scope.UNASSIGNED, new ArrayList<>())));

        assertTrue(rows.isEmpty());
    }

    @Test
    public void excludedRowsAndCreditsAreLeftOut() {
        List<Purchase> rows = Purchase.fromOrder(0L, order(THREE,
                CalcLineItem.common(1L, "Rice", 100L),
                CalcLineItem.excluded(2L, "Gift", 500L),
                CalcLineItem.common(3L, "Coupon", -50L)));

        assertEquals(1, rows.size());
        assertEquals("Rice", rows.get(0).name());
    }

    @Test
    public void anOrderWithNobodyOnItContributesNothing() {
        assertTrue(Purchase.fromOrder(0L, order(Collections.emptyList(),
                CalcLineItem.common(1L, "Rice", 100L))).isEmpty());
    }
}
