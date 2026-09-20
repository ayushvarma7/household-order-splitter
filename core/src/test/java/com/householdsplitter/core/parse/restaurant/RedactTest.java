package com.householdsplitter.core.parse.restaurant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Card data removal, checked from both directions: that it takes out what it must, and
 * that it leaves money alone.
 *
 * <p>The second half matters more than the first. A pattern that blanks a total is a
 * parser that silently loses a charge, and a receipt is nothing but digits.
 */
public class RedactTest {

    private static void removes(String line) {
        assertTrue("card data survived: " + Redact.cardData(line),
                Redact.carriesCardData(line));
        assertFalse(Redact.cardData(line).matches(".*\\d{4}\\b.*\\d{4}.*"));
    }

    private static void keeps(String line) {
        assertEquals("this line was not card data", line, Redact.cardData(line));
    }

    @Test
    public void maskedCardNumbersAreRemoved() {
        removes("VISA ************1234");
        removes("MASTERCARD XXXXXXXXXXXX4242");
        removes("ACCT ****5678");
        removes("CARD #: ****1111");
    }

    @Test
    public void fullAccountNumbersAreRemoved() {
        removes("4111 1111 1111 1111");
        removes("4111-1111-1111-1111");
        removes("4111111111111111");
    }

    @Test
    public void authorisationCodesAreRemoved() {
        assertTrue(Redact.carriesCardData("AUTH CODE: 0A41B9"));
        assertTrue(Redact.carriesCardData("APPROVAL 123456"));
        assertTrue(Redact.carriesCardData("REF # 884512003"));
        assertTrue(Redact.carriesCardData("AID: A0000000031010"));
    }

    @Test
    public void theLastFourAfterEndingInAreRemoved() {
        assertTrue(Redact.carriesCardData("Visa ending in 4242"));
        assertFalse(Redact.cardData("Visa ending in 4242").contains("4242"));
    }

    /** Everything below here is what must survive untouched. */
    @Test
    public void amountsAreNeverTouched() {
        keeps("SUBTOTAL 41.51");
        keeps("TOTAL $44.61");
        keeps("2 CAESAR SALAD 23.38");
        keeps("$1,284.00");
        keeps("TAX 3.10");
    }

    /** The commonest shape on a printed bill, and the one a careless pattern destroys. */
    @Test
    public void dotLeadersJoiningADishToItsPriceSurvive() {
        keeps("PENNE ARRABBIATA ........ 14.95");
        keeps("ESPRESSO .... 3.00");
    }

    @Test
    public void ordinaryReceiptFurnitureSurvives() {
        keeps("TABLE 3");
        keeps("HOST: JAMES");
        keeps("4/09/2024 8:36:18 AM");
        keeps("GOURMET COFFEE");
        keeps("GUESTS: 4");
    }

    @Test
    public void nothingIsStillNothing() {
        assertEquals(null, Redact.cardData(null));
        assertEquals("", Redact.cardData(""));
        assertFalse(Redact.carriesCardData(null));
    }
}
