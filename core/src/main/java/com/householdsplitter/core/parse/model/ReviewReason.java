package com.householdsplitter.core.parse.model;

/**
 * Why a parsed row was flagged. SPEC 8.8.1 lists the triggers and SPEC 8.8.2 requires
 * every flagged row to state its reason in the review UI, so the reason is stored rather
 * than recomputed.
 */
public enum ReviewReason {

    LOW_CONFIDENCE("The text recogniser was unsure about this name"),
    SHORT_NAME("The name is very short"),
    QUANTITY_ABOVE_ONE("Quantity is more than one, so check the price covers all of them"),
    STRUCK_PRICE_RESOLVED("Two prices were printed here and the last one was used"),
    EXCLUDED_SECTION("This came from an unavailable, cancelled or refunded section"),
    EDGE_FRAGMENT("This row was cut across two screenshots and reassembled"),
    PRICE_OUTLIER("This price is unusually large"),
    MANUALLY_ADDED("You added this row by hand"),
    /**
     * A price was read but no name was. The row is kept rather than dropped, because
     * dropping it would quietly lose a charge and leave the totals short with nothing to
     * point at. SPEC 7.6.9 then blocks Continue until it has a name.
     */
    NAME_NOT_READ("A price was found here but no name. Type what it was.");

    private final String message;

    ReviewReason(String message) {
        this.message = message;
    }

    /** Plain wording, used as the content description in SPEC 7.6.4. */
    public String message() {
        return message;
    }

    public static ReviewReason fromName(String name) {
        for (ReviewReason reason : values()) {
            if (reason.name().equals(name)) {
                return reason;
            }
        }
        return null;
    }
}
