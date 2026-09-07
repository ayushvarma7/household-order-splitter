package com.householdsplitter.core.parse.walmart;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Interface furniture that must never become a line item. SPEC 8.4.
 *
 * <p>The list is deliberately visible and documented in PARSING.md, because it is the
 * first thing to edit when Walmart changes its wording. Matching is case-insensitive and
 * runs against a normalised form of the text, so stray OCR punctuation does not defeat it
 * (SPEC 8.4, "tolerant of OCR noise").
 */
final class ChromeFilter {

    private static final List<Pattern> CHROME = Collections.unmodifiableList(Arrays.asList(
            // 8.4.1 buttons and links
            Pattern.compile("^\\+? ?add$"),
            Pattern.compile("^add to cart$"),
            Pattern.compile("^review item$"),
            Pattern.compile("^write a review$"),
            Pattern.compile("^view$"),
            Pattern.compile("^view delivery photo$"),
            Pattern.compile("^view all$"),
            Pattern.compile("^reorder$"),
            Pattern.compile("^buy it again$"),
            Pattern.compile("^start a return$"),
            Pattern.compile("^get help$"),
            Pattern.compile("^track delivery$"),
            Pattern.compile("^rate your order$"),
            Pattern.compile("^how did we do\\??$"),

            // 8.4.2 card headers
            Pattern.compile("^payment method$"),
            Pattern.compile("^charge history$"),
            Pattern.compile("^your transaction activity for this order$"),
            Pattern.compile("^order summary$"),

            // 8.4.3 card and masked payment lines
            Pattern.compile("^visa\\b.*$"),
            Pattern.compile("^mastercard\\b.*$"),
            Pattern.compile("^amex\\b.*$"),
            Pattern.compile("^discover\\b.*$"),
            Pattern.compile("^ebt\\b.*$"),
            Pattern.compile(".*\\bending in \\d{3,4}$"),

            // 8.4.4 delivery status lines
            Pattern.compile("^delivery dropped off on .*$"),
            Pattern.compile("^delivered on .*$"),
            Pattern.compile("^arriving .*$"),
            Pattern.compile("^want to see what was substituted\\??$"),
            Pattern.compile("^your order was delivered.*$"),

            // 8.4.7 the status bar: clocks, recording timers and battery
            Pattern.compile("^\\d{1,2}:\\d{2}(:\\d{2})?( ?[ap]m)?$"),
            Pattern.compile("^\\d{1,3}%$"),
            Pattern.compile("^lte$|^5g$|^4g$|^wi-?fi$")
    ));

    /** SPEC 8.4.8: the rating carousel is detected by its stars. */
    private static final Pattern STAR_GLYPH =
            Pattern.compile("[★☆⭐✪✩⭑⭒]");

    /** SPEC 8.4.5 and 8.6.6. */
    private static final Pattern ORDER_NUMBER =
            Pattern.compile("^order ?# ?([0-9][0-9 -]{5,})$");

    private ChromeFilter() {
    }

    /** Lowercase, collapse whitespace, drop trailing punctuation. */
    static String normalise(String text) {
        if (text == null) {
            return "";
        }
        String value = text.toLowerCase().trim();
        value = value.replaceAll("\\s+", " ");
        value = value.replaceAll("[\\.,;:•·]+$", "");
        return value.trim();
    }

    static boolean isChrome(String text) {
        String normalised = normalise(text);
        if (normalised.isEmpty()) {
            return true;
        }
        for (Pattern pattern : CHROME) {
            if (pattern.matcher(normalised).matches()) {
                return true;
            }
        }
        return false;
    }

    static boolean hasStarGlyph(String text) {
        return text != null && STAR_GLYPH.matcher(text).find();
    }

    /**
     * SPEC 8.4.2: the payment card marks the end of the item list. Everything below it is
     * summary and receipt furniture, never a purchased row.
     */
    static boolean endsItemRegion(String text) {
        String normalised = normalise(text);
        return normalised.equals("payment method")
                || normalised.equals("charge history")
                || normalised.equals("your transaction activity for this order");
    }

    /** SPEC 8.6.6: returns the digits, or null. */
    static String orderNumberIn(String text) {
        java.util.regex.Matcher matcher = ORDER_NUMBER.matcher(normalise(text));
        if (matcher.matches()) {
            return matcher.group(1).replace(" ", "").trim();
        }
        return null;
    }
}
