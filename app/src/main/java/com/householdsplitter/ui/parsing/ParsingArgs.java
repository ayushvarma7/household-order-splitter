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

    /**
     * True when the screenshots being read are every screenshot of an existing order, read
     * again as one page, rather than only the ones being added to it.
     */
    public static final String ARG_READ_ALL = "read_all";

    /**
     * Set by the parsing screen on the import screen behind it once a read has been saved:
     * the id of the order those screenshots now belong to. Going back to the import screen
     * then adds to that order instead of reading everything again into a second one.
     */
    public static final String RESULT_READ_INTO_ORDER = "read_into_order";

    private ParsingArgs() {
    }
}
