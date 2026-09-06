package com.householdsplitter.core.calc;

/** How tax and fees are spread across members. SPEC 6.4.9, SPEC 7.14.1. */
public enum AllocationMode {
    /** Weighted by each member's pre-tax total. The default. */
    PROPORTIONAL,
    /** One equal share per participant. */
    EQUAL
}
