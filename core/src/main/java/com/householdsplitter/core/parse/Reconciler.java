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
        //
        // Which subtotal, though. SPEC 8.9.3 says the printed one, and on a grocery order
        // page that is always there. A small restaurant bill frequently prints no subtotal
        // at all: four dishes, a tax line and a total, and nothing in between. Taking the
        // printed subtotal literally there means computing zero plus the tax and comparing
        // it to the total, which fails on every such bill and fails loudest on the ones
        // that were read perfectly.
        //
        // So the rows stand in when nothing was printed. That is not a weaker check, it is
        // the same check with the only available left-hand side: if the rows are right the
        // arithmetic still has to come out, and if a row was missed it still will not.
        long base = adjustments.statedSubtotalCents() == 0L
                ? itemsSubtotalCents
                : adjustments.statedSubtotalCents();
        long computed = base + adjustments.feesTipAndTax() - adjustments.discountCents();
        return new Reconciliation(
                itemsSubtotalCents,
                adjustments.statedSubtotalCents(),
                computed,
                adjustments.statedTotalCents());
    }
}
