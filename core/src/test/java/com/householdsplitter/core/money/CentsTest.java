package com.householdsplitter.core.money;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Locale;

/** SPEC 6.2, 8.6.4, 10.1.1, 11.13. */
public class CentsTest {

    @Test
    public void parsesTheSpecExample() {
        assertEquals(5352L, Cents.parse("$53.52"));
    }

    @Test
    public void parsesWithoutSymbol() {
        assertEquals(197L, Cents.parse("1.97"));
    }

    /** SPEC 8.6.4: the free delivery line prints a bare "$0". */
    @Test
    public void parsesAmountWithoutCents() {
        assertEquals(0L, Cents.parse("$0"));
    }

    @Test
    public void parsesThousandsSeparator() {
        assertEquals(123456L, Cents.parse("$1,234.56"));
    }

    /** SPEC 11.13: comma as the decimal separator. */
    @Test
    public void parsesCommaDecimalSeparator() {
        assertEquals(5352L, Cents.parse("53,52"));
    }

    @Test
    public void parsesNegative() {
        assertEquals(-120L, Cents.parse("-$1.20"));
        assertEquals(-120L, Cents.parse("($1.20)"));
    }

    @Test
    public void rejectsTextWithoutDigits() {
        assertFalse(Cents.isParsable("Review item"));
        try {
            Cents.parse("Subtotal");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
            // not an amount
        }
    }

    @Test
    public void plainStringIsMachineReadable() {
        assertEquals("53.52", Cents.toPlainString(5352L));
        assertEquals("0.00", Cents.toPlainString(0L));
        assertEquals("0.07", Cents.toPlainString(7L));
        assertEquals("-1.20", Cents.toPlainString(-120L));
        assertEquals("1234.56", Cents.toPlainString(123456L));
    }

    @Test
    public void roundTripsThroughPlainString() {
        for (long cents = -5000L; cents <= 5000L; cents += 7L) {
            assertEquals(cents, Cents.parse(Cents.toPlainString(cents)));
        }
    }

    @Test
    public void formatsForDisplay() {
        CurrencyFormat usd = new CurrencyFormat("$", Locale.US);
        assertEquals("$1.66", usd.format(166L));
        assertEquals("$1,234.56", usd.format(123456L));
        assertEquals("-$1.20", usd.format(-120L));
    }

    /** SPEC 11.13 again, this time on the way out. */
    @Test
    public void formatsWithLocaleSeparators() {
        CurrencyFormat euro = new CurrencyFormat("€", Locale.GERMANY);
        assertEquals("€1.234,56", euro.format(123456L));
    }

    @Test
    public void quotientIsDisplayOnly() {
        assertEquals("8.39", Cents.quotientForDisplay(3355L, 4));
        assertTrue(Cents.isParsable(Cents.quotientForDisplay(3355L, 4)));
    }
}
