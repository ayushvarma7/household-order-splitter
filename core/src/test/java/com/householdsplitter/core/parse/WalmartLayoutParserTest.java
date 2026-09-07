package com.householdsplitter.core.parse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.parse.model.OcrElement;
import com.householdsplitter.core.parse.model.ParsedItem;
import com.householdsplitter.core.parse.model.ParsedOrder;
import com.householdsplitter.core.parse.model.Reconciliation;
import com.householdsplitter.core.parse.model.ReviewReason;
import com.householdsplitter.core.parse.walmart.WalmartLayoutParser;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** SPEC 12.4, run against captured fixture element lists. */
public class WalmartLayoutParserTest {

    private final WalmartLayoutParser parser = new WalmartLayoutParser();

    private ParsedOrder parse(Page... pages) {
        List<List<OcrElement>> input = new ArrayList<>();
        for (Page page : pages) {
            input.add(page.elements());
        }
        return parser.parse(input);
    }

    /** 12.4.1: a four-line wrapped name with a right-aligned price is ONE item. */
    @Test
    public void fourLineWrappedNameBecomesOneItem() {
        Page page = Page.image(0)
                .appBar("Sep 03, 2026 order")
                .section("16 shopped")
                .itemStart("Great Value Triple Cheddar", "$4.34")
                .line("Finely Shredded Cheese,")
                .line("8 oz Bag")
                .line("Qty 1")
                .chrome("+ Add")
                .chrome("Review item")
                .summary("Subtotal", "$4.34")
                .summary("Total", "$4.34");

        ParsedOrder order = parse(page);

        assertEquals(1, order.items().size());
        ParsedItem item = order.items().get(0);
        assertEquals("Great Value Triple Cheddar Finely Shredded Cheese, 8 oz Bag", item.name());
        assertEquals(434L, item.lineTotalCents());
        assertEquals(1, item.quantity());
    }

    /** 12.4.2: a unit price is captured for display and never charged (SPEC 8.3.3). */
    @Test
    public void unitPriceIsNotTheLineTotal() {
        Page page = Page.image(0)
                .section("16 shopped")
                .itemStart("Fresh Bananas, each", "$1.97")
                .line("$3.94/lb")
                .chrome("+ Add")
                .summary("Total", "$1.97");

        ParsedOrder order = parse(page);

        assertEquals(1, order.items().size());
        ParsedItem item = order.items().get(0);
        assertEquals(197L, item.lineTotalCents());
        assertEquals("$3.94/lb", item.unitPriceText());
        assertFalse(item.name().contains("3.94"));
    }

    /**
     * A unit price is never the billed amount, even when it sits on the same row. The rate
     * on the left describes what a pound costs; the amount on the right is what was charged.
     */
    @Test
    public void unitPriceOnTheSameBandIsStillNotTheLineTotal() {
        Page page = Page.image(0)
                .section("16 shopped")
                .at(500, 180, 560, "Fresh Bananas")
                .at(500, 600, 700, "$3.94/lb")
                .at(500, 900, 1040, "$1.97")
                .cursorY(580)
                .summary("Total", "$1.97");

        ParsedOrder order = parse(page);

        assertEquals(1, order.items().size());
        assertEquals(197L, order.items().get(0).lineTotalCents());
        assertEquals("$3.94/lb", order.items().get(0).unitPriceText());
    }

    /** 12.4.3: metadata lines are stripped from the name (SPEC 8.3.7, 8.3.8). */
    @Test
    public void quantityAndMultipackLinesAreStrippedFromTheName() {
        Page page = Page.image(0)
                .section("16 shopped")
                .itemStart("Marketside Butter Croissants,", "$4.98")
                .line("8 oz")
                .line("Qty 1")
                .line("Multipack Quantity: 1")
                .chrome("Review item")
                .summary("Total", "$4.98");

        ParsedOrder order = parse(page);

        ParsedItem item = order.items().get(0);
        assertEquals("Marketside Butter Croissants, 8 oz", item.name());
        assertFalse(item.name().toLowerCase().contains("qty"));
        assertFalse(item.name().toLowerCase().contains("multipack"));
        assertEquals(1, item.quantity());
        // SPEC 5.6: the original survives for debugging even after the strip.
        assertTrue(item.rawOcrText().contains("Qty 1"));
    }

    /** Quantity above one is read and flagged for review (SPEC 8.3.8, 8.8.1). */
    @Test
    public void quantityAboveOneIsReadAndFlagged() {
        Page page = Page.image(0)
                .section("16 shopped")
                .itemStart("Great Value Whole Milk", "$7.88")
                .line("Qty 2")
                .chrome("+ Add")
                .summary("Total", "$7.88");

        ParsedItem item = parse(page).items().get(0);
        assertEquals(2, item.quantity());
        assertTrue(item.needsReview());
        assertTrue(item.reviewReasons().contains(ReviewReason.QUANTITY_ABOVE_ONE));
    }

    /** 12.4.4: buttons and links never reach an item name (SPEC 8.4.1). */
    @Test
    public void chromeNeverAppearsInAnyName() {
        Page page = Page.image(0)
                .section("16 shopped")
                .itemStart("Bananas", "$1.97")
                .chrome("+ Add")
                .chrome("Review item")
                .itemStart("Apples", "$3.12")
                .chrome("+ Add")
                .chrome("Review item")
                .summary("Total", "$5.09");

        ParsedOrder order = parse(page);

        assertEquals(2, order.items().size());
        for (ParsedItem item : order.items()) {
            assertFalse(item.name(), item.name().toLowerCase().contains("add"));
            assertFalse(item.name(), item.name().toLowerCase().contains("review item"));
        }
        assertEquals("Bananas", order.items().get(0).name());
        assertEquals("Apples", order.items().get(1).name());
    }

    /**
     * 12.4.5: a struck $9.95 beside a charged $0 resolves to zero (SPEC 8.6.4).
     *
     * <p>And it does so silently. The right-most amount on the band is the charged figure by
     * definition, so there is nothing exceptional to report.
     */
    @Test
    public void struckThroughDeliveryPriceResolvesToTheChargedAmount() {
        Page page = Page.image(0)
                .section("16 shopped")
                .item("Bananas", "$53.52")
                .summary("Subtotal", "$53.52")
                .summary("Free delivery from store", "$9.95", "$0")
                .summary("Taxes", "$0.50")
                .summary("Driver tip", "$0.00")
                .summary("Total", "$54.02");

        ParsedOrder order = parse(page);

        assertEquals(0L, order.adjustments().deliveryFeeCents());
        assertEquals(5352L, order.adjustments().statedSubtotalCents());
        assertEquals(50L, order.adjustments().taxCents());
        assertEquals(5402L, order.adjustments().statedTotalCents());
        assertTrue("taking the charged amount is the rule, not an exception to report",
                order.warnings().isEmpty());
    }

    /** 12.4.6: Subtotal is not matched by the Total rule (SPEC 8.6.8). */
    @Test
    public void subtotalIsNotMistakenForTotal() {
        Page page = Page.image(0)
                .section("16 shopped")
                .item("Bananas", "$53.52")
                .summary("Subtotal", "$53.52")
                .summary("Taxes", "$0.50")
                .summary("Total", "$54.02");

        ParsedOrder order = parse(page);

        assertEquals(5352L, order.adjustments().statedSubtotalCents());
        assertEquals(5402L, order.adjustments().statedTotalCents());
    }

    /** 12.4.7: a rating carousel card emits no item (SPEC 8.4.8). */
    @Test
    public void ratingCarouselEmitsNoItems() {
        Page page = Page.image(0)
                .appBar("Sep 03, 2026 order")
                .carouselCard("Fresh Banana, Each")
                .carouselCard("Great Value 2% Milk")
                .section("16 shopped")
                .itemStart("Bananas", "$1.97")
                .chrome("+ Add")
                .summary("Total", "$1.97");

        ParsedOrder order = parse(page);

        assertEquals(1, order.items().size());
        assertEquals("Bananas", order.items().get(0).name());
        for (ParsedItem item : order.items()) {
            assertFalse(item.name().toLowerCase().contains("fresh banana, each"));
        }
    }

    /**
     * 12.4.8: "33 items delivered" is a unit count, not a row count. A parse that produced
     * 18 rows is correct and must not be failed against it (SPEC 8.5.4).
     */
    @Test
    public void unitCountHeaderDoesNotValidateRowCount() {
        // A screenshot long enough to hold all eighteen rows; a real order this size
        // would be several images and stitched by SPEC 8.7.
        Page page = Page.tallImage(0, 3200)
                .appBar("Sep 03, 2026 order")
                .section("33 items delivered")
                .section("18 shopped");
        long expectedTotal = 0L;
        for (int i = 1; i <= 18; i++) {
            page.itemStart("Item number " + i, "$1.0" + (i % 10));
            page.chrome("+ Add");
            expectedTotal += 100L + (i % 10);
        }
        page.summary("Subtotal", "$" + (expectedTotal / 100) + "." + String.format("%02d", expectedTotal % 100));
        page.summary("Total", "$" + (expectedTotal / 100) + "." + String.format("%02d", expectedTotal % 100));

        ParsedOrder order = parse(page);

        assertEquals(18, order.items().size());
        Reconciliation reconciliation = Reconciler.reconcile(order);
        assertTrue("reconciliation is on money, never on the item count",
                reconciliation.subtotalMatches());
        assertTrue(reconciliation.totalMatches());
    }

    /** 12.4.9: a header repeated across two screenshots opens one section (SPEC 8.7.6). */
    @Test
    public void repeatedSectionHeaderDoesNotRestartTheSection() {
        Page first = Page.image(0)
                .appBar("Sep 03, 2026 order")
                .section("33 items delivered")
                .section("16 shopped")
                .itemStart("Bananas", "$1.97")
                .chrome("+ Add");
        Page second = Page.image(1)
                .section("16 shopped")
                .itemStart("Apples", "$3.12")
                .chrome("+ Add")
                .summary("Total", "$5.09");

        ParsedOrder order = parse(first, second);

        assertEquals(Arrays.asList("items delivered", "shopped"), order.sections());
        assertEquals(2, order.items().size());
    }

    /** 12.4.10, first half: an identical row at the boundary is one row (SPEC 8.7.3). */
    @Test
    public void identicalRowAcrossTheBoundaryIsDeduplicated() {
        Page first = Page.image(0)
                .section("16 shopped")
                .itemStart("Bananas", "$1.97")
                .chrome("+ Add")
                .itemStart("Whole Milk", "$3.42")
                .chrome("+ Add");
        Page second = Page.image(1)
                .itemStart("Whole Milk", "$3.42")
                .chrome("+ Add")
                .itemStart("Bread", "$2.50")
                .chrome("+ Add")
                .summary("Total", "$7.89");

        ParsedOrder order = parse(first, second);

        assertEquals(3, order.items().size());
        assertEquals("Bananas", order.items().get(0).name());
        assertEquals("Whole Milk", order.items().get(1).name());
        assertEquals("Bread", order.items().get(2).name());
    }

    /**
     * 12.4.10, second half: two identical rows separated by other items are two genuine
     * purchases and must both survive (SPEC 8.7.4).
     */
    @Test
    public void identicalRowsSeparatedByOthersAreBothKept() {
        Page page = Page.image(0)
                .section("16 shopped")
                .itemStart("Whole Milk", "$3.42")
                .chrome("+ Add")
                .itemStart("Bananas", "$1.97")
                .chrome("+ Add")
                .itemStart("Bread", "$2.50")
                .chrome("+ Add")
                .itemStart("Whole Milk", "$3.42")
                .chrome("+ Add")
                .summary("Total", "$11.31");

        ParsedOrder order = parse(page);

        assertEquals(4, order.items().size());
        assertEquals("Whole Milk", order.items().get(0).name());
        assertEquals("Whole Milk", order.items().get(3).name());
    }

    /** Two identical rows one after another in the same screenshot are also both real. */
    @Test
    public void adjacentIdenticalRowsInOneScreenshotAreBothKept() {
        Page page = Page.image(0)
                .section("16 shopped")
                .itemStart("Whole Milk", "$3.42")
                .chrome("+ Add")
                .itemStart("Whole Milk", "$3.42")
                .chrome("+ Add")
                .summary("Total", "$6.84");

        assertEquals(2, parse(page).items().size());
    }

    /** 12.4.11: a screen-recording timer is neither a price nor a name (SPEC 8.4.7). */
    @Test
    public void recordingTimerIsNotReadAsPriceOrName() {
        Page page = Page.image(0)
                .statusBar("02:53", "87%")
                .appBar("Sep 03, 2026 order")
                .section("16 shopped")
                .itemStart("Bananas", "$1.97")
                .chrome("+ Add")
                .summary("Total", "$1.97");

        ParsedOrder order = parse(page);

        assertEquals(1, order.items().size());
        for (ParsedItem item : order.items()) {
            assertFalse(item.name().contains("02:53"));
            assertFalse(item.name().contains("87%"));
            assertFalse(item.lineTotalCents() == 253L);
        }
    }

    /** A timer that survives the crop is still recognised as chrome. */
    @Test
    public void timerInsideTheContentAreaIsStillChrome() {
        Page page = Page.image(0)
                .section("16 shopped")
                .chrome("02:53")
                .itemStart("Bananas", "$1.97")
                .chrome("+ Add")
                .summary("Total", "$1.97");

        ParsedOrder order = parse(page);
        assertEquals(1, order.items().size());
        assertEquals("Bananas", order.items().get(0).name());
    }

    /** 12.4.12: the order number is captured and excluded (SPEC 8.4.5, 8.6.6). */
    @Test
    public void orderNumberIsCapturedAndExcludedFromItems() {
        Page page = Page.image(0)
                .section("16 shopped")
                .itemStart("Bananas", "$1.97")
                .chrome("+ Add")
                .summary("Total", "$1.97")
                .chrome("Charge history")
                .line("Order# 200011234567890-1");

        ParsedOrder order = parse(page);

        assertEquals("200011234567890-1", order.externalOrderNo());
        assertEquals(1, order.items().size());
        for (ParsedItem item : order.items()) {
            assertFalse(item.name().toLowerCase().contains("order#"));
        }
    }

    /** SPEC 8.6.1 and 8.6.2. */
    @Test
    public void orderDateAndDefaultLabelComeFromTheAppBar() {
        Page page = Page.image(0)
                .appBar("Sep 03, 2026 order")
                .section("16 shopped")
                .itemStart("Bananas", "$1.97")
                .summary("Total", "$1.97");

        ParsedOrder order = parse(page);

        assertNotNull(order.orderDateMillis());
        assertEquals("Sep 03 Walmart", order.label());
    }

    /** SPEC 8.5.3: unavailable rows default to EXCLUDED but stay visible. */
    @Test
    public void unavailableSectionDefaultsToExcluded() {
        Page page = Page.image(0)
                .section("16 shopped")
                .itemStart("Bananas", "$1.97")
                .chrome("+ Add")
                .section("1 unavailable")
                .itemStart("Sourdough Loaf", "$4.50")
                .chrome("+ Add")
                .summary("Total", "$1.97");

        ParsedOrder order = parse(page);

        assertEquals(2, order.items().size());
        assertEquals(Scope.UNASSIGNED, order.items().get(0).scope());
        assertEquals(Scope.EXCLUDED, order.items().get(1).scope());
        assertEquals("unavailable", order.items().get(1).sourceSection());
        assertTrue(order.items().get(1).reviewReasons().contains(ReviewReason.EXCLUDED_SECTION));
        // SPEC 8.9.1: excluded rows are outside the subtotal.
        assertEquals(197L, order.itemsSubtotalCents());
    }

    /** SPEC 8.7.5: an edge fragment never becomes a nameless row. */
    @Test
    public void edgeFragmentDoesNotBecomeANamelessItem() {
        Page first = Page.image(0)
                .section("16 shopped")
                .itemStart("Bananas", "$1.97")
                .chrome("+ Add")
                .line("Great Value Triple Cheddar");
        Page second = Page.image(1)
                .line("Qty 1")
                .itemStart("Whole Milk", "$3.42")
                .chrome("+ Add")
                .summary("Total", "$5.39");

        ParsedOrder order = parse(first, second);

        for (ParsedItem item : order.items()) {
            assertFalse("no nameless rows", item.name().trim().isEmpty());
        }
        assertEquals(2, order.items().size());
    }

    /**
     * A price in the middle of the page whose name failed to recognise is kept, not dropped.
     *
     * <p>Dropping it would quietly lose a charge: the totals would come out short with
     * nothing to point at. It is kept, flagged, and SPEC 7.6.9 then refuses to leave the
     * review screen until the user has typed what it was.
     */
    @Test
    public void aPriceWithNoNameInTheMiddleOfThePageIsKeptAndFlagged() {
        Page page = Page.image(0)
                .section("16 shopped")
                .itemStart("Bananas", "$1.97")
                .chrome("+ Add")
                // A row where only the price recognised.
                .at(900, 900, 1040, "$7.45")
                .cursorY(1000)
                .chrome("+ Add")
                .itemStart("Bread", "$2.50")
                .chrome("+ Add")
                .summary("Total", "$11.92");

        ParsedOrder order = parse(page);

        assertEquals(3, order.items().size());
        ParsedItem unread = order.items().get(1);
        assertEquals(745L, unread.lineTotalCents());
        assertTrue(unread.needsReview());
        assertTrue(unread.reviewReasons().contains(ReviewReason.NAME_NOT_READ));
        // Nothing is lost: the subtotal still accounts for every charge on the page.
        assertEquals(197L + 745L + 250L, order.itemsSubtotalCents());
    }

    /** At a screenshot edge the same thing is a cut-off fragment, and is dropped (SPEC 8.7.5). */
    @Test
    public void aPriceWithNoNameAtTheEdgeIsStillDropped() {
        Page first = Page.image(0)
                .section("16 shopped")
                .itemStart("Bananas", "$1.97")
                .chrome("+ Add");
        // A price right at the bottom edge, its name cut away with the rest of the row.
        first.at(2270, 900, 1040, "$7.45");

        Page second = Page.image(1)
                .itemStart("Bread", "$2.50")
                .chrome("+ Add")
                .summary("Total", "$4.47");

        ParsedOrder order = parse(first, second);

        for (ParsedItem item : order.items()) {
            assertFalse(item.lineTotalCents() == 745L);
        }
        assertEquals(2, order.items().size());
    }

    /** SPEC 8.9.4: the worked green-tick example from a real order. */
    @Test
    public void realOrderReconcilesToTheGreenTick() {
        Page page = Page.image(0)
                .appBar("Sep 03, 2026 order")
                .section("16 shopped")
                .itemStart("Everything else", "$53.52")
                .chrome("+ Add")
                .summary("Subtotal", "$53.52")
                .summary("Free delivery from store", "$9.95", "$0")
                .summary("Taxes", "$0.50")
                .summary("Driver tip", "$0.00")
                .summary("Total", "$54.02");

        ParsedOrder order = parse(page);
        Reconciliation reconciliation = Reconciler.reconcile(order);

        assertEquals(5402L, reconciliation.computedTotalCents());
        assertEquals(5402L, reconciliation.statedTotalCents());
        assertTrue(reconciliation.totalMatches());
        assertTrue(reconciliation.subtotalMatches());
        assertTrue(reconciliation.allMatched());
    }

    /** SPEC 11.11: nothing to parse is an empty list, not a crash. */
    @Test
    public void emptyInputProducesAnEmptyOrder() {
        assertEquals(0, parser.parse(new ArrayList<>()).items().size());
        assertEquals(0, parse(Page.image(0)).items().size());
        assertNull(parse(Page.image(0)).externalOrderNo());
    }

    /** SPEC 8.7.7: a later screenshot wins a disagreement, and the conflict is flagged. */
    @Test
    public void conflictingSummaryPrefersTheLaterScreenshotAndWarns() {
        Page first = Page.image(0)
                .section("16 shopped")
                .itemStart("Bananas", "$1.97")
                .summary("Total", "$54.02");
        Page second = Page.image(1)
                .summary("Total", "$54.55");

        ParsedOrder order = parse(first, second);

        assertEquals(5455L, order.adjustments().statedTotalCents());
        boolean warned = false;
        for (String warning : order.warnings()) {
            warned |= warning.contains("disagree");
        }
        assertTrue("the conflict must be surfaced", warned);
    }
}
