package com.householdsplitter.data.mapper;

import com.householdsplitter.core.parse.Reconciler;
import com.householdsplitter.core.parse.model.ParsedAdjustments;
import com.householdsplitter.core.parse.model.Reconciliation;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.relation.OrderItemTally;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An order's figures checked against each other, from what is stored.
 *
 * <p>One place builds this so that every screen showing an order's total, or a sum of them,
 * agrees: the home list and its month headings, the groups list, and a group's own screen.
 */
public final class OrderTotals {

    private OrderTotals() {
    }

    /** @param chargeableCents what the order's rows add up to, excluded rows left out */
    public static Reconciliation reconcile(Order order, long chargeableCents) {
        return Reconciler.reconcile(chargeableCents, new ParsedAdjustments(
                order.statedSubtotalCents,
                order.taxCents,
                order.deliveryFeeCents,
                order.tipCents,
                order.otherFeeCents,
                order.discountCents,
                order.statedTotalCents));
    }

    /** What the order costs, as {@link Reconciliation#effectiveTotalCents()} defines it. */
    public static long effectiveTotalCents(Order order, OrderItemTally tally) {
        return reconcile(order, tally == null ? 0L : tally.chargeableCents).effectiveTotalCents();
    }

    /** Tallies keyed by order, for looking up one order's rows in a list. */
    public static Map<Long, OrderItemTally> byOrder(List<OrderItemTally> tallies) {
        Map<Long, OrderItemTally> out = new HashMap<>();
        if (tallies != null) {
            for (OrderItemTally tally : tallies) {
                out.put(tally.orderId, tally);
            }
        }
        return out;
    }
}
