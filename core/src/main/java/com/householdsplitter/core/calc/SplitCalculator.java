package com.householdsplitter.core.calc;

import com.householdsplitter.core.calc.input.Adjustments;
import com.householdsplitter.core.calc.input.CalcAssignment;
import com.householdsplitter.core.calc.input.CalcLineItem;
import com.householdsplitter.core.calc.input.CalcMember;
import com.householdsplitter.core.calc.input.CalcOrder;
import com.householdsplitter.core.calc.result.ItemShare;
import com.householdsplitter.core.calc.result.MemberSplit;
import com.householdsplitter.core.calc.result.SplitResult;
import com.householdsplitter.core.money.MoneySplitter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns one order into what each member owes.
 *
 * <p>SPEC 6.4. The thirteen steps below run in the order the spec gives, and the comments
 * carry the step numbers, because the ordering is load bearing: each allocation's spare
 * cents depend on the totals produced by the step before it.
 *
 * <p>Plain Java, no {@code android.*}, no {@code float}, no {@code double} (SPEC 6.6).
 */
public final class SplitCalculator {

    private SplitCalculator() {
    }

    public static SplitResult calculate(CalcOrder order) {
        // 6.4.1
        final List<CalcMember> participants = order.participants();
        if (participants.isEmpty()) {
            throw new NoParticipantsException();
        }
        final int participantCount = participants.size();

        final Map<Long, CalcMember> byId = new LinkedHashMap<>();
        final Map<Long, Integer> indexById = new LinkedHashMap<>();
        for (int i = 0; i < participantCount; i++) {
            CalcMember member = participants.get(i);
            byId.put(member.id(), member);
            indexById.put(member.id(), i);
        }

        // 6.4.2
        final List<CalcLineItem> chargeable = new ArrayList<>();
        final List<Long> unansweredIds = new ArrayList<>();
        final List<String> unansweredNames = new ArrayList<>();
        for (CalcLineItem item : order.items()) {
            if (item.scope() == Scope.EXCLUDED) {
                continue;
            }
            boolean needsAssignees = item.scope() == Scope.SUBSET || item.scope() == Scope.PERSONAL;
            if (item.scope().isUnanswered() || (needsAssignees && item.assignments().isEmpty())) {
                // SPEC 7.10.6: collect them all rather than failing on the first, so the
                // blocking panel can list every offender at once.
                unansweredIds.add(item.id());
                unansweredNames.add(item.name());
                continue;
            }
            chargeable.add(item);
        }
        if (!unansweredIds.isEmpty()) {
            throw new UnassignedItemsException(unansweredIds, unansweredNames);
        }

        // 6.4.3
        long commonBucket = 0L;
        long itemSubtotal = 0L;
        for (CalcLineItem item : chargeable) {
            itemSubtotal = Math.addExact(itemSubtotal, item.lineTotalCents());
            if (item.scope() == Scope.COMMON) {
                commonBucket = Math.addExact(commonBucket, item.lineTotalCents());
            }
        }

        // 6.4.4. The discount is always order-level in this version, so it always comes
        // off the common bucket. The bucket is allowed to go negative (SPEC 11.4).
        final Adjustments adjustments = order.adjustments();
        commonBucket = Math.subtractExact(commonBucket, adjustments.discountCents());

        // 6.4.5
        final long[] commonShares = MoneySplitter.split(commonBucket, MoneySplitter.ones(participantCount));

        // 6.4.6
        final long[] itemTotals = new long[participantCount];
        final Map<Long, List<ItemShare>> sharesByMember = new LinkedHashMap<>();
        for (CalcMember member : participants) {
            sharesByMember.put(member.id(), new ArrayList<>());
        }

        for (CalcLineItem item : chargeable) {
            if (item.scope() != Scope.SUBSET && item.scope() != Scope.PERSONAL) {
                continue;
            }
            // Deterministic ordering: the same member order as the participant list, so
            // the largest remainder tie-break in SPEC 6.3.6 is reproducible.
            List<CalcAssignment> assignments = new ArrayList<>(item.assignments());
            assignments.sort(Comparator.comparingInt(
                    (CalcAssignment a) -> requireIndex(indexById, a.memberId(), item))
                    .thenComparingLong(CalcAssignment::memberId));

            int[] weights = new int[assignments.size()];
            for (int i = 0; i < assignments.size(); i++) {
                weights[i] = assignments.get(i).shares();
            }
            long[] parts = MoneySplitter.split(item.lineTotalCents(), weights);

            for (int i = 0; i < assignments.size(); i++) {
                CalcAssignment assignment = assignments.get(i);
                int index = requireIndex(indexById, assignment.memberId(), item);
                itemTotals[index] = Math.addExact(itemTotals[index], parts[i]);
                sharesByMember.get(assignment.memberId()).add(new ItemShare(
                        item.id(), item.name(), parts[i], assignment.shares(), assignments.size()));
            }
        }

        // 6.4.7
        final long[] preTax = new long[participantCount];
        for (int i = 0; i < participantCount; i++) {
            preTax[i] = Math.addExact(commonShares[i], itemTotals[i]);
        }

        // 6.4.8
        long preTaxTotal = 0L;
        for (long value : preTax) {
            preTaxTotal = Math.addExact(preTaxTotal, value);
        }

        // 6.4.9. Proportional weights are the pre-tax totals themselves. Two situations
        // make that impossible: a zero pre-tax total, which the spec names, and a member
        // whose pre-tax total went negative because a discount exceeded the common bucket
        // (SPEC 11.4), which it does not. Both fall back to an equal split and flag it.
        boolean equalFallbackUsed = false;
        boolean proportionalPossible = order.allocationMode() == AllocationMode.PROPORTIONAL
                && preTaxTotal > 0L && allNonNegative(preTax);
        if (order.allocationMode() == AllocationMode.PROPORTIONAL && !proportionalPossible) {
            equalFallbackUsed = true;
        }

        final List<Map<AdjustmentType, Long>> perMemberAdjustments = new ArrayList<>(participantCount);
        for (int i = 0; i < participantCount; i++) {
            perMemberAdjustments.add(new EnumMap<>(AdjustmentType.class));
        }

        for (AdjustmentType type : AdjustmentType.values()) {
            long amount = adjustments.valueOf(type);
            if (amount == 0L) {
                // Skipped, but everyone still carries an explicit zero so the breakdown
                // in SPEC 7.10.2 has a row to render.
                for (int i = 0; i < participantCount; i++) {
                    perMemberAdjustments.get(i).put(type, 0L);
                }
                continue;
            }
            long[] shares = proportionalPossible
                    ? MoneySplitter.split(amount, preTax)
                    : MoneySplitter.split(amount, MoneySplitter.ones(participantCount));
            for (int i = 0; i < participantCount; i++) {
                perMemberAdjustments.get(i).put(type, shares[i]);
            }
        }

        // 6.4.10 and 6.4.11
        final List<MemberSplit> memberSplits = new ArrayList<>(participantCount);
        long computedTotal = 0L;
        for (int i = 0; i < participantCount; i++) {
            CalcMember member = participants.get(i);
            long finalCents = preTax[i];
            for (Long share : perMemberAdjustments.get(i).values()) {
                finalCents = Math.addExact(finalCents, share);
            }
            computedTotal = Math.addExact(computedTotal, finalCents);
            memberSplits.add(new MemberSplit(
                    member.id(),
                    member.name(),
                    commonShares[i],
                    sharesByMember.get(member.id()),
                    perMemberAdjustments.get(i),
                    preTax[i],
                    finalCents));
        }

        // 6.4.13, asserted in code rather than trusted.
        long expected = Math.addExact(preTaxTotal, adjustments.total());
        if (computedTotal != expected) {
            throw new IllegalStateException("calculation postcondition violated: members sum to "
                    + computedTotal + ", expected " + expected);
        }

        // 6.4.12: the delta is reported, never corrected.
        return new SplitResult(
                memberSplits,
                commonBucket,
                participantCount,
                itemSubtotal,
                preTaxTotal,
                adjustments.total(),
                computedTotal,
                order.statedTotalCents(),
                equalFallbackUsed);
    }

    /**
     * SPEC 7.8.5 guarantees that dropping a participant drops their assignments, so an
     * assignment naming a non-participant means the repository broke that invariant.
     * Failing loudly here beats quietly charging nobody.
     */
    private static int requireIndex(Map<Long, Integer> indexById, long memberId, CalcLineItem item) {
        Integer index = indexById.get(memberId);
        if (index == null) {
            throw new IllegalArgumentException("item '" + item.name() + "' is assigned to member "
                    + memberId + ", who is not a participant of this order (SPEC 7.8.5)");
        }
        return index;
    }

    private static boolean allNonNegative(long[] values) {
        for (long value : values) {
            if (value < 0L) {
                return false;
            }
        }
        return true;
    }
}
