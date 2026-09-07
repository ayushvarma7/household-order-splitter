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

    /** A payment that has actually been made stops the screen asking for it again. */
    @Test
    public void aPaymentClearsWhatItSettles() {
        List<Balances.Settlement> orders = Arrays.asList(
                new Balances.Settlement(A, sharedOrder(3000L, 3)));

        Balances.Report before = Balances.of(orders);
        assertEquals(2, before.transfers().size());
        assertEquals(2000L, before.outstandingCents());

        // Ben hands Ana his thousand.
        Balances.Report after = Balances.of(orders,
                Arrays.asList(new Balances.Payment(B, A, 1000L)));

        assertEquals(1, after.transfers().size());
        assertEquals(1000L, after.outstandingCents());
        assertEquals("Gamma", after.transfers().get(0).from().name());
        assertEquals(0L, after.balances().get(2).netCents());
    }

    /** Paying everything owed leaves the household level. */
    @Test
    public void payingEverythingLeavesNothingOutstanding() {
        List<Balances.Settlement> orders = Arrays.asList(
                new Balances.Settlement(A, sharedOrder(3000L, 3)));

        Balances.Report after = Balances.of(orders, Arrays.asList(
                new Balances.Payment(B, A, 1000L),
                new Balances.Payment(C, A, 1000L)));

        assertTrue(after.isLevel());
        assertEquals(0L, after.outstandingCents());
        for (Balances.Balance balance : after.balances()) {
            assertEquals(0L, balance.netCents());
        }
    }

    /** Payments still have to sum to zero across the household. */
    @Test
    public void paymentsPreserveTheSumToZeroInvariant() {
        Balances.Report report = Balances.of(
                Arrays.asList(new Balances.Settlement(A, sharedOrder(4444L, 3))),
                Arrays.asList(new Balances.Payment(B, A, 700L),
                        new Balances.Payment(C, B, 250L)));

        long sum = 0L;
        for (Balances.Balance balance : report.balances()) {
            sum += balance.netCents();
        }
        assertEquals(0L, sum);
    }

    /** Overpaying flips the balance rather than being clamped, because that is the truth. */
    @Test
    public void overpayingFlipsTheBalance() {
        Balances.Report report = Balances.of(
                Arrays.asList(new Balances.Settlement(A, sharedOrder(3000L, 3))),
                Arrays.asList(new Balances.Payment(B, A, 1500L)));

        long ben = 0L;
        for (Balances.Balance balance : report.balances()) {
            if ("Beta".equals(balance.name())) {
                ben = balance.netCents();
            }
        }
        assertEquals("Beta paid 500 more than owed, so the household owes it back", 500L, ben);
    }

    /** A payment involving somebody with no orders is ignored, not silently absorbed. */
    @Test
    public void aPaymentToAStrangerIsIgnored() {
        Balances.Report report = Balances.of(
                Arrays.asList(new Balances.Settlement(A, sharedOrder(3000L, 3))),
                Arrays.asList(new Balances.Payment(B, 99L, 1000L)));

        assertEquals(2000L, report.outstandingCents());
    }

    /** A payment recorded to oneself would corrupt nothing, and is ignored anyway. */
    @Test
    public void aPaymentToOneselfIsIgnored() {
        Balances.Report report = Balances.of(
                Arrays.asList(new Balances.Settlement(A, sharedOrder(3000L, 3))),
                Arrays.asList(new Balances.Payment(B, B, 1000L)));

        assertEquals(2000L, report.outstandingCents());
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
