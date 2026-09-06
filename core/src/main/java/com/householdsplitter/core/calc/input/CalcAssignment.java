package com.householdsplitter.core.calc.input;

/** One member's stake in one line item. SPEC 5.7. */
public final class CalcAssignment {

    private final long memberId;
    private final int shares;

    public CalcAssignment(long memberId, int shares) {
        if (shares < 1) {
            throw new IllegalArgumentException("shares must be at least 1, was " + shares);
        }
        this.memberId = memberId;
        this.shares = shares;
    }

    public static CalcAssignment of(long memberId) {
        return new CalcAssignment(memberId, 1);
    }

    public long memberId() {
        return memberId;
    }

    /** SPEC 7.9.6: a doubled share shows as "x2" on the chip. */
    public int shares() {
        return shares;
    }
}
