package com.householdsplitter.core.calc.input;

import com.householdsplitter.core.calc.AdjustmentType;

/**
 * The order-level amounts. SPEC 5.3, SPEC 2.10.
 *
 * <p>The discount is held here for completeness but is not an {@link AdjustmentType}: SPEC
 * 6.4.4 applies it against the common bucket before any allocation happens, not as a
 * spread-out share.
 */
public final class Adjustments {

    private final long taxCents;
    private final long deliveryFeeCents;
    private final long tipCents;
    private final long otherFeeCents;
    private final long discountCents;

    public Adjustments(long taxCents, long deliveryFeeCents, long tipCents,
                       long otherFeeCents, long discountCents) {
        if (discountCents < 0L) {
            throw new IllegalArgumentException("discount is stored positive (SPEC 5.3)");
        }
        this.taxCents = taxCents;
        this.deliveryFeeCents = deliveryFeeCents;
        this.tipCents = tipCents;
        this.otherFeeCents = otherFeeCents;
        this.discountCents = discountCents;
    }

    public static Adjustments none() {
        return new Adjustments(0L, 0L, 0L, 0L, 0L);
    }

    public static Adjustments taxOnly(long taxCents) {
        return new Adjustments(taxCents, 0L, 0L, 0L, 0L);
    }

    public long taxCents() {
        return taxCents;
    }

    public long deliveryFeeCents() {
        return deliveryFeeCents;
    }

    public long tipCents() {
        return tipCents;
    }

    public long otherFeeCents() {
        return otherFeeCents;
    }

    /** Stored positive, applied negative (SPEC 5.3, SPEC 6.4.4). */
    public long discountCents() {
        return discountCents;
    }

    public long valueOf(AdjustmentType type) {
        switch (type) {
            case TAX:
                return taxCents;
            case DELIVERY_FEE:
                return deliveryFeeCents;
            case TIP:
                return tipCents;
            case OTHER_FEE:
                return otherFeeCents;
            default:
                throw new IllegalArgumentException("unknown adjustment " + type);
        }
    }

    /** The sum the postcondition in SPEC 6.4.13 is checked against. */
    public long total() {
        return taxCents + deliveryFeeCents + tipCents + otherFeeCents;
    }
}
