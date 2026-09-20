package com.householdsplitter.core.parse.restaurant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.parse.Reconciler;
import com.householdsplitter.core.parse.StoreKind;
import com.householdsplitter.core.parse.model.ParsedItem;
import com.householdsplitter.core.parse.model.ParsedOrder;
import com.householdsplitter.core.parse.model.Reconciliation;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The restaurant reader, against a bill laid out the way a till lays one out.
 *
 * <p>The receipt used through most of these is a real one in structure: a header with the
 * restaurant and its address, a date and a table line, a QTY/DESC/AMT column header, four
 * dishes, and a subtotal, tax and total block. The figures add up, which is what lets the
 * last test here check the property that matters more than any individual row.
 */
public class RestaurantBillTest {

    private static ParsedOrder read(List<List<com.householdsplitter.core.parse.model.OcrElement>>
                                            pages) {
        return StoreKind.RESTAURANT.layoutParser().parse(pages);
    }

    /** The bill from the reference photograph, item for item. */
    private static Bill gourmetCoffee() {
        return Bill.paper()
                .line("GOURMET COFFEE")
                .line("ADDRESS, STREET LOCATION")
                .line("------------------------")
                .leftLine("4/09/2024  8:36:18 AM")
                .leftLine("TABLE 3")
                .leftLine("HOST: JAMES")
                .leftLine("TAX INVOICE: ABN: 11346093268")
                .gap(30)
                .leftLine("QTY DESC AMT")
                .item(1, "CAPUCCINO", "$5.40")
                .item(1, "CHOCOLATE DONUT", "$8.13")
                .item(2, "CAESAR SALAD", "$23.38")
                .item(1, "ICED TEA", "$4.60")
                .gap(30)
                .summary("SUBTOTAL:", "$41.51")
                .summary("SALE TAX:", "$3.10")
                .summary("AMOUNT", "$44.61")
                .gap(30)
                .line("*** THANK YOU ***");
    }

    private static List<String> namesOf(ParsedOrder order) {
        List<String> names = new ArrayList<>();
        for (ParsedItem item : order.items()) {
            names.add(item.name());
        }
        return names;
    }

    @Test
    public void everyDishOnTheBillIsRead() {
        ParsedOrder order = read(gourmetCoffee().pages());
        assertEquals(namesOf(order).toString(), 4, order.items().size());
        assertEquals("Capuccino", order.items().get(0).name());
        assertEquals("Chocolate Donut", order.items().get(1).name());
        assertEquals("Caesar Salad", order.items().get(2).name());
        assertEquals("Iced Tea", order.items().get(3).name());
    }

    @Test
    public void theAmountsAreTheAmountsThatWerePrinted() {
        ParsedOrder order = read(gourmetCoffee().pages());
        assertEquals(540L, order.items().get(0).lineTotalCents());
        assertEquals(813L, order.items().get(1).lineTotalCents());
        assertEquals(2338L, order.items().get(2).lineTotalCents());
        assertEquals(460L, order.items().get(3).lineTotalCents());
    }

    @Test
    public void aCountPrintedInFrontOfTheDishBecomesItsQuantity() {
        ParsedOrder order = read(gourmetCoffee().pages());
        assertEquals(1, order.items().get(0).quantity());
        assertEquals("two salads, printed as a 2 in the count column",
                2, order.items().get(2).quantity());
    }

    @Test
    public void theSummaryBlockIsRead() {
        ParsedOrder order = read(gourmetCoffee().pages());
        assertEquals(4151L, order.adjustments().statedSubtotalCents());
        assertEquals(310L, order.adjustments().taxCents());
        assertEquals(4461L, order.adjustments().statedTotalCents());
    }

    /**
     * The check that a photographed bill makes possible and a screenshot does not. A whole
     * bill states its own subtotal, so the dishes must add up to it exactly.
     */
    @Test
    public void theDishesAddUpToThePrintedSubtotal() {
        ParsedOrder order = read(gourmetCoffee().pages());
        Reconciliation reconciliation = Reconciler.reconcile(order);
        assertEquals(4151L, order.itemsSubtotalCents());
        assertTrue("subtotal off by " + reconciliation.subtotalDeltaCents(),
                reconciliation.subtotalMatches());
        assertTrue("total off by " + reconciliation.totalDeltaCents(),
                reconciliation.totalMatches());
    }

    /** The whole reason the deskew stage exists. Nobody photographs paper square. */
    @Test
    public void aBillPhotographedAtAnAngleReadsTheSame() {
        ParsedOrder square = read(gourmetCoffee().pages());
        for (int slope : new int[]{-140, -70, 70, 140}) {
            ParsedOrder tilted = read(gourmetCoffee().tilted(slope));
            assertEquals("tilted by " + slope + ": " + namesOf(tilted),
                    namesOf(square), namesOf(tilted));
            assertEquals("tilted by " + slope,
                    square.itemsSubtotalCents(), tilted.itemsSubtotalCents());
            assertEquals("tilted by " + slope,
                    square.adjustments().statedTotalCents(),
                    tilted.adjustments().statedTotalCents());
        }
    }

    @Test
    public void theColumnHeaderIsNotADish() {
        assertFalse(namesOf(read(gourmetCoffee().pages())).toString(),
                namesOf(read(gourmetCoffee().pages())).contains("QTY DESC AMT"));
    }

    @Test
    public void theTotalIsNotADish() {
        List<String> names = namesOf(read(gourmetCoffee().pages()));
        for (String name : names) {
            assertFalse(name, name.toLowerCase().contains("subtotal"));
            assertFalse(name, name.toLowerCase().contains("tax"));
            assertFalse(name, name.toLowerCase().contains("amount"));
        }
    }

    /**
     * A small bill prints no subtotal at all. Without the total labels as a second anchor
     * the total line sits in the item region with an amount in the amount column, and
     * becomes a third dish that nobody ate.
     */
    @Test
    public void aBillWithNoSubtotalStillStopsAtTheTotal() {
        ParsedOrder order = read(Bill.paper()
                .line("THE CORNER DINER")
                .leftLine("QTY DESC AMT")
                .item(1, "SOUP OF THE DAY", "$6.50")
                .item(1, "FLAT WHITE", "$4.25")
                .gap(30)
                .summary("TOTAL", "$10.75")
                .pages());
        assertEquals(namesOf(order).toString(), 2, order.items().size());
        assertEquals(1075L, order.adjustments().statedTotalCents());
        assertEquals(1075L, order.itemsSubtotalCents());
    }

    @Test
    public void kitchenInstructionsDoNotBecomePartOfTheDishName() {
        ParsedOrder order = read(Bill.paper()
                .leftLine("QTY DESC AMT")
                .item(1, "CAESAR SALAD", "$12.00")
                .modifier("NO CROUTONS")
                .modifier("ADD CHICKEN")
                .item(1, "RIBEYE", "$34.00")
                .modifier("MEDIUM RARE")
                .summary("SUBTOTAL", "$46.00")
                .pages());
        assertEquals(namesOf(order).toString(), 2, order.items().size());
        assertEquals("Caesar Salad", order.items().get(0).name());
        assertEquals("Ribeye", order.items().get(1).name());
    }

    @Test
    public void aTipAlreadyPrintedOnTheBillIsRead() {
        ParsedOrder order = read(Bill.paper()
                .leftLine("QTY DESC AMT")
                .item(1, "PAD THAI", "$18.00")
                .summary("SUBTOTAL", "$18.00")
                .summary("TAX", "$1.58")
                .summary("GRATUITY", "$3.60")
                .summary("TOTAL", "$23.18")
                .pages());
        assertEquals(360L, order.adjustments().tipCents());
        assertEquals(158L, order.adjustments().taxCents());
        assertTrue(Reconciler.reconcile(order).totalMatches());
    }

    @Test
    public void aServiceChargeIsAFeeRatherThanADish() {
        ParsedOrder order = read(Bill.paper()
                .leftLine("QTY DESC AMT")
                .item(1, "TASTING MENU", "$95.00")
                .summary("SUBTOTAL", "$95.00")
                .summary("SERVICE CHARGE", "$17.10")
                .summary("TOTAL", "$112.10")
                .pages());
        assertEquals(namesOf(order).toString(), 1, order.items().size());
        assertEquals(1710L, order.adjustments().otherFeeCents());
    }

    /** The payment block, which is where the card details live. */
    @Test
    public void nothingFromThePaymentBlockBecomesADish() {
        ParsedOrder order = read(Bill.paper()
                .leftLine("QTY DESC AMT")
                .item(1, "BURGER", "$16.00")
                .summary("SUBTOTAL", "$16.00")
                .summary("TOTAL", "$16.00")
                .summary("VISA", "$16.00")
                .leftLine("CARD [redacted]")
                .leftLine("AUTH [redacted]")
                .line("CUSTOMER COPY")
                .line("SIGNATURE")
                .line("X______________")
                .pages());
        assertEquals(namesOf(order).toString(), 1, order.items().size());
        assertEquals("Burger", order.items().get(0).name());
    }

    @Test
    public void theDateOnTheBillIsRead() {
        ParsedOrder order = read(gourmetCoffee().pages());
        assertNotNull("no date read off the bill", order.orderDateMillis());
    }

    @Test
    public void aCountRunIntoTheDescriptionIsStillACount() {
        ParsedOrder order = read(Bill.paper()
                .leftLine("QTY DESC AMT")
                .itemRunTogether("3 SPRING ROLLS", "$13.50")
                .summary("SUBTOTAL", "$13.50")
                .pages());
        assertEquals(namesOf(order).toString(), 1, order.items().size());
        assertEquals("Spring Rolls", order.items().get(0).name());
        assertEquals(3, order.items().get(0).quantity());
    }

    /** A till that prints no count column at all. */
    @Test
    public void aBillWithNoCountColumnReadsAsSingles() {
        ParsedOrder order = read(Bill.paper()
                .item("ESPRESSO", "$3.00")
                .item("CROISSANT", "$4.50")
                .summary("SUBTOTAL", "$7.50")
                .pages());
        assertEquals(namesOf(order).toString(), 2, order.items().size());
        assertEquals(1, order.items().get(0).quantity());
        assertEquals(750L, order.itemsSubtotalCents());
    }
}
