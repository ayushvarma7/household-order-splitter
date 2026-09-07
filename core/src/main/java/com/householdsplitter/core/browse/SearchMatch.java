package com.householdsplitter.core.browse;

import java.util.Locale;

/**
 * Matching a typed query against the text of an order.
 *
 * <p>Kept pure so the behaviour that decides whether a user's order appears in their own
 * search results can be tested without a device.
 *
 * <p>The rules are chosen to be forgiving in the ways a person typing into a phone actually
 * needs. Words match in any order, so "coffee march" finds a March order containing coffee.
 * Case and surrounding whitespace are ignored. Every word has to match something, because a
 * search that returns an order matching only half of what was typed is worse than one that
 * returns nothing: the user cannot tell which half was honoured.
 */
public final class SearchMatch {

    private SearchMatch() {
    }

    /**
     * True when every whitespace-separated word of {@code query} appears somewhere in
     * {@code haystacks}. An empty or blank query matches everything, which is what makes an
     * empty search box show the whole list.
     */
    public static boolean matches(String query, String... haystacks) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }
        StringBuilder combined = new StringBuilder();
        if (haystacks != null) {
            for (String haystack : haystacks) {
                if (haystack != null) {
                    combined.append(haystack.toLowerCase(Locale.ROOT)).append('\n');
                }
            }
        }
        String text = combined.toString();
        for (String word : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!word.isEmpty() && !text.contains(word)) {
                return false;
            }
        }
        return true;
    }
}
