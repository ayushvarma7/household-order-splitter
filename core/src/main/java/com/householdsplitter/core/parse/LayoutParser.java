package com.householdsplitter.core.parse;

import com.householdsplitter.core.parse.model.OcrElement;
import com.householdsplitter.core.parse.model.ParsedOrder;

import java.util.List;

/**
 * Turns positioned OCR text into a {@link ParsedOrder}, for one store's page layout.
 *
 * <p>SPEC 3.5 rules out a second store in v1 but requires the parser be written so one
 * could be added without changing the domain layer. This interface is that requirement made
 * explicit: it is the only thing above the OCR that knows what a store's page looks like,
 * and everything below it, the money, the split, the entities, the repositories, takes a
 * {@link ParsedOrder} and never asks where it came from.
 *
 * <p>The contract is deliberately narrow. An implementation gets text with coordinates and
 * returns items and adjustments. It does no OCR (that is the app module's job, since it
 * needs a {@code Uri}), touches no database, and does no money arithmetic beyond reading
 * printed amounts into cents.
 *
 * <p>Two rules an implementation must hold to, because the rest of the app depends on them
 * rather than re-checking:
 * <ul>
 *   <li>Amounts are {@code long} cents. No {@code float} or {@code double}, per SPEC 6.1.
 *   <li>A charge that cannot be read confidently is returned flagged for review, never
 *       dropped. A missing item is a silently wrong split; a flagged one is a question.
 * </ul>
 */
public interface LayoutParser {

    /**
     * The store this parser reads, for the review screen and error messages. Not an
     * identifier: nothing branches on it.
     */
    String storeName();

    /**
     * Reads one order from the OCR of its screenshots, in the order the user arranged them
     * (SPEC 7.4.4). Each inner list is one page's elements.
     *
     * <p>Never throws for unreadable content: an order with no items is a valid result, and
     * SPEC 7.5.3 gives the user somewhere to go from there.
     */
    ParsedOrder parse(List<List<OcrElement>> pages);
}
