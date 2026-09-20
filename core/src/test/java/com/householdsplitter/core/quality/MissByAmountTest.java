package com.householdsplitter.core.quality;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.parse.StoreKind;
import com.householdsplitter.core.parse.layout.ParseTrace;
import com.householdsplitter.core.parse.layout.ReceiptLayoutParser;
import com.householdsplitter.core.parse.model.OcrElement;
import com.householdsplitter.core.parse.model.ParsedOrder;
import com.householdsplitter.core.parse.restaurant.Bill;

import org.junit.Test;

import java.util.List;

/**
 * Diagnosing a missed row from the shortfall rather than from a typed name.
 *
 * <p>This is the property a photographed bill has and a screenshot does not. The bill
 * states its own subtotal, so when the rows fall short the app knows the exact value of
 * what it lost, and an exact value is evidence where a name is only a resemblance.
 */
public class MissByAmountTest {

    private static ParsedOrder parse(List<List<OcrElement>> pages, ParseTrace trace) {
        ReceiptLayoutParser parser =
                (ReceiptLayoutParser) StoreKind.RESTAURANT.layoutParser();
        return parser.parse(pages, trace);
    }

    /**
     * A bill whose subtotal includes a dish the reader loses, because the till printed the
     * word TOTAL in front of it and the vocabulary reads that as the end of the food.
     */
    private static List<List<OcrElement>> billWithALostRow() {
        return Bill.paper()
                .leftLine("QTY DESC AMT")
                .item(1, "MARGHERITA", "$16.00")
                .item(1, "GARLIC BREAD", "$7.95")
                .summary("SUBTOTAL", "$23.95")
                .pages();
    }

    @Test
    public void theShortfallNamesTheStageThatLostTheRow() {
        ParseTrace trace = new ParseTrace();
        ParsedOrder order = parse(Bill.paper()
                .leftLine("QTY DESC AMT")
                .item(1, "MARGHERITA", "$16.00")
                // A dish the chrome list swallows: "CASH" opens the payment pattern, and a
                // dish called "CASHEW SALAD" is close enough to be worth proving is safe.
                .line("THANK YOU")
                .summary("SUBTOTAL", "$23.95")
                .pages(), trace);

        long shortfall = order.adjustments().statedSubtotalCents() - order.itemsSubtotalCents();
        assertEquals("the bill should be short by the missing dish", 795L, shortfall);

        MissDiagnosis.Result result = MissDiagnosis.byAmount(trace, shortfall);
        assertEquals(MissDiagnosis.Verdict.NOT_ON_ANY_PAGE, result.verdict);
    }

    @Test
    public void anAmountReadAsASummaryLineIsNamedAsSuch() {
        ParseTrace trace = new ParseTrace();
        parse(Bill.paper()
                .leftLine("QTY DESC AMT")
                .item(1, "MARGHERITA", "$16.00")
                // A till that prints a dish as "SERVICE" would have it read as a fee.
                .summary("SERVICE CHARGE", "$7.95")
                .summary("SUBTOTAL", "$23.95")
                .pages(), trace);

        MissDiagnosis.Result result = MissDiagnosis.byAmount(trace, 795L);
        assertEquals(MissDiagnosis.Verdict.READ_AS_A_SUMMARY_LINE, result.verdict);
        assertNotNull(result.matchedText);
        assertTrue(result.matchedText, result.matchedText.contains("7.95"));
    }

    @Test
    public void anAmountBelowWhereTheItemsStopIsNamedAsSuch() {
        ParseTrace trace = new ParseTrace();
        parse(Bill.paper()
                .leftLine("QTY DESC AMT")
                .item(1, "MARGHERITA", "$16.00")
                .summary("SUBTOTAL", "$16.00")
                .item(1, "LATE ADDITION", "$7.95")
                .pages(), trace);

        assertEquals(MissDiagnosis.Verdict.PAST_THE_ITEM_REGION,
                MissDiagnosis.byAmount(trace, 795L).verdict);
    }

    @Test
    public void anAmountThatWasReadCorrectlyIsNotBlamedOnTheReader() {
        ParseTrace trace = new ParseTrace();
        parse(billWithALostRow(), trace);
        // 7.95 is on the page and did become a row, so nothing upstream lost it.
        assertEquals(MissDiagnosis.Verdict.UNEXPLAINED,
                MissDiagnosis.byAmount(trace, 795L).verdict);
    }

    /** The guard that stops 7.95 matching inside 17.95. */
    @Test
    public void aShortfallDoesNotMatchInsideALargerAmount() {
        ParseTrace trace = new ParseTrace();
        parse(Bill.paper()
                .leftLine("QTY DESC AMT")
                .item(1, "STEAK", "$17.95")
                .summary("SUBTOTAL", "$17.95")
                .pages(), trace);
        assertEquals(MissDiagnosis.Verdict.NOT_ON_ANY_PAGE,
                MissDiagnosis.byAmount(trace, 795L).verdict);
    }

    @Test
    public void noShortfallAndNoTraceAreBothAnsweredRatherThanThrown() {
        assertEquals(MissDiagnosis.Verdict.NOT_ON_ANY_PAGE,
                MissDiagnosis.byAmount(null, 795L).verdict);
        assertEquals(MissDiagnosis.Verdict.NOT_ON_ANY_PAGE,
                MissDiagnosis.byAmount(new ParseTrace(), 795L).verdict);
        ParseTrace trace = new ParseTrace();
        parse(billWithALostRow(), trace);
        assertEquals(MissDiagnosis.Verdict.NOT_ON_ANY_PAGE,
                MissDiagnosis.byAmount(trace, 0L).verdict);
    }
}
