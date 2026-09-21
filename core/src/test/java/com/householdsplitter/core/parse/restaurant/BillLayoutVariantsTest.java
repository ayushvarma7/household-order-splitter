package com.householdsplitter.core.parse.restaurant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.parse.Reconciler;
import com.householdsplitter.core.parse.StoreKind;
import com.householdsplitter.core.parse.model.OcrElement;
import com.householdsplitter.core.parse.model.ParsedItem;
import com.householdsplitter.core.parse.model.ParsedOrder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Layouts found by running the reader over real receipts rather than by imagining them.
 *
 * <p>Each of these started as a defect visible in the output of
 * {@code RealBillCalibrationTest} against photographs and scans of actual bills. They are
 * written out as fixtures here so that the behaviour is pinned without the images, which
 * are not in this repository.
 */
public class BillLayoutVariantsTest {

    private static ParsedOrder read(List<List<OcrElement>> pages) {
        return StoreKind.RESTAURANT.layoutParser().parse(pages);
    }

    private static List<String> namesOf(ParsedOrder order) {
        List<String> names = new ArrayList<>();
        for (ParsedItem item : order.items()) {
            names.add(item.name());
        }
        return names;
    }

    /**
     * The layout that broke first: a description on one line, the figures on the next.
     *
     * <p>Growing the block downward from the price collects the description of the row
     * <em>below</em>, so every item ends up named after the one after it, and the last one
     * is named after the footer.
     */
    @Test
    public void aDescriptionPrintedAboveItsFiguresIsStillTheName() {
        ParsedOrder order = read(Bill.paper()
                .leftLine("CODE/DESC QTY PRICE AMOUNT")
                .line("MODELLING CLAY KIDDY FISH")
                .item(1, "PC", "$9.00")
                .line("GRILLED CHICKEN CHOP")
                .item(1, "PC", "$18.50")
                .summary("TOTAL", "$27.50")
                .pages());

        assertEquals(namesOf(order).toString(), 2, order.items().size());
        assertEquals("Modelling Clay Kiddy Fish", order.items().get(0).name());
        assertEquals("Grilled Chicken Chop", order.items().get(1).name());
        assertEquals(900L, order.items().get(0).lineTotalCents());
        assertEquals(1850L, order.items().get(1).lineTotalCents());
    }

    /** And a till that prints the name beside the amount is read exactly as before. */
    @Test
    public void aDescriptionBesideItsAmountIsUnaffected() {
        ParsedOrder order = read(Bill.paper()
                .leftLine("QTY DESC AMT")
                .item(1, "CAPUCCINO", "$5.40")
                .item(2, "CAESAR SALAD", "$23.38")
                .summary("SUBTOTAL", "$28.78")
                .pages());

        assertEquals(namesOf(order).toString(), 2, order.items().size());
        assertEquals("Capuccino", order.items().get(0).name());
        assertEquals("Caesar Salad", order.items().get(1).name());
    }

    /**
     * The count column is not a name.
     *
     * <p>"1 PC 9.00 0.00" is a count, a unit, a price and a discount. Stripping the count
     * used to leave "PC 9.00 0.00" standing in as the product.
     */
    @Test
    public void aRowOfNothingButFiguresNamesNothing() {
        ParsedOrder order = read(Bill.paper()
                .leftLine("CODE/DESC QTY PRICE AMOUNT")
                .line("WAXCO WINDSHIELD CLEANER 120ML")
                .item(1, "PC", "$8.02")
                .summary("TOTAL", "$8.02")
                .pages());

        assertEquals(1, order.items().size());
        String name = order.items().get(0).name();
        assertFalse(name, name.contains("8.02"));
        assertFalse(name, name.toLowerCase().startsWith("pc"));
    }

    /**
     * The check that used to fail on every bill that was read correctly.
     *
     * <p>A small bill states a total and no subtotal. Computing "printed subtotal plus tax"
     * there is computing "zero plus tax", which never reaches the total.
     */
    @Test
    public void aBillWithATotalAndNoSubtotalStillReconciles() {
        ParsedOrder order = read(Bill.paper()
                .leftLine("QTY DESC AMT")
                .item(1, "PAD THAI", "$18.00")
                .item(1, "THAI ICED TEA", "$4.50")
                .summary("TAX", "$1.97")
                .summary("TOTAL", "$24.47")
                .pages());

        assertEquals(2250L, order.itemsSubtotalCents());
        assertEquals(0L, order.adjustments().statedSubtotalCents());
        assertEquals(2447L, order.adjustments().statedTotalCents());
        assertTrue("rows plus tax should reach the printed total",
                Reconciler.reconcile(order).totalMatches());
    }

    /** And a bill that really is short still says so. */
    @Test
    public void aMissingRowIsStillCaughtWithoutAPrintedSubtotal() {
        ParsedOrder order = read(Bill.paper()
                .leftLine("QTY DESC AMT")
                .item(1, "PAD THAI", "$18.00")
                .summary("TAX", "$1.97")
                .summary("TOTAL", "$24.47")
                .pages());

        assertFalse("a dish is missing and the arithmetic should notice",
                Reconciler.reconcile(order).totalMatches());
        assertEquals(-450L, Reconciler.reconcile(order).totalDeltaCents());
    }

    /**
     * A figure the reader could not read is said out loud rather than left at zero.
     *
     * <p>A low quality scan turns "9.00" into "900". The amount is not guessed at, because
     * a dropped decimal point is a factor of a hundred, but a silent zero is
     * indistinguishable from a bill that printed no total at all.
     */
    @Test
    public void anUnreadableSummaryAmountIsReported() {
        ParsedOrder order = read(Bill.paper()
                .leftLine("QTY DESC AMT")
                .item(1, "NASI LEMAK", "$9.00")
                .summary("TOTAL", "900")
                .pages());

        assertEquals(0L, order.adjustments().statedTotalCents());
        assertFalse("the reader should say it could not read that line",
                order.warnings().isEmpty());
        assertTrue(order.warnings().toString(),
                order.warnings().get(0).toLowerCase().contains("total"));
    }

    /** The app lets you choose a currency symbol; the reader has to accept the one you chose. */
    @Test
    public void amountsAreReadInWhateverCurrencyTheTillPrints() {
        for (String symbol : new String[]{"$", "£", "€", "¥"}) {
            ParsedOrder order = read(Bill.paper()
                    .leftLine("QTY DESC AMT")
                    .item(1, "ESPRESSO", symbol + "3.20")
                    .item(1, "CROISSANT", symbol + "4.80")
                    .summary("TOTAL", symbol + "8.00")
                    .pages());
            assertEquals("currency " + symbol, 800L, order.itemsSubtotalCents());
            assertEquals("currency " + symbol, 800L, order.adjustments().statedTotalCents());
        }
    }

    /**
     * A curled bill, where straightening makes things worse.
     *
     * <p>Paper held in the hand bows, so its amounts lie on an arc. A line fitted through
     * an arc has a real slope, and rotating by it straightens one end while shearing the
     * rows apart at the other: measured on a real curved receipt it lost two dishes and a
     * third of the money.
     *
     * <p>The reader is not asked to predict which case a page is. Two cheap proxies were
     * tried and both were wrong on real receipts, in opposite directions. It reads the page
     * both ways and keeps whichever adds up, so this passes whether or not the tilt
     * estimate fires.
     */
    @Test
    public void aCurledBillIsReadTheWayThatWorks() {
        Bill bill = Bill.paper()
                .leftLine("QTY DESC AMT")
                .item(1, "STEAMED WONTONS", "$21.95")
                .item(1, "PORK WONTONS", "$13.95")
                .item(1, "SESAME CHICKEN", "$19.95")
                .item(1, "FRIED RICE", "$17.95")
                .summary("SUBTOTAL", "$73.80");

        ParsedOrder flat = read(bill.pages());
        assertEquals(namesOf(flat).toString(), 4, flat.items().size());
        assertEquals(7380L, flat.itemsSubtotalCents());

        // The same bill photographed on a curve. However the tilt estimate reacts to it,
        // the reading that is kept must not be worse than reading it as photographed.
        ParsedOrder curved = read(bill.bowed(70));
        assertTrue("a curled bill lost rows: " + namesOf(curved),
                curved.items().size() >= 4);
        assertEquals("and lost money", 7380L, curved.itemsSubtotalCents());
    }

    /**
     * A tilt is only measurable when there are enough amounts, spread far enough apart.
     *
     * <p>{@link com.householdsplitter.core.parse.layout.Deskew} wants four amounts covering
     * at least a seventh of the page height before it will fit a line through them, and
     * that floor is deliberate: four prices crowded into one corner describe the corner
     * rather than the page, and a rotation derived from them is noise acted upon.
     *
     * <p>The consequence is worth stating rather than discovering. A short bill photographed
     * at an angle is not straightened, because it does not carry enough evidence of the
     * angle. Four dishes over a few centimetres of till roll is a short bill.
     *
     * <p>What the reader promises even then is that it does not make things worse: it reads
     * the page as photographed as well, and keeps whichever adds up. So this asserts the
     * money, not the mechanism.
     */
    @Test
    public void aShortTiltedBillIsNeverReadWorseThanAsPhotographed() {
        Bill bill = Bill.paper()
                .leftLine("QTY DESC AMT")
                .item(1, "CAPUCCINO", "$5.40")
                .item(1, "CHOCOLATE DONUT", "$8.13")
                .item(2, "CAESAR SALAD", "$23.38")
                .item(1, "ICED TEA", "$4.60")
                .summary("SUBTOTAL", "$41.51");

        assertEquals(4151L, read(bill.pages()).itemsSubtotalCents());

        ParsedOrder tilted = read(bill.tilted(140));
        assertTrue("a tilted short bill should still find its dishes: " + namesOf(tilted),
                tilted.items().size() >= 4);
    }
}
