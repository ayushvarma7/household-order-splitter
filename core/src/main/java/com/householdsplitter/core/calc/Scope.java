package com.householdsplitter.core.calc;

/**
 * How a line item is shared. SPEC 2.6 defines four values.
 *
 * <p>{@link #UNASSIGNED} is a fifth, added deliberately. SPEC 7.9.8 disables "Next" while
 * nothing is selected and SPEC 7.10.6 requires the summary to detect items nobody has
 * answered for yet, so "not yet answered" has to be representable. Defaulting a fresh
 * parse to COMMON would silently charge the whole household for an unanswered row, which
 * is the one failure mode this app exists to remove.
 */
public enum Scope {
    UNASSIGNED,
    COMMON,
    SUBSET,
    PERSONAL,
    EXCLUDED;

    /** True when the item still needs an answer from the user (SPEC 7.10.6). */
    public boolean isUnanswered() {
        return this == UNASSIGNED;
    }

    /** True when the item contributes money to somebody (SPEC 6.4.2). */
    public boolean isChargeable() {
        return this == COMMON || this == SUBSET || this == PERSONAL;
    }
}
