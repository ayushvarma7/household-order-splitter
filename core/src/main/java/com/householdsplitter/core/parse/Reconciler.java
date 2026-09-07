package com.householdsplitter.core.parse;

import com.householdsplitter.core.parse.model.ParsedAdjustments;
import com.householdsplitter.core.parse.model.ParsedOrder;
import com.householdsplitter.core.parse.model.Reconciliation;

/** SPEC 8.9. Pure arithmetic over cents. */
public final class Reconciler {

    private Reconciler() {
    }

    public static Reconciliation reconcile(ParsedOrder order) {
        return reconcile(order.itemsSubtotalCents(), order.adjustments());
    }

    public static Reconciliation reconcile(long itemsSubtotalCents, ParsedAdjustments adjustments) {
        // SPEC 8.9.3: subtotal + tax + fees + tip - discount, against the printed total.
        long computed = adjustments.statedSubtotalCents()
                + adjustments.feesTipAndTax()
                - adjustments.discountCents();
        return new Reconciliation(
                itemsSubtotalCents,
                adjustments.statedSubtotalCents(),
                computed,
                adjustments.statedTotalCents());
    }
}
