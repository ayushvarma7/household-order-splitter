package com.householdsplitter.core.quality;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ParserScorecardTest {

    @Test
    public void nothingJudgedReportsNoRateRatherThanZero() {
        // Zero would read as "the parser got everything wrong" on a household that has not
        // imported anything yet, which is a claim the data does not support.
        ParserScorecard empty = ParserScorecard.empty();
        assertEquals(0, empty.judged());
        assertEquals(ParserScorecard.UNKNOWN, empty.accuracyPermille());
        assertEquals(ParserScorecard.UNKNOWN, empty.valueAccuracyPermille());
    }

    @Test
    public void accuracyIsTheShareOfRowsLeftExactlyAsRead() {
        ParserScorecard card = ParserScorecard.builder()
                .keptAsRead(100).keptAsRead(200).keptAsRead(300)
                .corrected(400)
                .build();
        assertEquals(4, card.judged());
        assertEquals("three of four", 750, card.accuracyPermille());
        assertEquals(250, card.correctedPermille());
    }

    @Test
    public void everyFailureCountsAgainstIt() {
        // A deleted row and a hand-typed row are both the reader's failures, so both are in
        // the denominator. Counting only the rows it produced would let a parser that
        // silently drops half an order score perfectly on the half it kept.
        ParserScorecard card = ParserScorecard.builder()
                .keptAsRead(100).keptAsRead(100)
                .removed(100)
                .addedByHand(100)
                .build();
        assertEquals(4, card.judged());
        assertEquals(500, card.accuracyPermille());
        assertEquals(250, card.inventedPermille());
        assertEquals(250, card.missedPermille());
    }

    @Test
    public void inventedAndMissedAreReportedApart() {
        // Same headline, opposite problems. An invented charge is visible in the list; a
        // missed one is not, and only shows up as a short total. The breakdown is what
        // stops a reader that fails quietly from looking like one that fails loudly.
        ParserScorecard loud = ParserScorecard.builder()
                .keptAsRead(100).removed(100).build();
        ParserScorecard quiet = ParserScorecard.builder()
                .keptAsRead(100).addedByHand(100).build();

        assertEquals(loud.accuracyPermille(), quiet.accuracyPermille());
        assertEquals(500, loud.inventedPermille());
        assertEquals(0, loud.missedPermille());
        assertEquals(0, quiet.inventedPermille());
        assertEquals(500, quiet.missedPermille());
    }

    @Test
    public void valueAccuracyDisagreesWithRowAccuracyWhenItShould() {
        // Nine rows right at a dollar each, one wrong at ninety-one. Ninety percent of the
        // rows and half the money, and the money is what the household argues about.
        ParserScorecard.Builder builder = ParserScorecard.builder();
        for (int i = 0; i < 9; i++) {
            builder.keptAsRead(100);
        }
        ParserScorecard card = builder.addedByHand(9100).build();

        assertEquals(900, card.accuracyPermille());
        assertEquals(90, card.valueAccuracyPermille());
    }

    @Test
    public void ordersSumRatherThanAverage() {
        // A one-row perfect order must not outweigh a forty-row poor one. Averaging the two
        // rates would say 55%; summing the counts says 12.1%, which is what happened.
        ParserScorecard tiny = ParserScorecard.builder().keptAsRead(500).build();
        ParserScorecard.Builder big = ParserScorecard.builder();
        for (int i = 0; i < 4; i++) {
            big.keptAsRead(100);
        }
        for (int i = 0; i < 36; i++) {
            big.corrected(100);
        }

        ParserScorecard total = tiny.plus(big.build());
        assertEquals(41, total.judged());
        assertEquals(1000, tiny.accuracyPermille());
        assertEquals(121, total.accuracyPermille());
    }

    @Test
    public void addingNothingChangesNothing() {
        ParserScorecard card = ParserScorecard.builder().keptAsRead(100).corrected(200).build();
        assertEquals(card.accuracyPermille(), card.plus(null).accuracyPermille());
        assertEquals(card.judged(), card.plus(ParserScorecard.empty()).judged());
    }

    @Test
    public void aRefundRowCountsItsSizeNotItsSign() {
        // Discounts and refunds arrive negative. A negative amount must not subtract from
        // the money the reader is judged on, or a wrong refund would improve the score.
        ParserScorecard card = ParserScorecard.builder()
                .keptAsRead(1000)
                .addedByHand(-1000)
                .build();
        assertEquals(2000L, card.judgedCents());
        assertEquals(500, card.valueAccuracyPermille());
    }
}
