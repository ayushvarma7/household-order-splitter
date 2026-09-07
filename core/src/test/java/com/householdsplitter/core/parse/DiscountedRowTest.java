package com.householdsplitter.core.parse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.parse.model.ParsedItem;
import com.householdsplitter.core.parse.model.ParsedOrder;
import com.householdsplitter.core.parse.walmart.WalmartLayoutParser;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Discounted item rows, traced from a real Aug 30 order.
 *
 * <p>The layout that caused the bug:
 * <pre>
 *   Fresh Gala Apples, 3 lb Bag        $3.24     &lt;- charged, green
 *   $1.08/lb                           $4.44     &lt;- struck through
 *   Qty 1
 *   $1.20 from savings
 * </pre>
 *
 * <p>Two amounts in the price column on two different bands. The parser treated the second
 * as a second item, so the order gained a $4.44 charge nobody paid, named from whatever text
 * happened to be nearby: the packaging in the thumbnail and the savings note, giving
 * "GALA APPLES $1.20 from savings".
 *
 * <p>Only the charged price is a charge. The user put it plainly: the final billed figure is
 * what matters and the original-then-discounted display is not needed.
 */
public class DiscountedRowTest {

    /**
     * Enough vertical room to clear the top edge zone. SPEC 8.7.5 drops a nameless row
     * found near a screenshot edge, on the grounds that its other half is on the adjacent
     * image, and that rule would otherwise decide these tests instead of the one under test.
     */
    private static final int EDGE_CLEARANCE = 400;

    private static ParsedOrder parse(Page... pages) {
        return new WalmartLayoutParser().parse(Arrays.asList(
                pages.length > 0 ? pages[0].elements() : null));
    }

    /** The reported bug. One card, one item, and the charged price. */
    @Test
    public void aDiscountedRowYieldsOneItemAtTheChargedPrice() {
        Page page = Page.image(0)
                .appBar("Aug 30, 2026 order")
                .section("16 shopped")
                .itemStart("Fresh Gala Apples, 3 lb Bag", "$3.24")
                .struckOriginalBelow("$1.08/lb", "$4.44")
                .thumbnailText("GALA APPLES")
                .line("Qty 1")
                .line("$1.20 from savings")
                .chrome("+ Add")
                .chrome("Review item");

        List<ParsedItem> items = parse(page).items();

        assertEquals("the struck original is not a second item", 1, items.size());
        assertEquals("Fresh Gala Apples, 3 lb Bag", items.get(0).name());
        assertEquals(324L, items.get(0).lineTotalCents());
    }

    /** The savings note describes the price; it is not part of what the thing is called. */
    @Test
    public void theSavingsNoteStaysOutOfTheName() {
        Page page = Page.image(0)
                .gap(EDGE_CLEARANCE)
                .itemStart("Fresh Gala Apples, 3 lb Bag", "$3.24")
                .struckOriginalBelow("$1.08/lb", "$4.44")
                .line("Qty 1")
                .line("$1.20 from savings");

        ParsedItem item = parse(page).items().get(0);

        assertFalse(item.name(), item.name().toLowerCase().contains("savings"));
        assertEquals("Fresh Gala Apples, 3 lb Bag", item.name());
        assertTrue("but it is kept in the raw text",
                item.rawOcrText().toLowerCase().contains("from savings"));
    }

    /** Packaging text inside the thumbnail is not part of the name either. */
    @Test
    public void thumbnailPackagingTextStaysOutOfTheName() {
        Page page = Page.image(0)
                .gap(EDGE_CLEARANCE)
                .itemStart("Fresh Gala Apples, 3 lb Bag", "$3.24")
                .thumbnailText("GALA APPLES")
                .line("Qty 1");

        ParsedItem item = parse(page).items().get(0);

        assertFalse(item.name(), item.name().contains("GALA APPLES"));
        assertEquals("Fresh Gala Apples, 3 lb Bag", item.name());
    }

    /** The unit price is still captured for display, and still never charged. */
    @Test
    public void theUnitPriceIsStillCaptured() {
        Page page = Page.image(0)
                .gap(EDGE_CLEARANCE)
                .itemStart("Fresh Gala Apples, 3 lb Bag", "$3.24")
                .struckOriginalBelow("$1.08/lb", "$4.44")
                .line("Qty 1")
                .line("$1.20 from savings");

        ParsedItem item = parse(page).items().get(0);

        assertEquals("$1.08/lb", item.unitPriceText());
        assertEquals(324L, item.lineTotalCents());
    }

    /**
     * The card's later lines still belong to the item. The struck band must not end the
     * block, or the quantity below it would be lost.
     */
    @Test
    public void theQuantityBelowTheStruckPriceIsStillRead() {
        Page page = Page.image(0)
                .gap(EDGE_CLEARANCE)
                .itemStart("Great Value Whole Vitamin D Milk", "$2.73")
                .struckOriginalBelow("$0.71/qt", "$3.42")
                .line("Qty 2")
                .line("$0.69 from savings");

        ParsedItem item = parse(page).items().get(0);

        assertEquals(1, parse(page).items().size());
        assertEquals(2, item.quantity());
        assertEquals(273L, item.lineTotalCents());
    }

    /**
     * The safety rule. When the arithmetic does not reconcile, the second amount is not
     * assumed to be a struck original: it becomes an item, flagged, for the user to judge.
     *
     * <p>An invented row is visible in a list the user reads. A dropped charge is a total
     * that is quietly short, which is the worse failure by far.
     */
    @Test
    public void anUnreconciledSecondAmountIsKeptRatherThanGuessedAway() {
        Page page = Page.image(0)
                .gap(EDGE_CLEARANCE)
                .itemStart("Fresh Gala Apples, 3 lb Bag", "$3.24")
                .struckOriginalBelow("$1.08/lb", "$4.44")
                .line("Qty 1")
                .line("$9.99 from savings");   // 4.44 - 3.24 is 1.20, not 9.99

        List<ParsedItem> items = parse(page).items();

        assertEquals("the charge is kept because nothing proved it was not one",
                2, items.size());
    }

    /** With no savings note at all there is nothing to check against, so nothing is dropped. */
    @Test
    public void aSecondAmountWithNoSavingsNoteIsKept() {
        Page page = Page.image(0)
                .gap(EDGE_CLEARANCE)
                .itemStart("Fresh Gala Apples, 3 lb Bag", "$3.24")
                .struckOriginalBelow("$1.08/lb", "$4.44")
                .line("Qty 1");

        assertEquals(2, parse(page).items().size());
    }

    /** A lower second amount is never an original price, so the rule cannot fire on it. */
    @Test
    public void aCheaperSecondAmountIsNeverTreatedAsAnOriginal() {
        Page page = Page.image(0)
                .gap(EDGE_CLEARANCE)
                .itemStart("First item", "$9.00")
                .struckOriginalBelow("$1.08/lb", "$2.00")
                .line("Qty 1")
                .line("$7.00 from savings");   // the difference matches, but 2.00 < 9.00

        assertEquals(2, parse(page).items().size());
    }

    /**
     * Two ordinary items in a row must still be two items, even when the second happens to
     * cost more than the first.
     */
    @Test
    public void twoRealItemsAreStillTwoItems() {
        Page page = Page.image(0)
                .gap(EDGE_CLEARANCE)
                .itemStart("Fresh Whole Russet Potatoes, 5 lb Bag", "$3.87")
                .line("77.4¢/lb")
                .line("Qty 1")
                .itemStart("Freshness Guaranteed Boneless Skinless Chicken Thighs", "$14.65")
                .line("$3.22/lb")
                .line("Qty 1");

        List<ParsedItem> items = parse(page).items();

        assertEquals(2, items.size());
        assertEquals(387L, items.get(0).lineTotalCents());
        assertEquals(1465L, items.get(1).lineTotalCents());
    }

    /** "Ordered price" on a weighed item is commentary too, and the charged figure wins. */
    @Test
    public void theOrderedPriceNoteStaysOutOfTheName() {
        Page page = Page.image(0)
                .gap(EDGE_CLEARANCE)
                .itemStart("Freshness Guaranteed Boneless Skinless Chicken Thighs", "$14.65")
                .line("$3.22/lb")
                .line("Qty 1")
                .line("Ordered price $13.97");

        List<ParsedItem> items = parse(page).items();

        assertEquals(1, items.size());
        assertEquals("the charged weight price, not the estimate", 1465L,
                items.get(0).lineTotalCents());
        assertFalse(items.get(0).name().toLowerCase().contains("ordered price"));
    }
}
