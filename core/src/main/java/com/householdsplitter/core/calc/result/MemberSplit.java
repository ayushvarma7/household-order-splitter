package com.householdsplitter.core.calc.result;

import com.householdsplitter.core.calc.AdjustmentType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** What one member owes, broken out the way SPEC 7.10.2 displays it. */
public final class MemberSplit {

    private final long memberId;
    private final String memberName;
    private final long commonShareCents;
    private final List<ItemShare> itemShares;
    private final Map<AdjustmentType, Long> adjustmentShares;
    private final long preTaxCents;
    private final long finalCents;

    public MemberSplit(long memberId, String memberName, long commonShareCents,
                       List<ItemShare> itemShares, Map<AdjustmentType, Long> adjustmentShares,
                       long preTaxCents, long finalCents) {
        this.memberId = memberId;
        this.memberName = memberName;
        this.commonShareCents = commonShareCents;
        this.itemShares = Collections.unmodifiableList(itemShares);
        this.adjustmentShares = Collections.unmodifiableMap(new EnumMap<>(adjustmentShares));
        this.preTaxCents = preTaxCents;
        this.finalCents = finalCents;
    }

    public long memberId() {
        return memberId;
    }

    public String memberName() {
        return memberName;
    }

    /** Their slice of the common bucket (SPEC 2.8). */
    public long commonShareCents() {
        return commonShareCents;
    }

    /** Their SUBSET and PERSONAL slices, in item order. */
    public List<ItemShare> itemShares() {
        return itemShares;
    }

    public long itemsTotalCents() {
        long sum = 0L;
        for (ItemShare share : itemShares) {
            sum += share.cents();
        }
        return sum;
    }

    public Map<AdjustmentType, Long> adjustmentShares() {
        return adjustmentShares;
    }

    public long adjustmentShare(AdjustmentType type) {
        Long value = adjustmentShares.get(type);
        return value == null ? 0L : value;
    }

    /** Tax and fees combined, as the share text prints them (SPEC 10.2). */
    public long adjustmentsTotalCents() {
        long sum = 0L;
        for (Long value : adjustmentShares.values()) {
            sum += value;
        }
        return sum;
    }

    /** SPEC 2.9. */
    public long preTaxCents() {
        return preTaxCents;
    }

    /** SPEC 2.11. */
    public long finalCents() {
        return finalCents;
    }
}
