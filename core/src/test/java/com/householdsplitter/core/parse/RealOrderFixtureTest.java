package com.householdsplitter.core.parse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.parse.model.OcrElement;
import com.householdsplitter.core.parse.model.ParsedItem;
import com.householdsplitter.core.parse.model.ParsedOrder;
import com.householdsplitter.core.parse.model.Reconciliation;
import com.householdsplitter.core.parse.walmart.WalmartLayoutParser;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Three real Walmart screenshots from one order, traced element by element.
 *
 * <p>Every string below is exactly what is printed on the screenshots, and every box is
 * placed where it appears on a 899 x 1959 capture. This is the fixture SPEC 12.4 asks for:
 * text plus bounding boxes, no images and no network.
 *
 * <p>The point of this test is that structural extraction is exact, not approximate. Given
 * the text and where it sits, which line belongs to which row, which amount is the line
 * price and which is a unit price, and which lines are interface furniture are all
 * decidable with no guessing. If any assertion here has to be loosened, the parser is
 * wrong.
 */
public class RealOrderFixtureTest {

    private static final int W = 899;
    private static final int H = 1959;

    /** Left column: product names and metadata (SPEC 8.3.6). */
    private static void name(Page page, int y, String text) {
        page.at(y, 261, 261 + 14 * text.length(), text, 38, 96);
    }

    /** Right column: the line price, right-aligned against the first name line. */
    private static void price(Page page, int y, String text) {
        page.at(y, 748, 828, text, 40, 97);
    }

    private static void heading(Page page, int y, String text) {
        page.at(y, 69, 69 + 16 * text.length(), text, 44, 97);
    }

    private static void body(Page page, int y, String text) {
        page.at(y, 69, 69 + 14 * text.length(), text, 36, 95);
    }

    /** The blue bar: back chevron, the date title, and a cart printing its own total. */
    private static void appBar(Page page) {
        page.at(55, 78, 140, "7:30", 30, 90);
        page.at(55, 176, 300, "02:53", 30, 88);
        page.at(55, 770, 820, "79", 30, 88);
        page.at(173, 160, 523, "Sep 03, 2026 order", 46, 98);
        page.at(150, 858, 878, "0", 24, 80);
        page.at(205, 800, 862, "$0.00", 32, 92);
    }

    /** Screenshot one: delivery block, "33 items delivered", the two substituted rows. */
    private static Page pageOne() {
        Page page = Page.sized(0, W, H);
        appBar(page);
        body(page, 285, "Delivery dropped off on Sep 03");
        body(page, 332, "View delivery photo");
        heading(page, 470, "33 items delivered");
        body(page, 570, "Want to see what was substituted?");
        page.at(566, 700, 800, "View", 40, 95);
        heading(page, 712, "2 substituted");

        name(page, 845, "Great Value Sharp Cheddar");
        name(page, 889, "Cheese Snack, 9 oz Bag, 12");
        name(page, 933, "Cheese Sticks");
        name(page, 977, "Qty 1");
        price(page, 849, "$2.56");
        body(page, 1065, "+ Add");
        body(page, 1180, "Review item");

        name(page, 1383, "Equate Body Sponge, Color");
        name(page, 1427, "May Vary");
        name(page, 1471, "Multipack Quantity: 1");
        name(page, 1515, "Qty 1");
        price(page, 1387, "$1.00");
        body(page, 1605, "+ Add");
        body(page, 1719, "Review item");

        heading(page, 1845, "16 shopped");
        return page;
    }

    /**
     * Screenshot two: overlaps the first. It opens with a stray "Qty 1" cut off from the
     * previous row, repeats the "16 shopped" header, then carries three shopped rows, the
     * last of which is cut off at the bottom edge.
     */
    private static Page pageTwo() {
        Page page = Page.sized(1, W, H);
        appBar(page);
        page.at(55, 176, 300, "02:56", 30, 88);

        name(page, 235, "Qty 1");
        body(page, 320, "+ Add");
        body(page, 435, "Review item");
        heading(page, 580, "16 shopped");

        name(page, 712, "Great Value Triple Cheddar");
        name(page, 756, "Finely Shredded Cheese, 8 oz");
        name(page, 800, "Bag");
        name(page, 847, "$3.94/lb");
        name(page, 893, "Qty 1");
        price(page, 716, "$1.97");
        body(page, 980, "+ Add");
        body(page, 1095, "Review item");

        name(page, 1294, "Great Value Tomato Basil");
        name(page, 1338, "Garlic Pasta Sauce, 24 oz");
        name(page, 1384, "Multipack Quantity: 1");
        name(page, 1431, "$1.31/lb");
        name(page, 1477, "Qty 1");
        price(page, 1298, "$1.97");
        body(page, 1565, "+ Add");
        body(page, 1680, "Review item");

        name(page, 1855, "Great Value 100% Whole");
        price(page, 1852, "$4.18");
        return page;
    }

    /** Screenshot three: the rating carousel, the payment card and the summary block. */
    private static Page pageThree() {
        Page page = Page.sized(2, W, H);
        appBar(page);
        page.at(55, 176, 300, "03:07", 30, 88);

        // SPEC 8.4.8: a rating prompt, not a purchase. Stars, and no price column.
        page.at(305, 293, 555, "Fresh Banana, Each", 38, 96);
        page.at(410, 300, 620, "☆ ☆ ☆ ☆ ☆", 46, 70);

        page.at(740, 155, 460, "Payment method", 44, 97);
        page.at(845, 72, 172, "VISA", 40, 96);
        page.at(845, 195, 390, "Ending in 0000", 38, 96);
        page.at(845, 745, 848, "$54.02", 38, 96);

        page.at(1030, 52, 190, "Subtotal", 42, 98);
        page.at(1030, 762, 848, "$53.52", 40, 98);

        // SPEC 8.6.4: the struck-through original beside the charged amount.
        page.at(1145, 90, 492, "Free delivery from store", 42, 96);
        page.at(1145, 725, 800, "$9.95", 38, 94);
        page.at(1145, 812, 848, "$0", 38, 95);

        page.at(1260, 52, 155, "Taxes", 42, 98);
        page.at(1260, 780, 848, "$0.50", 40, 98);

        page.at(1375, 52, 200, "Driver tip", 42, 98);
        page.at(1375, 762, 848, "$0.00", 40, 98);

        page.at(1462, 52, 172, "Total", 52, 99);
        page.at(1462, 688, 848, "$54.02", 52, 99);

        page.at(1645, 190, 450, "Charge history", 42, 96);
        page.at(1688, 190, 630, "Your transaction activity for this order", 34, 94);
        page.at(1810, 20, 425, "Order# 1000001-12345678", 38, 96);
        return page;
    }

    private static ParsedOrder parseAll() {
        List<List<OcrElement>> pages = Arrays.asList(
                pageOne().elements(), pageTwo().elements(), pageThree().elements());
        return new WalmartLayoutParser().parse(pages);
    }

    /** Every purchased row, and nothing else. */
    @Test
    public void extractsExactlyTheFiveVisibleRows() {
        ParsedOrder order = parseAll();

        StringBuilder actual = new StringBuilder();
        for (ParsedItem item : order.items()) {
            actual.append(item.name()).append(" = ").append(item.lineTotalCents()).append('\n');
        }

        assertEquals(""
                        + "Great Value Sharp Cheddar Cheese Snack, 9 oz Bag, 12 Cheese Sticks = 256\n"
                        + "Equate Body Sponge, Color May Vary = 100\n"
                        + "Great Value Triple Cheddar Finely Shredded Cheese, 8 oz Bag = 197\n"
                        + "Great Value Tomato Basil Garlic Pasta Sauce, 24 oz = 197\n"
                        + "Great Value 100% Whole = 418\n",
                actual.toString());
    }

    /** SPEC 8.3.10: a name wrapping over three lines is one name, not three items. */
    @Test
    public void wrappedNamesAreJoinedNotTruncated() {
        ParsedItem cheddar = itemNamed(parseAll(), "Great Value Triple Cheddar Finely Shredded Cheese, 8 oz Bag");
        assertNotNull(cheddar);
        assertEquals(197L, cheddar.lineTotalCents());
        assertEquals(1, cheddar.quantity());
    }

    /** SPEC 8.3.3: the unit price is captured for display and never charged. */
    @Test
    public void unitPricesAreCapturedAndNeverCharged() {
        ParsedOrder order = parseAll();
        assertEquals("$3.94/lb", itemNamed(order,
                "Great Value Triple Cheddar Finely Shredded Cheese, 8 oz Bag").unitPriceText());
        assertEquals("$1.31/lb", itemNamed(order,
                "Great Value Tomato Basil Garlic Pasta Sauce, 24 oz").unitPriceText());
        for (ParsedItem item : order.items()) {
            assertFalse(item.name().contains("/lb"));
        }
    }

    /** SPEC 8.3.7: Qty and Multipack Quantity lines never reach a name. */
    @Test
    public void metadataLinesAreStripped() {
        for (ParsedItem item : parseAll().items()) {
            assertFalse(item.name(), item.name().toLowerCase().contains("qty"));
            assertFalse(item.name(), item.name().toLowerCase().contains("multipack"));
        }
    }

    /** SPEC 8.4.1: no button or link text survives into a name. */
    @Test
    public void chromeNeverBecomesAName() {
        for (ParsedItem item : parseAll().items()) {
            String lower = item.name().toLowerCase();
            assertFalse(item.name(), lower.contains("add"));
            assertFalse(item.name(), lower.contains("review item"));
            assertFalse(item.name(), lower.contains("delivery"));
            assertFalse(item.name(), lower.contains("visa"));
            assertFalse(item.name(), lower.contains("charge history"));
        }
    }

    /** SPEC 8.4.8: the carousel banana is a rating prompt, not a purchase. */
    @Test
    public void ratingCarouselProducesNoItem() {
        for (ParsedItem item : parseAll().items()) {
            assertFalse(item.name(), item.name().toLowerCase().contains("banana"));
        }
    }

    /** The cart in the app bar prints $0.00 in the price column. It is not a purchase. */
    @Test
    public void cartTotalInTheAppBarIsNotAnItem() {
        for (ParsedItem item : parseAll().items()) {
            assertFalse("the cart total became an item", item.lineTotalCents() == 0L);
        }
    }

    /** SPEC 8.4.7: the screen-recording timer is not a price and not a name. */
    @Test
    public void recordingTimerIsIgnored() {
        for (ParsedItem item : parseAll().items()) {
            assertFalse(item.name().contains("02:53"));
            assertFalse(item.name().contains("03:07"));
            assertFalse(item.name().contains("7:30"));
        }
    }

    /** SPEC 8.7.5: the stray "Qty 1" at the top of screenshot two is not a row. */
    @Test
    public void edgeFragmentIsDiscarded() {
        for (ParsedItem item : parseAll().items()) {
            assertFalse(item.name().trim().isEmpty());
            assertFalse(item.name().trim().equalsIgnoreCase("Qty 1"));
        }
    }

    /** SPEC 8.7.6: "16 shopped" appears on both screenshots and opens one section. */
    @Test
    public void repeatedSectionHeaderOpensOneSection() {
        ParsedOrder order = parseAll();
        assertEquals(Arrays.asList("items delivered", "substituted", "shopped"), order.sections());
        assertEquals("substituted", itemNamed(order,
                "Equate Body Sponge, Color May Vary").sourceSection());
        assertEquals("shopped", itemNamed(order,
                "Great Value Tomato Basil Garlic Pasta Sauce, 24 oz").sourceSection());
    }

    /** SPEC 8.6.3, 8.6.4 and 8.6.8: the whole summary block, exactly. */
    @Test
    public void summaryBlockIsExact() {
        ParsedOrder order = parseAll();
        assertEquals(5352L, order.adjustments().statedSubtotalCents());
        // The struck $9.95 loses to the charged $0 (SPEC 8.6.4).
        assertEquals(0L, order.adjustments().deliveryFeeCents());
        assertEquals(50L, order.adjustments().taxCents());
        assertEquals(0L, order.adjustments().tipCents());
        assertEquals(0L, order.adjustments().otherFeeCents());
        assertEquals(0L, order.adjustments().discountCents());
        // Subtotal contains "total" and must not be matched by the Total rule (SPEC 8.6.8).
        assertEquals(5402L, order.adjustments().statedTotalCents());
    }

    /** The $54.02 printed on the payment card is not a summary line and not an item. */
    @Test
    public void paymentCardAmountIsNotMistakenForAnything() {
        ParsedOrder order = parseAll();
        for (ParsedItem item : order.items()) {
            assertFalse(item.lineTotalCents() == 5402L);
        }
    }

    /** SPEC 8.6.1, 8.6.2 and 8.6.6. */
    @Test
    public void orderIdentityIsExtracted() {
        ParsedOrder order = parseAll();
        assertEquals("1000001-12345678", order.externalOrderNo());
        assertEquals("Sep 03 Walmart", order.label());
        assertNotNull(order.orderDateMillis());
    }

    /**
     * SPEC 8.5.4 and 8.9.5: "33 items delivered" is a unit count. Five rows is not a
     * failure, and reconciliation is done on money instead.
     */
    @Test
    public void unitCountNeverValidatesRowCount() {
        ParsedOrder order = parseAll();
        assertEquals(5, order.items().size());

        Reconciliation reconciliation = Reconciler.reconcile(order);
        // SPEC 8.9.4, the worked green-tick example: 53.52 + 0.00 + 0.50 + 0.00 = 54.02.
        assertEquals(5402L, reconciliation.computedTotalCents());
        assertTrue(reconciliation.totalMatches());

        // These five rows are only part of the order, so the subtotal check correctly
        // reports a gap. Advisory, never a gate (SPEC 8.9.4, PROMPT hard rule 7).
        assertEquals(1168L, reconciliation.itemsSubtotalCents());
        assertFalse(reconciliation.subtotalMatches());
    }

    private static ParsedItem itemNamed(ParsedOrder order, String name) {
        for (ParsedItem item : order.items()) {
            if (item.name().equals(name)) {
                return item;
            }
        }
        return null;
    }
}
