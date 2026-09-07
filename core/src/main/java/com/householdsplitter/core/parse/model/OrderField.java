package com.householdsplitter.core.parse.model;

/**
 * The order-level fields of SPEC 8.6.3. Tracking which ones actually came off a
 * screenshot is what lets SPEC 7.7.2 put a "from screenshot" marker beside them.
 */
public enum OrderField {
    ORDER_DATE,
    EXTERNAL_ORDER_NO,
    SUBTOTAL,
    TAX,
    DELIVERY_FEE,
    TIP,
    OTHER_FEE,
    DISCOUNT,
    TOTAL
}
