package com.householdsplitter.core.browse;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchMatchTest {

    /** What makes an empty search box show the whole list rather than nothing. */
    @Test
    public void anEmptyQueryMatchesEverything() {
        assertTrue(SearchMatch.matches(null, "Weekly shop"));
        assertTrue(SearchMatch.matches("", "Weekly shop"));
        assertTrue(SearchMatch.matches("   ", "Weekly shop"));
    }

    @Test
    public void aPartialWordMatches() {
        assertTrue(SearchMatch.matches("week", "Weekly shop"));
    }

    @Test
    public void capitalsAreIgnored() {
        assertTrue(SearchMatch.matches("WEEKLY", "Weekly shop"));
        assertTrue(SearchMatch.matches("weekly", "WEEKLY SHOP"));
    }

    @Test
    public void surroundingSpaceIsIgnored() {
        assertTrue(SearchMatch.matches("  weekly  ", "Weekly shop"));
    }

    /** Any of the order's text will do, which is what makes an item name searchable. */
    @Test
    public void anyOfTheGivenTextCanMatch() {
        assertTrue(SearchMatch.matches("coffee", "Weekly shop", "Ana, Ben", "Coffee beans"));
        assertTrue(SearchMatch.matches("ben", "Weekly shop", "Ana, Ben", "Coffee beans"));
    }

    /** "coffee march" should find a March order containing coffee. */
    @Test
    public void wordsMatchInAnyOrderAndAcrossFields() {
        assertTrue(SearchMatch.matches("coffee ana", "Weekly shop", "Ana, Ben", "Coffee beans"));
        assertTrue(SearchMatch.matches("ana coffee", "Weekly shop", "Ana, Ben", "Coffee beans"));
    }

    /**
     * Every word has to match. An order matching only half of what was typed is worse than
     * no result, because the user cannot tell which half was honoured.
     */
    @Test
    public void everyWordHasToMatch() {
        assertFalse(SearchMatch.matches("coffee tea", "Weekly shop", "Coffee beans"));
    }

    @Test
    public void nothingMatchesWhenThereIsNoText() {
        assertFalse(SearchMatch.matches("coffee"));
        assertFalse(SearchMatch.matches("coffee", (String) null));
    }

    @Test
    public void aQueryThatMatchesNothingReturnsFalse() {
        assertFalse(SearchMatch.matches("zzz", "Weekly shop", "Coffee beans"));
    }
}
