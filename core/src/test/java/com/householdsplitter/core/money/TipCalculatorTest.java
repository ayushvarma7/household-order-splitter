package com.householdsplitter.core.money;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TipCalculatorTest {

    @Test
    public void aPercentageIsTakenOnThePreTaxSubtotal() {
        // $41.51 of food, whatever the tax on it happens to be.
        assertEquals(623L, TipCalculator.ofSubtotal(4151L, 15));
        assertEquals(747L, TipCalculator.ofSubtotal(4151L, 18));
        assertEquals(830L, TipCalculator.ofSubtotal(4151L, 20));
    }

    @Test
    public void halfCentsRoundAwayFromZero() {
        // 10% of $0.05 is exactly half a cent.
        assertEquals(1L, TipCalculator.ofSubtotal(5L, 10));
        // 15% of $10.10 is 1.515, which is a penny and a half.
        assertEquals(152L, TipCalculator.ofSubtotal(1010L, 15));
    }

    @Test
    public void nothingToTipOnMeansNoTip() {
        assertEquals(0L, TipCalculator.ofSubtotal(0L, 20));
        assertEquals(0L, TipCalculator.ofSubtotal(-100L, 20));
        assertEquals(0L, TipCalculator.ofSubtotal(4151L, 0));
    }

    @Test
    public void roundingUpReachesTheNextWholeFigure() {
        // $44.61 to the next dollar is $45.00, so 39 cents.
        assertEquals(39L, TipCalculator.toRoundTotal(4461L, 100L));
        // And to the next five dollars is also $45.00, because $45 is a multiple of five.
        assertEquals(39L, TipCalculator.toRoundTotal(4461L, 500L));
        // $41.20 to the next five is $45.00, so $3.80.
        assertEquals(380L, TipCalculator.toRoundTotal(4120L, 500L));
        // And to the next ten is $50.00.
        assertEquals(880L, TipCalculator.toRoundTotal(4120L, 1000L));
    }

    @Test
    public void whateverTheStepTheTotalLandsOnAMultipleOfIt() {
        for (long total : new long[]{1L, 4461L, 4120L, 9999L, 10000L}) {
            for (long step : new long[]{100L, 500L, 1000L}) {
                long topUp = TipCalculator.toRoundTotal(total, step);
                assertEquals(total + " rounded by " + step, 0L, (total + topUp) % step);
            }
        }
    }

    /** A button that does nothing when pressed is a broken button. */
    @Test
    public void roundingUpFromARoundFigureAddsAWholeStep() {
        assertEquals(100L, TipCalculator.toRoundTotal(6000L, 100L));
        assertEquals(500L, TipCalculator.toRoundTotal(6000L, 500L));
    }

    @Test
    public void aTypedTipCanBeShownAsAPercentage() {
        assertEquals("18.0%", 180, TipCalculator.percentTenthsOf(747L, 4151L));
        assertEquals("20.0%", 200, TipCalculator.percentTenthsOf(830L, 4151L));
        assertEquals("0.0%", 0, TipCalculator.percentTenthsOf(0L, 4151L));
    }

    @Test
    public void noSubtotalIsNotTheSameAsNoPercentage() {
        assertEquals(-1, TipCalculator.percentTenthsOf(500L, 0L));
    }

    /** The round trip, which is what the user actually sees on the screen. */
    @Test
    public void aPercentageTipReadsBackAsThatPercentage() {
        for (long subtotal : new long[]{1000L, 4151L, 8899L, 12345L}) {
            for (int percent : new int[]{15, 18, 20, 25}) {
                long tip = TipCalculator.ofSubtotal(subtotal, percent);
                assertEquals(subtotal + " at " + percent + "%",
                        percent * 10, TipCalculator.percentTenthsOf(tip, subtotal));
            }
        }
    }
}
