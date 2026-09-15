package com.householdsplitter.core.quality;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.parse.Page;
import com.householdsplitter.core.parse.amazon.AmazonFreshLayoutParser;
import com.householdsplitter.core.parse.layout.ParseTrace;
import com.householdsplitter.core.parse.model.OcrElement;
import com.householdsplitter.core.parse.model.ParsedItem;
import com.householdsplitter.core.parse.model.ParsedOrder;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * Diagnosing a missed row against a real Amazon Fresh page.
 *
 * <p>The page is the same traced capture the parser tests use, so these are answers about
 * the reader that actually ships rather than about a model of it.
 */
public class MissDiagnosisTest {

    private static final int W = 921;
    private static final int H = 2048;

    /** Two rows in the item column, plus whatever the caller adds. */
    private static Page page() {
        Page page = Page.sized(0, W, H);
        page.at(169, 220, 614, "Search or ask a question", 36, 93);
        page.at(291, 37, 478, "Items in your order (2)", 37, 97);

        page.at(393, 165, 615, "Amazon Grocery, Broccoli Florets,", 26, 96);
        page.at(393, 809, 883, "$1.16", 26, 97);
        page.at(438, 165, 354, "12 Oz, Frozen", 26, 96);
        page.at(483, 165, 242, "Qty: 1", 25, 95);

        page.at(600, 165, 490, "Banana Bunch (4-5 Count)", 26, 96);
        page.at(600, 809, 883, "$0.99", 26, 97);
        page.at(645, 165, 242, "Qty: 1", 25, 95);
        return page;
    }

    private static ParseTrace traceOf(Page page) {
        ParseTrace trace = new ParseTrace();
        new AmazonFreshLayoutParser().parse(
                Collections.<List<OcrElement>>singletonList(page.elements()), trace);
        return trace;
    }

    @Test
    public void aRowThatWasNeverOnAScreenshotIsNotTheReadersFault() {
        // The single most important distinction here. A cash item, a page nobody captured,
        // or a row typed in from memory is a manual add and not a parser miss, and counting
        // it as one would turn the measurement into a record of how people use the app.
        MissDiagnosis.Result result =
                MissDiagnosis.diagnose(traceOf(page()), "Bottle of olive oil");

        assertEquals(MissDiagnosis.Verdict.NOT_ON_ANY_PAGE, result.verdict);
        assertFalse("and it must not be counted against the reader",
                result.verdict.isParserFault());
    }

    @Test
    public void aRowSwallowedByAChromePatternIsNamedAsSuch() {
        // The failure worth catching most: a chrome pattern eating a real product name.
        // "View related transactions" is on Amazon's chrome list, so a product with that
        // name would vanish without trace. Driving it through the app's own vocabulary is
        // what proves the diagnosis reads the reader's real decision rather than making
        // its own.
        Page page = page();
        page.at(760, 165, 520, "View related transactions", 26, 96);

        MissDiagnosis.Result result =
                MissDiagnosis.diagnose(traceOf(page), "View related transactions");

        assertEquals(MissDiagnosis.Verdict.FILTERED_AS_CHROME, result.verdict);
        assertTrue(result.verdict.isParserFault());
        assertNotNull(result.matchedText);
    }

    @Test
    public void aRowReadAsASectionHeaderIsNamedAsSuch() {
        // A different bug from chrome and a different fix, so it gets its own verdict.
        // Amazon's "Items in your order (2)" is a section header, and a product that
        // happened to match that shape would be turned into a heading.
        MissDiagnosis.Result result =
                MissDiagnosis.diagnose(traceOf(page()), "Items in your order");

        assertEquals(MissDiagnosis.Verdict.READ_AS_A_SECTION_HEADER, result.verdict);
    }

    @Test
    public void aNameWithNoPriceBesideItSaysSo() {
        Page page = page();
        // A product line with nothing in the price column, which is what a row looks like
        // when the amount failed to recognise or sat outside the column.
        page.at(760, 165, 520, "Amazon Grocery, Whole Milk Plain Yogurt", 26, 96);

        MissDiagnosis.Result result = MissDiagnosis.diagnose(traceOf(page),
                "Amazon Grocery, Whole Milk Plain Yogurt, 32 Oz");

        assertEquals(MissDiagnosis.Verdict.NO_PRICE_IN_THE_COLUMN, result.verdict);
    }

    @Test
    public void aRowClippedByTheNavigationBarIsNamedAsCropped() {
        Page page = page();
        // Amazon pins a five-icon bar over the foot of the page, so the bottom crop is deep.
        // A row that ran under it is lost before any vocabulary sees it, and that is a
        // different fix from a wrong pattern: capture the page differently, or crop less.
        page.at(1980, 165, 520, "Amazon Grocery, Russet Potatoes", 26, 96);
        page.at(1980, 809, 883, "$3.76", 26, 97);

        MissDiagnosis.Result result =
                MissDiagnosis.diagnose(traceOf(page), "Amazon Grocery, Russet Potatoes, 5 Lb");

        assertEquals(MissDiagnosis.Verdict.CROPPED_AT_THE_MARGIN, result.verdict);
    }

    @Test
    public void aRowTheReaderActuallyFoundIsNotReportedAsMissed() {
        // The guard against the diagnosis inventing work. These two rows parse correctly,
        // so if one is ever typed in by hand the honest verdict is that the reader had
        // every chance and the row is unexplained, not that a stage lost it.
        ParsedOrder order = new AmazonFreshLayoutParser().parse(
                Collections.<List<OcrElement>>singletonList(page().elements()));
        assertEquals(2, order.items().size());

        MissDiagnosis.Result result =
                MissDiagnosis.diagnose(traceOf(page()), "Banana Bunch (4-5 Count)");
        assertEquals(MissDiagnosis.Verdict.UNEXPLAINED, result.verdict);
    }

    @Test
    public void aShortenedNameStillFindsItsRow() {
        // People type "broccoli florets", not the full catalogue title. Matching asks how
        // much of the band the typed name accounts for, so a partial name still lands.
        MissDiagnosis.Result result =
                MissDiagnosis.diagnose(traceOf(page()), "Broccoli Florets");

        assertTrue("a shortened name is still the same row",
                result.verdict != MissDiagnosis.Verdict.NOT_ON_ANY_PAGE);
    }

    @Test
    public void oneSharedWordIsNotAMatch() {
        // "Amazon Grocery" prefixes most of the catalogue. If that alone counted as a
        // match, every hand-typed row would be blamed on whichever Amazon row came first.
        MissDiagnosis.Result result =
                MissDiagnosis.diagnose(traceOf(page()), "Amazon");

        assertEquals(MissDiagnosis.Verdict.NOT_ON_ANY_PAGE, result.verdict);
    }

    @Test
    public void everyRowTheReaderFoundDiagnosesAsUnexplained() {
        // Sanity on the whole page at once. Every row here parsed, so none of them has a
        // stage that lost it, and the diagnosis must not manufacture one. A verdict of
        // NOT_ON_ANY_PAGE here would mean the matcher cannot find rows that are plainly
        // present, which would make every real diagnosis untrustworthy.
        ParsedOrder order = new AmazonFreshLayoutParser().parse(
                Collections.<List<OcrElement>>singletonList(page().elements()));
        ParseTrace trace = traceOf(page());

        assertEquals(2, order.items().size());
        for (ParsedItem item : order.items()) {
            MissDiagnosis.Result result = MissDiagnosis.diagnose(trace, item.name());
            assertEquals(item.name(), MissDiagnosis.Verdict.UNEXPLAINED, result.verdict);
            assertNotNull(item.name(), result.matchedText);
        }
    }
}
