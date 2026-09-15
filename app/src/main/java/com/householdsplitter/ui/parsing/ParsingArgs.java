package com.householdsplitter.ui.parsing;

/** Argument keys shared between the import and parsing screens. */
public final class ParsingArgs {

    public static final String ARG_URIS = "image_uris";
    public static final String ARG_ORDER_ID = "order_id";

    /**
     * Which store the user picked before importing, as a {@link
     * com.householdsplitter.core.parse.StoreKind} name.
     *
     * <p>Carried as a nav argument rather than held in a setting, so that two orders being
     * imported from different stores cannot end up reading each other's layout.
     */
    public static final String ARG_STORE = "store";

    private ParsingArgs() {
    }
}
