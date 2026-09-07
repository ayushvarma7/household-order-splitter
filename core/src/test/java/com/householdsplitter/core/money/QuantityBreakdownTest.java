package com.householdsplitter.core.money;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Locale;

public class QuantityBreakdownTest {

    private final CurrencyFormat money = new CurrencyFormat("$", Locale.US);
    private static final String UNEVEN = "%d units";

    @Test
    public void aSingleUnitSaysNothing() {
        assertNull(QuantityBreakdown.describe(1, 505L, money, UNEVEN));
        assertNull(QuantityBreakdown.describe(0, 505L, money, UNEVEN));
    }

    @Test
    public void anEvenDivisionIsSpelledOut() {
        assertEquals("2 × $2.52", QuantityBreakdown.describe(2, 504L, money, UNEVEN));
        assertEquals("3 × $1.00", QuantityBreakdown.describe(3, 300L, money, UNEVEN));
    }

    /**
     * The case worth being careful about. $5.05 over two is $2.53 and $2.52, so neither
     * "2 x $2.53" nor "2 x $2.52" is true, and either would invite somebody to check the
     * arithmetic and find the app a penny out.
     */
    @Test
    public void anUnevenDivisionDoesNotInventAPerUnitPrice() {
        assertEquals("2 units", QuantityBreakdown.describe(2, 505L, money, UNEVEN));
        assertEquals("3 units", QuantityBreakdown.describe(3, 1000L, money, UNEVEN));
    }

    @Test
    public void dividesEvenlyIsOnlyTrueForRealMultiples() {
        assertTrue(QuantityBreakdown.dividesEvenly(2, 504L));
        assertFalse(QuantityBreakdown.dividesEvenly(2, 505L));
        assertFalse("a single unit is not a division", QuantityBreakdown.dividesEvenly(1, 500L));
    }

    @Test
    public void theUnitAmountIsExactOrAbsent() {
        assertEquals(252L, QuantityBreakdown.unitCents(2, 504L));
        assertEquals(-1L, QuantityBreakdown.unitCents(2, 505L));
    }

    /** A free item still divides, and saying "2 x $0.00" is correct. */
    @Test
    public void aZeroPricedRowStillDescribes() {
        assertEquals("2 × $0.00", QuantityBreakdown.describe(2, 0L, money, UNEVEN));
    }

    @Test
    public void aNullUnevenWordingFallsBackToSayingNothing() {
        assertNull(QuantityBreakdown.describe(2, 505L, money, null));
    }
}
