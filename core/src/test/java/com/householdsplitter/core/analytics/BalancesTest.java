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
import java.util.List;

/** Member names here are test fixtures and appear nowhere in the app (SPEC 1.6). */
public class BalancesTest {

    private static final long A = 1L;
    private static final long B = 2L;
    private static final long C = 3L;

    private static SplitResult sharedOrder(long cents, int memberCount) {
        List<CalcMember> members = new ArrayList<>();
        String[] names = {"Alpha", "Beta", "Gamma"};
        for (int i = 0; i < memberCount; i++) {
            members.add(new CalcMember(i + 1L, names[i], i));
        }
        return SplitCalculator.calculate(new CalcOrder(members,
                Arrays.asList(CalcLineItem.common(1L, "Shared", cents)),
                Adjustments.none(), cents, AllocationMode.PROPORTIONAL));
    }

    @Test
    public void nobodyOwesAnybodyWhenThereAreNoOrders() {
        Balances.Report report = Balances.of(new ArrayList<>());
        assertTrue(report.isLevel());
        assertEquals(0L, report.outstandingCents());
    }

    /** One person fronts a shared shop, so the others owe them their share. */
    @Test
    public void oneOrderLeavesThePayerOutOfPocket() {
        Balances.Report report = Balances.of(Arrays.asList(
                new Balances.Settlement(A, sharedOrder(3000L, 3))));

        assertEquals(2000L, report.balances().get(0).netCents());
        assertEquals("Alpha", report.balances().get(0).name());
        assertEquals(2, report.transfers().size());
        assertEquals(2000L, report.outstandingCents());
        for (Balances.Transfer transfer : report.transfers()) {
            assertEquals("Alpha", transfer.to().name());
            assertEquals(1000L, transfer.amountCents());
        }
    }

    /** Taking turns to pay cancels out, which is the whole point of tracking it. */
    @Test
    public void takingTurnsEvensOut() {
        Balances.Report report = Balances.of(Arrays.asList(
                new Balances.Settlement(A, sharedOrder(3000L, 3)),
                new Balances.Settlement(B, sharedOrder(3000L, 3)),
                new Balances.Settlement(C, sharedOrder(3000L, 3))));

        assertTrue("three equal shops paid in turn need no payments", report.isLevel());
        for (Balances.Balance balance : report.balances()) {
            assertEquals(0L, balance.netCents());
        }
    }

    /** Balances must always sum to zero, or somebody is being wrongly asked for money. */
    @Test
    public void balancesAlwaysSumToZero() {
        Balances.Report report = Balances.of(Arrays.asList(
                new Balances.Settlement(A, sharedOrder(1234L, 3)),
                new Balances.Settlement(B, sharedOrder(5677L, 3)),
                new Balances.Settlement(A, sharedOrder(999L, 2))));

        long sum = 0L;
        for (Balances.Balance balance : report.balances()) {
            sum += balance.netCents();
        }
        assertEquals(0L, sum);
    }

    /** The suggested payments must exactly clear every balance. */
    @Test
    public void theSuggestedPaymentsSettleEveryone() {
        Balances.Report report = Balances.of(Arrays.asList(
                new Balances.Settlement(A, sharedOrder(5000L, 3)),
                new Balances.Settlement(B, sharedOrder(1700L, 3))));

        java.util.Map<Long, Long> after = new java.util.LinkedHashMap<>();
        for (Balances.Balance balance : report.balances()) {
            after.put(balance.memberId(), balance.netCents());
        }
        for (Balances.Transfer transfer : report.transfers()) {
            after.put(transfer.from().memberId(),
                    after.get(transfer.from().memberId()) + transfer.amountCents());
            after.put(transfer.to().memberId(),
                    after.get(transfer.to().memberId()) - transfer.amountCents());
        }
        for (Long remaining : after.values()) {
            assertEquals("everyone must end up level", Long.valueOf(0L), remaining);
        }
    }

    /** At most one payment fewer than there are people. */
    @Test
    public void theListOfPaymentsIsShort() {
        Balances.Report report = Balances.of(Arrays.asList(
                new Balances.Settlement(A, sharedOrder(900L, 3)),
                new Balances.Settlement(A, sharedOrder(600L, 3)),
                new Balances.Settlement(B, sharedOrder(300L, 3))));
        assertTrue(report.transfers().size() <= report.balances().size() - 1);
    }

    /**
     * An order with no payer says nothing about who is out of pocket. Counting what people
     * owed on it without counting what anyone paid would break the sum-to-zero invariant,
     * so it is left out entirely rather than counted by halves.
     */
    @Test
    public void anOrderWithNoPayerIsLeftOut() {
        Balances.Report report = Balances.of(Arrays.asList(
                new Balances.Settlement(A, sharedOrder(3000L, 3)),
                new Balances.Settlement(null, sharedOrder(9999L, 3))));

        assertEquals(2000L, report.balances().get(0).netCents());
        assertEquals(2000L, report.outstandingCents());
    }
}
