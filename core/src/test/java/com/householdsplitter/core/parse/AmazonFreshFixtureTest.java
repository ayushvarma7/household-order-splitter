package com.householdsplitter.core.parse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.parse.amazon.AmazonFreshLayoutParser;
import com.householdsplitter.core.parse.model.OcrElement;
import com.householdsplitter.core.parse.model.OrderField;
import com.householdsplitter.core.parse.model.ParsedItem;
import com.householdsplitter.core.parse.model.ParsedOrder;
import com.householdsplitter.core.parse.model.Reconciliation;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Two real Amazon Fresh order pages, traced element by element.
 *
 * <p>Every string is what the screenshot prints and every box is where it sits on a
 * 921 x 2048 capture, measured off the image rather than estimated, with two exceptions:
 * the order number and the card's last four digits are placeholders of the right shape,
 * because this file is committed and those identify a real order and a real card. Neither
 * changes what is under test, which is that the card line is discarded and that an order
 * number is captured from wherever it appears.
 *
 * <p>The screenshots themselves are not committed and never will be. Two of the six carry a
 * payment card, so {@code /scratchpad/} is in {@code .gitignore} and this traced fixture is
 * the committed artefact instead. That is the arrangement SPEC 12.4 asks for anyway: text
 * plus bounding boxes, no images and no network.
 */
public class AmazonFreshFixtureTest {

    private static final int W = 921;
    private static final int H = 2048;

    /** Product title, in Amazon's blue link text. Left edge measured at 179 permille. */
    private static void name(Page page, int y, int xEnd, String text) {
        page.at(y, 165, xEnd, text, 26, 96);
    }

    /** Right-aligned line total. Measured at 877 to 960 permille on every page. */
    private static void price(Page page, int y, String text) {
        page.at(y, 809, 883, text, 26, 97);
    }

    /** Row metadata under the title: the quantity, a weight, an adjustment note. */
    private static void meta(Page page, int y, int xEnd, String text) {
        page.at(y, 165, xEnd, text, 25, 95);
    }

    /**
     * Text ML Kit reads off the product photograph. The thumbnail occupies 43 to 140
     * permille, which is why {@code nameZoneStartPermille} is 160: without a left edge this
     * packaging text lands inside the product name.
     */
    private static void thumbnail(Page page, int y, String text) {
        page.at(y, 45, 128, text, 22, 84);
    }

    /** A summary row: label on the left, amounts on the same band (SPEC 8.6.3). */
    private static void summary(Page page, int y, int labelEnd, String label, String... amounts) {
        page.at(y, 39, labelEnd, label, 28, 96);
        if (amounts.length == 1) {
            price(page, y, amounts[0]);
            return;
        }
        // The struck-through original measured at 727 to 858 permille, the charged amount
        // at 863 to 958, both on one band.
        page.at(y, 670, 791, amounts[0], 28, 95);
        page.at(y, 795, 883, amounts[1], 28, 96);
    }

    /** The pinned tan header: status bar and search field, both above 120 permille. */
    private static void header(Page page, String clock) {
        page.at(45, 87, 143, clock, 30, 90);
        page.at(45, 780, 830, "39", 30, 88);
        page.at(169, 220, 614, "Search or ask a question", 36, 93);
    }

    /** "Items in your order (18)" through the seventh row, exactly as captured. */
    private static Page itemsPage() {
        Page page = Page.sized(0, W, H);
        header(page, "1:10");
        page.at(291, 37, 478, "Items in your order (18)", 37, 97);

        thumbnail(page, 395, "CHICKEN BREASTS");
        name(page, 393, 615, "Amazon Grocery, Chicken Breast");
        price(page, 393, "$7.26");
        name(page, 438, 607, "Fillets, Boneless Skinless, 100%");
        name(page, 483, 476, "Natural, Weight Varies");
        meta(page, 528, 242, "Qty: 1");
        meta(page, 571, 330, "Weight: 2.08 lb");
        page.at(571, 340, 506, "($3.49/lb)", 25, 93);
        meta(page, 625, 675, "Weight adjusted from est. 2.00 lb");

        thumbnail(page, 783, "BROCCOLI");
        name(page, 750, 633, "Amazon Grocery, Broccoli Florets,");
        price(page, 750, "$1.16");
        name(page, 781, 354, "12 Oz, Frozen");
        meta(page, 840, 242, "Qty: 1");

        name(page, 949, 606, "Amazon Grocery, Red Onions, 2");
        price(page, 949, "$3.48");
        name(page, 983, 190, "Lb");
        meta(page, 1039, 242, "Qty: 1");

        thumbnail(page, 1195, "STRAWBERRIES");
        name(page, 1148, 460, "Amazon Grocery, Sliced");
        price(page, 1148, "$5.90");
        name(page, 1193, 540, "Strawberries,16 Oz, Frozen");
        name(page, 1237, 497, "(Previously Happy Belly,");
        name(page, 1281, 453, "Packaging May Vary)");
        meta(page, 1328, 247, "Qty: 2");

        name(page, 1437, 406, "Plum Roma Tomato");
        price(page, 1437, "$1.74");
        meta(page, 1482, 228, "Qty: 6");

        name(page, 1607, 490, "Banana Bunch (4-5 Count)");
        price(page, 1607, "$0.99");
        meta(page, 1653, 242, "Qty: 1");

        thumbnail(page, 1790, "OATS");
        name(page, 1780, 612, "Amazon Grocery, Old Fashioned");
        price(page, 1780, "$2.78");
        name(page, 1825, 327, "Oats, 18 Oz");
        // The last row's quantity is clipped by the pinned navigation bar, see
        // quantityClippedByTheNavigationBarIsNotRead below.
        meta(page, 1871, 242, "Qty: 1");

        // The five-icon navigation bar Amazon pins over the page.
        page.at(1915, 80, 110, "⌂", 34, 70);
        page.at(1915, 270, 300, "0", 24, 72);
        return page;
    }

    /** The order summary card, the payment card and the subscriber savings banner. */
    private static Page summaryPage() {
        Page page = Page.sized(1, W, H);
        header(page, "1:09");
        page.at(255, 39, 392, "Delivery instructions:", 33, 96);
        page.at(300, 39, 271, "None provided", 25, 96);
        page.at(453, 39, 321, "Order summary", 37, 97);
        page.at(537, 39, 493, "Order #: 111-0000000-0000000", 20, 94);
        page.at(581, 39, 544, "Ordered September 6, 2026 6:02PM", 21, 95);

        summary(page, 648, 330, "Item(s) Subtotal:", "$38.93");
        summary(page, 692, 239, "Delivery Fee:", "$13.95", "$0.00");
        summary(page, 738, 340, "Total before tax:", "$38.93");
        summary(page, 782, 470, "Estimated tax to be collected:", "$1.12");
        summary(page, 829, 220, "Driver tip:", "$0.00");
        summary(page, 872, 280, "Grand Total:", "$40.05");

        page.at(987, 39, 321, "Payment method", 32, 97);
        page.at(1061, 37, 357, "Visa ending in 0000", 26, 96);
        page.at(1145, 75, 440, "View related transactions", 31, 96);
        page.at(1283, 33, 725, "You're saving $13.95 on this order", 50, 96);
        page.at(1355, 33, 830, "as a Grocery subscriber and", 50, 96);
        page.at(1490, 33, 845, "Includes deals & discounts on items and delivery", 28, 95);
        page.at(1700, 33, 680, "Save time with Repeat Items", 40, 96);
        page.at(1770, 55, 340, "Purchased Sep 2026", 26, 90);
        return page;
    }

    private static ParsedOrder parse(Page... pages) {
        List<List<OcrElement>> input = new java.util.ArrayList<>();
        for (Page page : pages) {
            input.add(page.elements());
        }
        return new AmazonFreshLayoutParser().parse(input);
    }

    private static ParsedItem itemNamed(ParsedOrder order, String fragment) {
        for (ParsedItem item : order.items()) {
            if (item.name().contains(fragment)) {
                return item;
            }
        }
        return null;
    }

    @Test
    public void readsEverySevenRowsAtTheirChargedPrice() {
        ParsedOrder order = parse(itemsPage());

        List<String> expected = Arrays.asList(
                "Chicken Breast", "Broccoli Florets", "Red Onions", "Strawberries",
                "Plum Roma Tomato", "Banana Bunch", "Old Fashioned");
        long[] cents = {726, 116, 348, 590, 174, 99, 278};

        assertEquals("one row per product and no more", 7, order.items().size());
        for (int i = 0; i < expected.size(); i++) {
            ParsedItem item = itemNamed(order, expected.get(i));
            assertNotNull("missing row for " + expected.get(i), item);
            assertEquals(expected.get(i) + " line total", cents[i], item.lineTotalCents());
        }
    }

    @Test
    public void theRightHandPriceIsTheExtendedLineTotalNotAUnitPrice() {
        // Confirmed by a natural experiment across two orders: the same yogurt is $2.99 at
        // Qty 1 and $5.98 at Qty 2. The parser must not multiply again.
        ParsedOrder order = parse(itemsPage());

        ParsedItem strawberries = itemNamed(order, "Strawberries");
        assertEquals(2, strawberries.quantity());
        assertEquals("quantity two, and $5.90 is already both of them",
                590, strawberries.lineTotalCents());

        ParsedItem tomatoes = itemNamed(order, "Plum Roma Tomato");
        assertEquals(6, tomatoes.quantity());
        assertEquals(174, tomatoes.lineTotalCents());
    }

    @Test
    public void aWeighedRowKeepsTheChargeAndDropsTheRateFromTheName() {
        ParsedOrder order = parse(itemsPage());
        ParsedItem chicken = itemNamed(order, "Chicken Breast");

        // 2.08 lb at $3.49/lb is $7.2592, printed and charged as $7.26.
        assertEquals(726, chicken.lineTotalCents());
        assertFalse("the rate is not part of the name", chicken.name().contains("3.49"));
        assertFalse("nor is the weight", chicken.name().toLowerCase().contains("weight:"));
        assertFalse("nor is the adjustment note",
                chicken.name().toLowerCase().contains("adjusted"));
        assertTrue("but all of it survives in the raw text",
                chicken.rawOcrText().contains("Weight adjusted from est. 2.00 lb"));
    }

    @Test
    public void everyNameIsExactlyWhatTheScreenshotPrints() {
        // Structural extraction is exact, not approximate. Each of these is the product
        // title as printed, with the thumbnail's packaging text, the quantity, the weight
        // and the adjustment note all excluded, and a title wrapped over as many as four
        // lines joined back into one. If any of these has to be loosened, the parser is
        // wrong.
        ParsedOrder order = parse(itemsPage());
        List<String> expected = Arrays.asList(
                "Amazon Grocery, Chicken Breast Fillets, Boneless Skinless, "
                        + "100% Natural, Weight Varies",
                "Amazon Grocery, Broccoli Florets, 12 Oz, Frozen",
                "Amazon Grocery, Red Onions, 2 Lb",
                "Amazon Grocery, Sliced Strawberries,16 Oz, Frozen "
                        + "(Previously Happy Belly, Packaging May Vary)",
                "Plum Roma Tomato",
                "Banana Bunch (4-5 Count)",
                "Amazon Grocery, Old Fashioned Oats, 18 Oz");

        assertEquals(expected.size(), order.items().size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals("row " + i, expected.get(i), order.items().get(i).name());
        }
    }

    @Test
    public void packagingTextInTheThumbnailNeverReachesAName() {
        // The chicken pack photograph reads "CHICKEN BREASTS"; the title says "Chicken
        // Breast Fillets". The plural is therefore packaging and nothing else, which makes
        // it a usable probe for the leak that nameZoneStartPermille exists to stop. Sitting
        // at 48 to 139 permille, it is left of the 160 permille name boundary.
        ParsedOrder order = parse(itemsPage());
        for (ParsedItem item : order.items()) {
            assertFalse(item.name(), item.name().toUpperCase().contains("CHICKEN BREASTS"));
        }
        assertNotNull("and the row it belongs to is still read",
                itemNamed(order, "Chicken Breast Fillets"));
    }

    @Test
    public void theSummaryBlockIsReadAndReconciles() {
        ParsedOrder order = parse(summaryPage());

        assertEquals(3893L, (long) order.adjustments().statedSubtotalCents());
        assertEquals("the struck $13.95 never wins over the charged $0.00",
                0L, order.adjustments().deliveryFeeCents());
        assertEquals(112L, order.adjustments().taxCents());
        assertEquals(0L, order.adjustments().tipCents());
        assertEquals(4005L, (long) order.adjustments().statedTotalCents());

        Reconciliation reconciliation = Reconciler.reconcile(order);
        assertEquals("38.93 + 0 + 1.12 + 0 = 40.05",
                reconciliation.statedTotalCents(), reconciliation.computedTotalCents());
    }

    @Test
    public void theSubtotalDoesNotBecomeAPhantomProduct() {
        // The failure this whole store exists to avoid. "Item(s) Subtotal" is printed above
        // the first summary label the parser recognises, and it has $38.93 sitting in the
        // price column. Anchoring the end of the item region on "Order summary" rather than
        // on the first recognised label is what keeps it out.
        ParsedOrder order = parse(summaryPage());

        assertTrue("a summary page holds no purchases", order.items().isEmpty());
        for (ParsedItem item : order.items()) {
            assertFalse(item.name().toLowerCase().contains("subtotal"));
            assertFalse(item.name().toLowerCase().contains("total"));
            assertFalse(item.name().toLowerCase().contains("tax"));
        }
    }

    @Test
    public void totalBeforeTaxIsNotMistakenForTheOrderTotal() {
        ParsedOrder order = parse(summaryPage());
        // "Total before tax" is $38.93 and the Grand Total is $40.05. Reading the wrong one
        // would under-bill the household by the tax.
        assertEquals(4005L, (long) order.adjustments().statedTotalCents());
    }

    @Test
    public void theCardLineAndTheSavingsBannerAreDiscarded() {
        ParsedOrder order = parse(summaryPage());
        for (ParsedItem item : order.items()) {
            String name = item.name().toLowerCase();
            assertFalse(name.contains("visa"));
            assertFalse(name.contains("ending in"));
            assertFalse(name.contains("saving"));
            assertFalse(name.contains("repeat items"));
        }
        assertFalse("the banner's $13.95 is not a charge",
                order.adjustments().discountCents() == 1395L);
    }

    @Test
    public void readsTheOrderIdentity() {
        ParsedOrder order = parse(summaryPage());

        assertNotNull("the order number is captured despite Amazon's colon after the hash",
                order.externalOrderNo());
        assertTrue(order.externalOrderNo(), order.externalOrderNo().matches("\\d{3}-\\d{7}-\\d{7}"));
        assertNotNull("\"Ordered September 6, 2026\" puts the word before the date",
                order.orderDateMillis());
        assertNotNull(order.label());
        assertTrue(order.label(), order.label().endsWith(" Amazon Fresh"));
    }

    @Test
    public void bothPagesStitchIntoOneOrder() {
        ParsedOrder order = parse(itemsPage(), summaryPage());

        assertEquals(7, order.items().size());
        long sum = 0L;
        for (ParsedItem item : order.items()) {
            sum += item.lineTotalCents();
        }
        assertEquals("the seven captured rows come to $23.31", 2331L, sum);
        assertEquals(3893L, (long) order.adjustments().statedSubtotalCents());
        assertTrue("and $23.31 of a $38.93 subtotal means pages are missing, which the "
                        + "reconciler is what reports",
                sum < order.adjustments().statedSubtotalCents());
    }

    @Test
    public void theItemsHeaderIsNotAProductAndStatesNoUnitCount() {
        ParsedOrder order = parse(itemsPage());
        assertEquals(7, order.items().size());
        assertEquals("the (18) is not claimed as a unit count, because whether it counts "
                        + "rows or units has not been established",
                -1, order.deliveredUnitCount());
    }

    /**
     * What the wrong vocabulary does to the right pages, which is the case for asking.
     *
     * <p>Handing Amazon's summary page to the Walmart reader does not throw, refuse, or
     * produce obvious nonsense. It reads a plausible order that is wrong in two ways at
     * once, and the only reason the user finds out is that one row arrives with no name.
     *
     * <p>This is why {@link com.householdsplitter.core.parse.StoreKind} is chosen by the
     * user rather than detected. A detector that got this wrong would be silently building
     * the same order.
     */
    @Test
    public void theWalmartReaderMisreadsAnAmazonPage() {
        ParsedOrder amazon = new AmazonFreshLayoutParser()
                .parse(java.util.Collections.singletonList(summaryPage().elements()));
        ParsedOrder walmart = new com.householdsplitter.core.parse.walmart.WalmartLayoutParser()
                .parse(java.util.Collections.singletonList(summaryPage().elements()));

        // Read correctly, a summary page is all order-level figures and no purchases.
        assertTrue(amazon.items().isEmpty());
        assertEquals(4005L, (long) amazon.adjustments().statedTotalCents());

        // Read with the wrong vocabulary, the order total is never found, because "Grand
        // Total" is not a label Walmart prints.
        assertEquals("the bill goes unread entirely",
                0L, (long) walmart.adjustments().statedTotalCents());

        // And the subtotal, sitting above the first label Walmart does recognise, is inside
        // the item region with an amount in the price column, so it becomes a row.
        assertFalse("a summary line became a purchase", walmart.items().isEmpty());
        long invented = 0L;
        for (ParsedItem item : walmart.items()) {
            invented += item.lineTotalCents();
        }
        assertEquals("$38.93 of charge that nobody bought", 3893L, invented);
    }
}
