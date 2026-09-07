package com.householdsplitter.data.entity;

/** SPEC 5.3. */
public enum OrderStatus {
    /** Being built: imported, reviewed or part-assigned (SPEC 7.3.4). */
    DRAFT,
    /** Every item answered for, totals computed. */
    ASSIGNED,
    /** SPEC 7.10.7: read-only until an explicit Reopen. */
    SETTLED
}
