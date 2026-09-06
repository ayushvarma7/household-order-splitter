package com.householdsplitter.core.calc;

/**
 * The order-level amounts that are not line items (SPEC 2.10).
 *
 * <p>Declaration order is significant: SPEC 6.4.9 requires the adjustments to be
 * allocated in exactly this sequence, because each allocation's rounding depends on the
 * ones before it.
 */
public enum AdjustmentType {
    TAX("Tax"),
    DELIVERY_FEE("Delivery"),
    TIP("Tip"),
    OTHER_FEE("Fees");

    private final String label;

    AdjustmentType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
