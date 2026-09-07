package com.householdsplitter.core.parse.model;

/** The summary block of SPEC 8.6.3, as parsed. All values are cents. */
public final class ParsedAdjustments {

    private final long statedSubtotalCents;
    private final long taxCents;
    private final long deliveryFeeCents;
    private final long tipCents;
    private final long otherFeeCents;
    private final long discountCents;
    private final long statedTotalCents;

    public ParsedAdjustments(long statedSubtotalCents, long taxCents, long deliveryFeeCents,
                             long tipCents, long otherFeeCents, long discountCents,
                             long statedTotalCents) {
        this.statedSubtotalCents = statedSubtotalCents;
        this.taxCents = taxCents;
        this.deliveryFeeCents = deliveryFeeCents;
        this.tipCents = tipCents;
        this.otherFeeCents = otherFeeCents;
        this.discountCents = discountCents;
        this.statedTotalCents = statedTotalCents;
    }

    public static ParsedAdjustments empty() {
        return new ParsedAdjustments(0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    public long statedSubtotalCents() {
        return statedSubtotalCents;
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

    /** Stored positive (SPEC 5.3). */
    public long discountCents() {
        return discountCents;
    }

    public long statedTotalCents() {
        return statedTotalCents;
    }

    public long feesTipAndTax() {
        return taxCents + deliveryFeeCents + tipCents + otherFeeCents;
    }
}
