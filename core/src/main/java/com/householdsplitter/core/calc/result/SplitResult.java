package com.householdsplitter.core.calc.result;

import com.householdsplitter.core.calc.AdjustmentType;

import java.util.Collections;
import java.util.List;

/**
 * The outcome of SPEC 6.4, carrying everything the summary screen, the CSV and the share
 * text need, plus the reconciliation delta that SPEC 6.4.12 forbids us to silently absorb.
 */
public final class SplitResult {

    private final List<MemberSplit> members;
    private final long commonBucketCents;
    private final int participantCount;
    private final long itemSubtotalCents;
    private final long preTaxTotalCents;
    private final long adjustmentsTotalCents;
    private final long computedTotalCents;
    private final long statedTotalCents;
    private final boolean equalFallbackUsed;

    public SplitResult(List<MemberSplit> members, long commonBucketCents, int participantCount,
                       long itemSubtotalCents, long preTaxTotalCents, long adjustmentsTotalCents,
                       long computedTotalCents, long statedTotalCents, boolean equalFallbackUsed) {
        this.members = Collections.unmodifiableList(members);
        this.commonBucketCents = commonBucketCents;
        this.participantCount = participantCount;
        this.itemSubtotalCents = itemSubtotalCents;
        this.preTaxTotalCents = preTaxTotalCents;
        this.adjustmentsTotalCents = adjustmentsTotalCents;
        this.computedTotalCents = computedTotalCents;
        this.statedTotalCents = statedTotalCents;
        this.equalFallbackUsed = equalFallbackUsed;
    }

    /** Ordered by {@code Member.sortOrder} (SPEC 6.4.5). */
    public List<MemberSplit> members() {
        return members;
    }

    public MemberSplit member(long memberId) {
        for (MemberSplit split : members) {
            if (split.memberId() == memberId) {
                return split;
            }
        }
        throw new IllegalArgumentException("member " + memberId + " has no allocation");
    }

    /** SPEC 2.7, after the discount of SPEC 6.4.4. */
    public long commonBucketCents() {
        return commonBucketCents;
    }

    public int participantCount() {
        return participantCount;
    }

    /** Every chargeable item, common and otherwise, before adjustments. */
    public long itemSubtotalCents() {
        return itemSubtotalCents;
    }

    public long preTaxTotalCents() {
        return preTaxTotalCents;
    }

    public long adjustmentsTotalCents() {
        return adjustmentsTotalCents;
    }

    /** SPEC 6.4.11. */
    public long computedTotalCents() {
        return computedTotalCents;
    }

    /** SPEC 2.12, as parsed from the screenshot. */
    public long statedTotalCents() {
        return statedTotalCents;
    }

    /** SPEC 6.4.12: positive when we computed more than the bill states. */
    public long deltaCents() {
        return computedTotalCents - statedTotalCents;
    }

    /** SPEC 8.9.4 and 7.10.3: the green tick case. */
    public boolean matchesStatedTotal() {
        return deltaCents() == 0L;
    }

    /** SPEC 6.4.9: true when a proportional allocation had to fall back to an equal one. */
    public boolean equalFallbackUsed() {
        return equalFallbackUsed;
    }

    public long adjustmentTotal(AdjustmentType type) {
        long sum = 0L;
        for (MemberSplit split : members) {
            sum += split.adjustmentShare(type);
        }
        return sum;
    }
}
