package com.householdsplitter.core.parse.layout;

import com.householdsplitter.core.parse.model.OrderField;

/**
 * Everything a receipt reader has to know in words rather than in geometry.
 *
 * <p>{@link ReceiptLayoutParser} holds the shape of the job: group elements into bands,
 * find the price column, open a block at a price and grow it downward, stop at the
 * summary. None of that is Walmart's or Amazon's. What differs between two stores is the
 * wording they print and where they print it, and those are exactly the two things a store
 * supplies: this interface for the wording, {@link ParseTuning} for the geometry.
 *
 * <p>Splitting it this way is what stops a second store becoming a second parser. The
 * alternative, copying the pipeline and editing the strings, would duplicate the money
 * path, and a bug fixed in one copy would stay live in the other.
 *
 * <p>Two rules bind every implementation, both inherited from
 * {@link com.householdsplitter.core.parse.LayoutParser}:
 *
 * <ul>
 *   <li>An unrecognised line is never guessed at. A method here returns "not mine" rather
 *       than a plausible answer, because the caller can flag an unread row for the user to
 *       fix but cannot recover from a confidently wrong one.
 *   <li>Every pattern is wording observed on a real screenshot. A label that never appears
 *       costs nothing; a label invented to look thorough can match something else and drop
 *       a real charge.
 * </ul>
 *
 * <p>Implementations receive raw OCR text and are expected to run it through
 * {@link Normalise} themselves, so each store can decide how much punctuation noise its
 * own wording tolerates.
 */
public interface StoreVocabulary {

    /** Shown on the review screen and used to label the order. Nothing branches on it. */
    String storeName();

    /** The geometry that goes with this wording. */
    ParseTuning tuning();

    // ----- interface furniture, SPEC 8.4 -------------------------------------------------

    /** True when the line is a button, a card header, a status bar reading or similar. */
    boolean isChrome(String text);

    /**
     * True when this line marks the end of the purchased rows.
     *
     * <p>Anchoring on a header that always sits below the list is what keeps the summary
     * block out of the item region even when a label inside it is unrecognised. Relying on
     * recognising the first summary label instead would put every line above that label
     * back in play, and a subtotal printed in the price column then opens a block and
     * becomes a phantom purchase.
     */
    boolean endsItemRegion(String text);

    /**
     * True when the line belongs to a suggestion carousel rather than the order: Walmart's
     * rating strip, Amazon's repeat-items strip. Those cards carry product names and
     * images, so they read exactly like purchases.
     */
    boolean isCarouselMarker(String text);

    // ----- order identity, SPEC 8.6.1 and 8.6.6 -----------------------------------------

    /** The order number digits, or null when this line carries none. */
    String orderNumberIn(String text);

    /** The order date as epoch millis, or null when this line carries none. */
    Long orderDateMillis(String text);

    /** The editable default order label, e.g. "Sep 06 Amazon Fresh" (SPEC 8.6.2). */
    String defaultLabel(long epochMillis);

    // ----- the summary block, SPEC 8.6.3 ------------------------------------------------

    /**
     * Which order-level figure this label names, or null when it names none.
     *
     * <p>Matching is exact rather than by substring on purpose: "subtotal" contains
     * "total", so a substring test reads the subtotal as the order total and every figure
     * downstream of it is then wrong.
     */
    OrderField summaryLabelOf(String leftText);

    /** True when this band belongs to the summary block. */
    default boolean isSummaryLabel(String leftText) {
        return summaryLabelOf(leftText) != null;
    }

    // ----- sections, SPEC 8.5 -----------------------------------------------------------

    /** The section name without its count, or null when this is not a section header. */
    String sectionNameOf(String text);

    /** The unit count on a "N items delivered" header, or -1. A hint only (SPEC 8.5.4). */
    int deliveredUnitCount(String text);

    /** True for sections whose rows default to EXCLUDED, e.g. unavailable (SPEC 8.5.3). */
    boolean isExcludedSection(String sectionName);

    // ----- row grammar, SPEC 8.3.7 and 8.3.8 --------------------------------------------

    /**
     * The quantity printed on its own line within a row, or -1 when this is not one.
     *
     * <p>The quantity is kept rather than dropped, because a row charging for two of
     * something has to show the user the arithmetic behind its line total.
     */
    int quantityIn(String line);

    /**
     * True when the line is row metadata rather than part of the product name: a multipack
     * count, a weight, a note about the price.
     *
     * <p>These are stripped from the name and retained in the row's raw text, so nothing
     * read off the screen is lost even though it is not what the thing is called.
     */
    boolean isRowMetadata(String line);

    /**
     * The amount a savings annotation states, in cents, or -1 when the line is not one or
     * does not say how much.
     *
     * <p>This is what makes a struck-through original provable instead of guessed. Given a
     * charged price, a larger amount beside it and a printed savings figure, the three
     * either reconcile or they do not, and only a row that reconciles may drop the larger
     * amount. Returning -1 for "a discount exists but not how big" is deliberate: that is
     * not enough to prove anything with.
     */
    long savingsCentsIn(String line);
}
