package com.householdsplitter.core.parse.restaurant;

import java.util.regex.Pattern;

/**
 * Removes payment card data from recognised text before anything stores it.
 *
 * <p>The two screenshot stores never needed this. A Walmart order page prints "Visa ending
 * in 1234" and nothing more, and that single line is discarded as chrome. A photograph of a
 * restaurant bill is a different situation: the customer copy carries the masked card
 * number, the authorisation code, the entry method and often a signature line, and the
 * merchant copy frequently sits in the same frame.
 *
 * <p>That text would otherwise be persisted three times over: in an item's raw OCR text, in
 * the discarded-row table that feeds the parser report, and in the parse trace. None of
 * those needs it, and a receipt image the user keeps is a deliberate choice in a way that a
 * database column quietly holding card digits is not.
 *
 * <p>Redaction happens at the recognition boundary, before the parser sees a single
 * element, so there is no path through the app on which the digits reach storage. Applied
 * to every store rather than only to restaurants, because the cost is a regular expression
 * over short strings and the alternative is remembering to opt in.
 *
 * <p>Amounts are deliberately left alone. A masked card number and a price are both digits,
 * and telling them apart is the entire job here: the patterns below match the shapes a card
 * number takes and nothing else, so a total is never mistaken for a PAN and blanked out of
 * a bill.
 */
public final class Redact {

    /** What replaces the digits. Short, obvious, and never mistakable for an amount. */
    public static final String MASK = "[redacted]";

    private static final Pattern[] CARD_DATA = {
            // A full or partial PAN, masked by the till in any of the usual ways:
            // ****1234, XXXXXXXXXXXX1234, ############1234, ....1234
            // The dot is deliberately absent from this class. A run of dots followed by a
            // number is the commonest thing on a restaurant bill: the leader that joins a
            // dish to its price. Masking "PENNE ...... 14.95" would delete the charge.
            Pattern.compile("(?i)[*x#\\u2022]{3,}\\s*\\d{2,6}"),
            // Groups of digits in card shape, 13 to 19 digits with optional separators.
            Pattern.compile("\\b\\d{4}[ -]?\\d{4}[ -]?\\d{4}[ -]?\\d{1,7}\\b"),
            // An unbroken run long enough to be an account number and not an amount.
            Pattern.compile("\\b\\d{12,19}\\b"),
            // The words tills print beside the last four.
            Pattern.compile("(?i)\\b(ending in|acct|account|card ?#|pan)\\s*:?\\s*[*xX#.]*\\d{2,6}\\b"),
            // Authorisation, approval and reference codes.
            Pattern.compile("(?i)\\b(auth(orizatio|orisatio)?n?\\s*(code|no|num|#)?|approval(\\s*code)?"
                    + "|ref(erence)?\\s*(no|num|#)|trace|arqc|aid)\\s*:?\\s*[a-z0-9]{4,}\\b")
    };

    private Redact() {
    }

    /**
     * The line with any card data replaced by {@link #MASK}.
     *
     * <p>Returns the same string when there is nothing to remove, which is the overwhelming
     * majority of lines, so the common case allocates nothing.
     */
    public static String cardData(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        for (Pattern pattern : CARD_DATA) {
            if (pattern.matcher(result).find()) {
                result = pattern.matcher(result).replaceAll(MASK);
            }
        }
        return result;
    }

    /** True when this line carried something that had to be removed. */
    public static boolean carriesCardData(String text) {
        return text != null && !text.equals(cardData(text));
    }
}
