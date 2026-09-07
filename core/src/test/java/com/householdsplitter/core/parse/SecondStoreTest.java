package com.householdsplitter.core.parse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.calc.AllocationMode;
import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.calc.SplitCalculator;
import com.householdsplitter.core.calc.input.Adjustments;
import com.householdsplitter.core.calc.input.CalcLineItem;
import com.householdsplitter.core.calc.input.CalcMember;
import com.householdsplitter.core.calc.input.CalcOrder;
import com.householdsplitter.core.calc.result.SplitResult;
import com.householdsplitter.core.parse.model.OcrElement;
import com.householdsplitter.core.parse.model.ParsedAdjustments;
import com.householdsplitter.core.parse.model.ParsedItem;
import com.householdsplitter.core.parse.model.ParsedOrder;
import com.householdsplitter.core.parse.walmart.WalmartLayoutParser;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SPEC 3.5: a second store must be addable without changing the domain layer.
 *
 * <p>That is an architectural claim, and the only way to check a claim like that is to try
 * it. This test implements a fictional second store whose pages look nothing like Walmart's
 *, prices on the left, a footer instead of a header, a different currency style, and runs
 * its output through the real {@link SplitCalculator}.
 *
 * <p>What makes the test meaningful is what it does not contain: no cast, no branch on which
 * store this is, and no new type. It uses {@link LayoutParser}, {@link ParsedOrder} and
 * {@link SplitCalculator} exactly as the Walmart path does. If someone later pushes
 * store-specific knowledge down into the domain layer, this stops compiling or stops passing.
 *
 * <p>The store here is invented for the test and is not shipped. SPEC 3.5 rules out a real
 * second store in v1, and guessing at a layout without real screenshots to check against is
 * how a parser ends up silently wrong about money.
 */
public class SecondStoreTest {

    /**
     * A deliberately un-Walmart-like layout. Each line reads
     * {@code 12.99 | Item name}, so the price is the first token rather than the rightmost,
     * and the order total sits in a footer rather than a header.
     */
    private static final class LeftPriceLayoutParser implements LayoutParser {

        @Override
        public String storeName() {
            return "Test Grocer";
        }

        @Override
        public ParsedOrder parse(List<List<OcrElement>> pages) {
            List<ParsedItem> items = new ArrayList<>();
            long statedTotal = 0L;
            long tax = 0L;

            for (List<OcrElement> page : pages) {
                for (OcrElement element : page) {
                    String text = element.text();
                    if (text.startsWith("TOTAL ")) {
                        statedTotal = cents(text.substring(6));
                        continue;
                    }
                    if (text.startsWith("TAX ")) {
                        tax = cents(text.substring(4));
                        continue;
                    }
                    int split = text.indexOf(" | ");
                    if (split < 0) {
                        continue;
                    }
                    items.add(ParsedItem.builder()
                            .name(text.substring(split + 3))
                            .rawOcrText(text)
                            .lineTotalCents(cents(text.substring(0, split)))
                            .scope(Scope.UNASSIGNED)
                            .build());
                }
            }
            long subtotal = 0L;
            for (ParsedItem item : items) {
                subtotal += item.lineTotalCents();
            }
            return ParsedOrder.builder()
                    .items(items)
                    .adjustments(new ParsedAdjustments(subtotal, tax, 0L, 0L, 0L, 0L,
                            statedTotal))
                    .build();
        }

        /** Money as long cents, per SPEC 6.1. No float anywhere, second store or not. */
        private static long cents(String printed) {
            String digits = printed.trim().replace("$", "").replace(",", "");
            int dot = digits.indexOf('.');
            if (dot < 0) {
                return Long.parseLong(digits) * 100L;
            }
            long whole = Long.parseLong(digits.substring(0, dot));
            long fraction = Long.parseLong((digits.substring(dot + 1) + "00").substring(0, 2));
            return whole * 100L + fraction;
        }
    }

    private static OcrElement line(String text, int top) {
        return new OcrElement(text, 0, 40, top, 1000, top + 40, 1080, 2400, 95);
    }

    private static List<List<OcrElement>> onePage(String... lines) {
        List<OcrElement> elements = new ArrayList<>();
        int top = 200;
        for (String text : lines) {
            elements.add(line(text, top));
            top += 60;
        }
        return Arrays.asList(elements);
    }

    /** The interface holds: a completely different layout produces the same shape of result. */
    @Test
    public void aSecondStoreProducesAnOrdinaryParsedOrder() {
        LayoutParser parser = new LeftPriceLayoutParser();

        ParsedOrder parsed = parser.parse(onePage(
                "12.99 | Coffee beans",
                "3.42 | Whole milk",
                "5.00 | Sourdough loaf",
                "TAX 1.29",
                "TOTAL 22.70"));

        assertEquals("Test Grocer", parser.storeName());
        assertEquals(3, parsed.items().size());
        assertEquals("Coffee beans", parsed.items().get(0).name());
        assertEquals(1299L, parsed.items().get(0).lineTotalCents());
        assertEquals(2141L, parsed.itemsSubtotalCents());
        assertEquals(129L, parsed.adjustments().taxCents());
        assertEquals(2270L, parsed.adjustments().statedTotalCents());
    }

    /**
     * The claim itself. The split of a second store's order goes through the same calculator,
     * with no store-specific code anywhere below the parser.
     */
    @Test
    public void theDomainLayerSplitsASecondStoresOrderUnchanged() {
        ParsedOrder parsed = new LeftPriceLayoutParser().parse(onePage(
                "12.99 | Coffee beans",
                "3.42 | Whole milk",
                "5.00 | Sourdough loaf",
                "TAX 1.29",
                "TOTAL 22.70"));

        // Exactly the conversion the Walmart path does: items in, scopes chosen by the user.
        List<CalcLineItem> items = Arrays.asList(
                CalcLineItem.common(1L, parsed.items().get(0).name(),
                        parsed.items().get(0).lineTotalCents()),
                CalcLineItem.personal(2L, parsed.items().get(1).name(),
                        parsed.items().get(1).lineTotalCents(), 10L),
                CalcLineItem.common(3L, parsed.items().get(2).name(),
                        parsed.items().get(2).lineTotalCents()));

        SplitResult result = SplitCalculator.calculate(new CalcOrder(
                Arrays.asList(new CalcMember(10L, "Ana", 0), new CalcMember(20L, "Ben", 1)),
                items,
                new Adjustments(parsed.adjustments().taxCents(), 0L, 0L, 0L, 0L),
                parsed.adjustments().statedTotalCents(),
                AllocationMode.PROPORTIONAL));

        // 12.99 + 5.00 shared, 3.42 to Ana, 1.29 tax spread proportionally.
        assertEquals(1799L, result.commonBucketCents());
        assertEquals(2141L, result.itemSubtotalCents());
        assertEquals(2270L, result.computedTotalCents());
        assertTrue("the second store's own total reconciles", result.matchesStatedTotal());
        assertFalse(result.equalFallbackUsed());

        long summed = result.member(10L).finalCents() + result.member(20L).finalCents();
        assertEquals("every cent is accounted for, whichever store it came from",
                2270L, summed);
        assertTrue(result.member(10L).finalCents() > result.member(20L).finalCents());
    }

    /**
     * Both stores satisfy the same contract, which is what "without changing the domain
     * layer" has to mean in practice: the caller holds the interface, not an implementation.
     */
    @Test
    public void bothStoresAreInterchangeableThroughTheInterface() {
        List<LayoutParser> parsers = Arrays.asList(
                new WalmartLayoutParser(), new LeftPriceLayoutParser());

        for (LayoutParser parser : parsers) {
            assertFalse("every store names itself", parser.storeName().isEmpty());
            // An empty page is a valid result, never an exception (SPEC 7.5.3 handles it).
            ParsedOrder empty = parser.parse(new ArrayList<>());
            assertTrue(parser.storeName(), empty.items().isEmpty());
        }
    }
}
