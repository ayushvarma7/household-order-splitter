package com.householdsplitter.core.calc.input;

import com.householdsplitter.core.calc.AllocationMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Everything {@code SplitCalculator} needs about one order. SPEC 6.4. */
public final class CalcOrder {

    private final List<CalcMember> participants;
    private final List<CalcLineItem> items;
    private final Adjustments adjustments;
    private final long statedTotalCents;
    private final AllocationMode allocationMode;

    public CalcOrder(List<CalcMember> participants, List<CalcLineItem> items,
                     Adjustments adjustments, long statedTotalCents,
                     AllocationMode allocationMode) {
        // SPEC 6.4.5: participants are ordered by Member.sortOrder, and the ordering has
        // to be stable because the largest remainder tie-break hands the spare cent to the
        // earliest index.
        List<CalcMember> ordered = new ArrayList<>(participants == null
                ? Collections.emptyList() : participants);
        ordered.sort(Comparator.comparingInt(CalcMember::sortOrder).thenComparingLong(CalcMember::id));
        this.participants = Collections.unmodifiableList(ordered);
        this.items = Collections.unmodifiableList(new ArrayList<>(items == null
                ? Collections.emptyList() : items));
        this.adjustments = adjustments == null ? Adjustments.none() : adjustments;
        this.statedTotalCents = statedTotalCents;
        this.allocationMode = allocationMode == null ? AllocationMode.PROPORTIONAL : allocationMode;
    }

    public List<CalcMember> participants() {
        return participants;
    }

    public List<CalcLineItem> items() {
        return items;
    }

    public Adjustments adjustments() {
        return adjustments;
    }

    public long statedTotalCents() {
        return statedTotalCents;
    }

    public AllocationMode allocationMode() {
        return allocationMode;
    }
}
