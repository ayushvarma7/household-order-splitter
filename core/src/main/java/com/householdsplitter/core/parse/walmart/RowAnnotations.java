package com.householdsplitter.core.parse.walmart;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The extra lines a Walmart item row prints about its own price.
 *
 * <p>A discounted row does not print one amount, it prints three things: the charged price,
 * the original struck through, and a line saying how much came off. Only the charged price is
 * a charge. The other two are commentary, and the user has said so directly: they care about
 * the final billed figure and nothing else.
 *
 * <p>The savings line is worth recognising rather than merely ignoring, because it makes the
 * struck-through price <em>provable</em>. On the observed row the charged price is $3.24, the
 * struck one $4.44, and the annotation reads "$1.20 from savings"; 4.44 - 3.24 = 1.20
 * exactly. That turns "the bigger number is probably the original" from a guess into
 * arithmetic that either reconciles or does not.
 *
 * <p>Every pattern here is wording observed on a real order. Nothing is invented: a guessed
 * label that never appears does nothing, but a guessed label that matches something else
 * would drop a real charge.
 */
final class RowAnnotations {

    /** "$1.20 from savings", and the same line with the amount trailing. */
    private static final Pattern FROM_SAVINGS = Pattern.compile(
            "(?i)^\\$?(\\d{1,3}(?:,\\d{3})*\\.\\d{2})\\s+from\\s+savings$");

    /** The bare tail, for when OCR splits the amount onto its own element. */
    private static final Pattern FROM_SAVINGS_BARE = Pattern.compile("(?i)^from\\s+savings$");

    /**
     * "Ordered price $13.97" on a weighed item, where the charged price differs because the
     * pack weight did. The charged figure is the one in the price column.
     */
    private static final Pattern ORDERED_PRICE = Pattern.compile(
            "(?i)^ordered price\\s*\\$?(\\d{1,3}(?:,\\d{3})*\\.\\d{2})$");

    private RowAnnotations() {
    }

    /**
     * True when a name-zone line is commentary about the price rather than part of the
     * product name. Such a line is dropped from the name, the way Qty and Multipack are.
     */
    static boolean isPriceCommentary(String line) {
        String text = ChromeFilter.normalise(line);
        return FROM_SAVINGS.matcher(text).matches()
                || FROM_SAVINGS_BARE.matcher(text).matches()
                || ORDERED_PRICE.matcher(text).matches();
    }

    /**
     * The amount in a "$1.20 from savings" line, in cents, or -1 when the line is not one.
     *
     * <p>Only the form carrying an amount returns a figure. The bare "from savings" tail
     * says a discount exists without saying how big, which is not enough to prove anything
     * with, so it returns -1 rather than a guess.
     */
    static long savingsCentsIn(String line) {
        Matcher matcher = FROM_SAVINGS.matcher(ChromeFilter.normalise(line));
        if (!matcher.matches()) {
            return -1L;
        }
        try {
            return PriceTokens.toCents(matcher.group(1));
        } catch (NumberFormatException notAnAmount) {
            return -1L;
        }
    }

    /** True when the line is a savings annotation in any observed form. */
    static boolean isSavingsLine(String line) {
        String text = ChromeFilter.normalise(line);
        return FROM_SAVINGS.matcher(text).matches() || FROM_SAVINGS_BARE.matcher(text).matches();
    }

    static long orderedPriceCentsIn(String line) {
        Matcher matcher = ORDERED_PRICE.matcher(ChromeFilter.normalise(line));
        if (!matcher.matches()) {
            return -1L;
        }
        try {
            return PriceTokens.toCents(matcher.group(1));
        } catch (NumberFormatException notAnAmount) {
            return -1L;
        }
    }
}
