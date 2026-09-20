package com.householdsplitter.core.parse.restaurant;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReceiptCaseTest {

    @Test
    public void aShoutedNameIsTidied() {
        assertEquals("Caesar Salad", ReceiptCase.title("CAESAR SALAD"));
        assertEquals("Grilled Salmon", ReceiptCase.title("GRILLED SALMON"));
    }

    /** The mistake the first version of this made. A menu is full of short real words. */
    @Test
    public void shortOrdinaryWordsAreNotMistakenForAcronyms() {
        assertEquals("Iced Tea", ReceiptCase.title("ICED TEA"));
        assertEquals("Ham and Egg Pie", ReceiptCase.title("HAM AND EGG PIE"));
        assertEquals("Rib Tips", ReceiptCase.title("RIB TIPS"));
        assertEquals("Rum Punch", ReceiptCase.title("RUM PUNCH"));
    }

    @Test
    public void initialismsKeepTheirCapitals() {
        assertEquals("BBQ Ribs", ReceiptCase.title("BBQ RIBS"));
        assertEquals("BLT Sandwich", ReceiptCase.title("BLT SANDWICH"));
        assertEquals("Hazy IPA", ReceiptCase.title("HAZY IPA"));
    }

    @Test
    public void joiningWordsStayLowercaseUnlessTheyOpenTheName() {
        assertEquals("Steak and Eggs", ReceiptCase.title("STEAK AND EGGS"));
        assertEquals("Mac n Cheese", ReceiptCase.title("MAC N CHEESE"));
        assertEquals("The Special", ReceiptCase.title("THE SPECIAL"));
    }

    /** A till that can print mixed case already made a choice worth keeping. */
    @Test
    public void aNameThatIsAlreadyMixedCaseIsLeftAlone() {
        assertEquals("Chef's Tasting Menu", ReceiptCase.title("Chef's Tasting Menu"));
        assertEquals("pad see ew", ReceiptCase.title("pad see ew"));
        assertEquals("iPhone Charger", ReceiptCase.title("iPhone Charger"));
    }

    @Test
    public void digitsAndPunctuationSurvive() {
        // "PC" is piece, and is on the acronym list.
        assertEquals("2 PC Chicken", ReceiptCase.title("2 PC CHICKEN"));
        assertEquals("(GF) Pasta", ReceiptCase.title("(GF) PASTA"));
        assertEquals("Fish & Chips", ReceiptCase.title("FISH & CHIPS"));
    }

    @Test
    public void nothingIsStillNothing() {
        assertEquals(null, ReceiptCase.title(null));
        assertEquals("", ReceiptCase.title(""));
    }
}
