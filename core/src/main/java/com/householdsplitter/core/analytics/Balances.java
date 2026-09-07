package com.householdsplitter.core.analytics;

import com.householdsplitter.core.calc.result.MemberSplit;
import com.householdsplitter.core.calc.result.SplitResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who is ahead and who is behind across every order, and the shortest way to level up.
 *
 * <p>An individual order says what each person owes for that shop. It does not say who is
 * out of pocket, because one person fronted the money. Over a run of orders those advances
 * accumulate, and the useful question stops being "what do I owe for this shop?" and becomes
 * "what do I owe, all in?".
 *
 * <p>A member's balance is what they paid out, less what they owed. Positive means the
 * household owes them. Every figure is {@code long} cents and the balances always sum to
 * zero, which is asserted, because money that appears or vanishes here would be money
 * somebody is wrongly asked for.
 */
public final class Balances {

    private Balances() {
    }

    /** One order's contribution: who fronted it, and what each person owed. */
    public static final class Settlement {

        private final Long payerMemberId;
        private final SplitResult result;

        public Settlement(Long payerMemberId, SplitResult result) {
            this.payerMemberId = payerMemberId;
            this.result = result;
        }

        public Long payerMemberId() {
            return payerMemberId;
        }

        public SplitResult result() {
            return result;
        }
    }

    /** Where one member stands. */
    public static final class Balance {

        private final long memberId;
        private final String name;
        private final long paidCents;
        private final long owedCents;

        Balance(long memberId, String name, long paidCents, long owedCents) {
            this.memberId = memberId;
            this.name = name;
            this.paidCents = paidCents;
            this.owedCents = owedCents;
        }

        public long memberId() {
            return memberId;
        }

        public String name() {
            return name;
        }

        /** What they fronted, across every order they paid for. */
        public long paidCents() {
            return paidCents;
        }

        /** What their share came to, across every order they were in. */
        public long owedCents() {
            return owedCents;
        }

        /** Positive means the household owes them; negative means they owe it. */
        public long netCents() {
            return paidCents - owedCents;
        }

        public boolean isSettled() {
            return netCents() == 0L;
        }
    }

    /** One payment that would move the household closer to level. */
    public static final class Transfer {

        private final Balance from;
        private final Balance to;
        private final long amountCents;

        Transfer(Balance from, Balance to, long amountCents) {
            this.from = from;
            this.to = to;
            this.amountCents = amountCents;
        }

        public Balance from() {
            return from;
        }

        public Balance to() {
            return to;
        }

        public long amountCents() {
            return amountCents;
        }
    }

    public static final class Report {

        private final List<Balance> balances;
        private final List<Transfer> transfers;

        Report(List<Balance> balances, List<Transfer> transfers) {
            this.balances = balances;
            this.transfers = transfers;
        }

        /** Ordered by how far from level they are, furthest first. */
        public List<Balance> balances() {
            return balances;
        }

        /** The payments that would settle everyone up. Empty when nobody owes anybody. */
        public List<Transfer> transfers() {
            return transfers;
        }

        public boolean isLevel() {
            return transfers.isEmpty();
        }

        /** How much money needs to move in total. */
        public long outstandingCents() {
            long total = 0L;
            for (Transfer transfer : transfers) {
                total += transfer.amountCents();
            }
            return total;
        }
    }

    public static Report of(List<Settlement> settlements) {
        Map<Long, long[]> totals = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();

        for (Settlement settlement : settlements) {
            if (settlement.result() == null) {
                continue;
            }
            for (MemberSplit member : settlement.result().members()) {
                long[] sums = totals.get(member.memberId());
                if (sums == null) {
                    sums = new long[]{0L, 0L};
                    totals.put(member.memberId(), sums);
                }
                names.put(member.memberId(), member.memberName());
                sums[1] += member.finalCents();
            }
            // An order with no payer recorded tells us nothing about who is out of pocket,
            // so it contributes what people owed and nothing to what anyone paid. That
            // would make the balances fail to sum to zero, so such orders are skipped
            // entirely rather than counted by halves.
            Long payer = settlement.payerMemberId();
            if (payer == null || !totals.containsKey(payer)) {
                for (MemberSplit member : settlement.result().members()) {
                    totals.get(member.memberId())[1] -= member.finalCents();
                }
                continue;
            }
            totals.get(payer)[0] += settlement.result().computedTotalCents();
        }

        List<Balance> balances = new ArrayList<>();
        for (Map.Entry<Long, long[]> entry : totals.entrySet()) {
            balances.add(new Balance(entry.getKey(), names.get(entry.getKey()),
                    entry.getValue()[0], entry.getValue()[1]));
        }

        long check = 0L;
        for (Balance balance : balances) {
            check += balance.netCents();
        }
        if (check != 0L) {
            throw new IllegalStateException(
                    "balances must sum to zero, they sum to " + check);
        }

        balances.sort(Comparator.comparingLong((Balance b) -> Math.abs(b.netCents())).reversed()
                .thenComparing(Balance::name));
        return new Report(balances, transfers(balances));
    }

    /**
     * The shortest sensible list of payments: repeatedly send the largest debt to the
     * largest credit. That settles everyone in at most one payment fewer than there are
     * people, which is the fewest a household will tolerate being told to make.
     */
    private static List<Transfer> transfers(List<Balance> balances) {
        List<Balance> creditors = new ArrayList<>();
        List<Balance> debtors = new ArrayList<>();
        Map<Long, Long> remaining = new LinkedHashMap<>();
        for (Balance balance : balances) {
            if (balance.netCents() > 0L) {
                creditors.add(balance);
            } else if (balance.netCents() < 0L) {
                debtors.add(balance);
            }
            remaining.put(balance.memberId(), balance.netCents());
        }
        creditors.sort(Comparator.comparingLong((Balance b) -> remaining.get(b.memberId())).reversed());
        debtors.sort(Comparator.comparingLong(b -> remaining.get(b.memberId())));

        List<Transfer> transfers = new ArrayList<>();
        int c = 0;
        int d = 0;
        while (c < creditors.size() && d < debtors.size()) {
            Balance creditor = creditors.get(c);
            Balance debtor = debtors.get(d);
            long credit = remaining.get(creditor.memberId());
            long debt = -remaining.get(debtor.memberId());
            long amount = Math.min(credit, debt);
            if (amount > 0L) {
                transfers.add(new Transfer(debtor, creditor, amount));
                remaining.put(creditor.memberId(), credit - amount);
                remaining.put(debtor.memberId(), -(debt - amount));
            }
            if (remaining.get(creditor.memberId()) == 0L) {
                c++;
            }
            if (remaining.get(debtor.memberId()) == 0L) {
                d++;
            }
        }
        return transfers;
    }
}
