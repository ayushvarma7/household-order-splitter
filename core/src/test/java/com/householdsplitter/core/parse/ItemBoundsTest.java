package com.householdsplitter.core.parse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.parse.model.ItemBounds;
import com.householdsplitter.core.parse.model.ParsedItem;
import com.householdsplitter.core.parse.walmart.WalmartLayoutParser;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Where each row was read from, kept so the app can show the user the page it came from.
 *
 * <p>"Which one is this?" is a fair question about a mangled name or a charge that looks
 * wrong, and the only honest answer is the original screenshot with the row ringed.
 */
public class ItemBoundsTest {

    private static List<ParsedItem> parse(Page page) {
        return new WalmartLayoutParser().parse(Arrays.asList(page.elements())).items();
    }

    @Test
    public void aParsedRowKnowsWhereItCameFrom() {
        Page page = Page.image(0)
                .gap(400)
                .itemStart("Great Value Triple Cheddar", "$1.97")
                .line("Finely Shredded Cheese, 8 oz Bag")
                .line("Qty 1");

        ItemBounds bounds = parse(page).get(0).bounds();

        assertTrue(bounds.toString(), bounds.isKnown());
        assertEquals(0, bounds.imageIndex());
        assertTrue("spans the price column", bounds.rightPermille() > 800);
        assertTrue("starts at the name column", bounds.leftPermille() < 200);
        assertTrue("covers more than one line", 
                bounds.bottomPermille() - bounds.topPermille() > 20);
    }

    /** Two rows on one page occupy different vertical regions. */
    @Test
    public void eachRowGetsItsOwnRegion() {
        Page page = Page.image(0)
                .gap(400)
                .itemStart("First item", "$1.00")
                .line("Qty 1")
                .itemStart("Second item", "$2.00")
                .line("Qty 1");

        List<ParsedItem> items = parse(page);
        assertEquals(2, items.size());
        ItemBounds first = items.get(0).bounds();
        ItemBounds second = items.get(1).bounds();

        assertTrue(first.isKnown() && second.isKnown());
        assertTrue("the second row sits below the first",
                second.topPermille() >= first.bottomPermille() - 5);
    }

    /** The row on the second screenshot records that it is on the second screenshot. */
    @Test
    public void theImageIndexFollowsTheScreenshot() {
        Page one = Page.image(0).gap(400).itemStart("On page one", "$1.00").line("Qty 1");
        Page two = Page.image(1).gap(400).itemStart("On page two", "$2.00").line("Qty 1");

        List<ParsedItem> items = new WalmartLayoutParser()
                .parse(Arrays.asList(one.elements(), two.elements())).items();

        assertEquals(2, items.size());
        assertEquals(0, items.get(0).bounds().imageIndex());
        assertEquals(1, items.get(1).bounds().imageIndex());
    }

    /**
     * Permille, not pixels: the same layout captured at a different size measures the same.
     *
     * <p>Placed proportionally rather than through the fixed-pixel helpers, since those
     * assume a 1080-wide page and would put the price outside the price column on a wider
     * one.
     */
    @Test
    public void theBoxIsResolutionIndependent() {
        ItemBounds small = parse(proportionalPage(1080, 2400)).get(0).bounds();
        ItemBounds large = parse(proportionalPage(1440, 3200)).get(0).bounds();

        assertTrue("small " + small + " large " + large, small.isKnown() && large.isKnown());
        assertNear("left edge", small.leftPermille(), large.leftPermille(), 12);
        assertNear("top edge", small.topPermille(), large.topPermille(), 12);
        assertNear("right edge", small.rightPermille(), large.rightPermille(), 12);
        assertNear("bottom edge", small.bottomPermille(), large.bottomPermille(), 12);
    }

    /** One item, positioned as fractions of the page so it scales with the capture. */
    private static Page proportionalPage(int width, int height) {
        Page page = Page.sized(0, width, height);
        int nameLeft = width * 170 / 1000;
        int nameRight = width * 570 / 1000;
        int priceLeft = width * 830 / 1000;
        int priceRight = width * 960 / 1000;
        int firstLine = height * 300 / 1000;
        int step = height * 26 / 1000;
        int glyph = height * 17 / 1000;
        page.at(firstLine, nameLeft, nameRight, "Item", glyph, 95);
        page.at(firstLine, priceLeft, priceRight, "$1.00", glyph, 95);
        page.at(firstLine + step, nameLeft, nameLeft + width / 8, "Qty 1", glyph, 95);
        return page;
    }

    @Test
    public void padGrowsTheBoxAndStaysInsideTheImage() {
        ItemBounds tight = new ItemBounds(0, 100, 200, 300, 400);
        ItemBounds roomy = tight.padded(10);

        assertEquals(90, roomy.leftPermille());
        assertEquals(190, roomy.topPermille());
        assertEquals(310, roomy.rightPermille());
        assertEquals(410, roomy.bottomPermille());

        ItemBounds atEdge = new ItemBounds(0, 2, 3, 995, 998).padded(20);
        assertEquals(0, atEdge.leftPermille());
        assertEquals(0, atEdge.topPermille());
        assertEquals(1000, atEdge.rightPermille());
        assertEquals(1000, atEdge.bottomPermille());
    }

    @Test
    public void anUnknownBoxStaysUnknown() {
        assertFalse(ItemBounds.UNKNOWN.isKnown());
        assertFalse(ItemBounds.UNKNOWN.padded(50).isKnown());
    }

    private static void assertNear(String message, int expected, int actual, int tolerance) {
        assertTrue(message + ": expected " + expected + " within " + tolerance + " of " + actual,
                Math.abs(expected - actual) <= tolerance);
    }
}
