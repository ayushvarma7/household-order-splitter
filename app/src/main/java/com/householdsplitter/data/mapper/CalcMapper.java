package com.householdsplitter.data.mapper;

import com.householdsplitter.core.calc.AllocationMode;
import com.householdsplitter.core.calc.input.Adjustments;
import com.householdsplitter.core.calc.input.CalcAssignment;
import com.householdsplitter.core.calc.input.CalcLineItem;
import com.householdsplitter.core.calc.input.CalcMember;
import com.householdsplitter.core.calc.input.CalcOrder;
import com.householdsplitter.core.export.ExportOrder;
import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.data.entity.ItemAssignment;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.relation.LineItemWithAssignments;
import com.householdsplitter.data.relation.OrderBundle;

import java.util.ArrayList;
import java.util.List;

/**
 * Room entities in, plain calculation POJOs out.
 *
 * <p>This is the seam that keeps SPEC 6.6 true: the calculator never sees a Room type, an
 * Android type, or a LiveData.
 */
public final class CalcMapper {

    private CalcMapper() {
    }

    public static CalcOrder toCalcOrder(OrderBundle bundle, AllocationMode mode) {
        List<CalcMember> participants = new ArrayList<>();
        for (Member member : bundle.participants) {
            participants.add(new CalcMember(member.id, member.name, member.sortOrder));
        }

        List<CalcLineItem> items = new ArrayList<>();
        for (LineItemWithAssignments row : bundle.items) {
            List<CalcAssignment> assignments = new ArrayList<>();
            for (ItemAssignment assignment : row.assignments) {
                assignments.add(new CalcAssignment(assignment.memberId, assignment.shares));
            }
            items.add(new CalcLineItem(row.item.id, row.item.name, row.item.lineTotalCents,
                    row.item.scope, assignments));
        }

        Adjustments adjustments = new Adjustments(
                bundle.order.taxCents,
                bundle.order.deliveryFeeCents,
                bundle.order.tipCents,
                bundle.order.otherFeeCents,
                bundle.order.discountCents);

        return new CalcOrder(participants, items, adjustments,
                bundle.order.statedTotalCents, mode);
    }

    /** The COMMON rows, which the CSV lists down its own column (SPEC 10.1). */
    public static List<ExportOrder.NamedAmount> commonItems(OrderBundle bundle) {
        List<ExportOrder.NamedAmount> common = new ArrayList<>();
        for (LineItemWithAssignments row : bundle.items) {
            if (row.item.scope == Scope.COMMON) {
                common.add(new ExportOrder.NamedAmount(row.item.name, row.item.lineTotalCents));
            }
        }
        return common;
    }

    public static String memberName(OrderBundle bundle, Long memberId) {
        if (memberId == null) {
            return null;
        }
        for (Member member : bundle.participants) {
            if (member.id == memberId) {
                return member.name;
            }
        }
        return null;
    }
}
