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

    /**
     * A real Burlington line, which is a style code, a product, a SKU, a count and a unit
     * price, all of it left of the amount column and all of it reaching the name.
     */
    @Test
    public void stockCodesAndStrayFiguresAreNotPartOfTheName() {
        assertEquals("POWERBLEND HOODIE-MAROON",
                ReceiptCase.withoutCodes("CPM253FH68 POWERBLEND HOODIE-MAROON M337784517 16.99"));
    }

    /** Short alphanumerics are real names and must survive. */
    @Test
    public void sizesAndShortNamesSurvive() {
        assertEquals("COKE 500ML", ReceiptCase.withoutCodes("COKE 500ML"));
        assertEquals("7UP", ReceiptCase.withoutCodes("7UP"));
        assertEquals("A1 SAUCE", ReceiptCase.withoutCodes("A1 SAUCE"));
        assertEquals("CAESAR SALAD", ReceiptCase.withoutCodes("CAESAR SALAD"));
    }

    /** Stripping everything would leave a row labelled with nothing, which is worse. */
    @Test
    public void aLineThatIsNothingButCodesKeepsWhatItHad() {
        assertEquals("9556789012345", ReceiptCase.withoutCodes("9556789012345"));
        assertEquals("16.99", ReceiptCase.withoutCodes("16.99"));
    }

    @Test
    public void theTwoStepsComposeTheWayTheReaderUsesThem() {
        assertEquals("Powerblend Hoodie-maroon",
                ReceiptCase.title(ReceiptCase.withoutCodes(
                        "CPM253FH68 POWERBLEND HOODIE-MAROON M337784517 16.99")));
    }

    /** The count column bleeding into the name, which is where the Burlington row ended up. */
    @Test
    public void aCountStrandedBetweenWordsIsNotPartOfTheName() {
        assertEquals("POWERBLEND HOODIE", ReceiptCase.withoutCodes("POWERBLEND 1 HOODIE"));
    }

    /** But a number that is genuinely part of a short name stays. */
    @Test
    public void aNumberInAShortNameStays() {
        assertEquals("7 UP", ReceiptCase.withoutCodes("7 UP"));
        assertEquals("PIZZA 12", ReceiptCase.withoutCodes("PIZZA 12"));
    }
}
