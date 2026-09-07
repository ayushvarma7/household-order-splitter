package com.householdsplitter.core.parse.walmart;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The collapsible section headers of SPEC 8.5.
 *
 * <p>The count these headers carry is a <em>unit</em> count, not a row count: an order
 * reading "33 items delivered" may hold 18 rows because quantities are summed (SPEC
 * 8.5.4). Nothing in this class returns that number to the caller for validation, and
 * reconciliation is done on money instead (SPEC 8.9.5).
 */
final class SectionHeaders {

    private static final List<Pattern> HEADERS = Collections.unmodifiableList(Arrays.asList(
            Pattern.compile("^\\d+ items? delivered$"),
            Pattern.compile("^\\d+ substituted$"),
            Pattern.compile("^\\d+ shopped$"),
            Pattern.compile("^\\d+ unavailable$"),
            Pattern.compile("^\\d+ cancell?ed$"),
            Pattern.compile("^\\d+ refunded$")
    ));

    private static final Pattern LEADING_COUNT = Pattern.compile("^\\d+\\s+");

    /** Only the "N items delivered" header, which is the one that counts units. */
    private static final Pattern DELIVERED_UNITS =
            Pattern.compile("^(\\d+) items? delivered$");

    private SectionHeaders() {
    }

    /** @return the section name without its count, or null when this is not a header */
    static String sectionNameOf(String text) {
        String normalised = ChromeFilter.normalise(text);
        for (Pattern pattern : HEADERS) {
            if (pattern.matcher(normalised).matches()) {
                Matcher stripper = LEADING_COUNT.matcher(normalised);
                return stripper.replaceFirst("").trim();
            }
        }
        return null;
    }

    static boolean isHeader(String text) {
        return sectionNameOf(text) != null;
    }

    /**
     * The unit count printed on the "N items delivered" header, or -1.
     *
     * <p>Returned as a hint and nothing more. SPEC 8.5.4 is emphatic that this counts units
     * rather than rows, so it must never validate a parse, and SPEC 8.9.5 says
     * reconciliation is always on money. What it is genuinely useful for is telling a user
     * whose five rows do not add up to a fifty dollar bill that the order mentions thirty
     * three units, so they have probably missed a screenshot. That is help, not validation:
     * it points at a likely cause rather than rejecting anything.
     */
    static int deliveredUnitCount(String text) {
        Matcher matcher = DELIVERED_UNITS.matcher(ChromeFilter.normalise(text));
        if (!matcher.matches()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    /**
     * SPEC 8.5.3: unavailable, cancelled and refunded rows default to EXCLUDED so nobody
     * is charged for them, while staying visible in review in case they were charged.
     */
    static boolean isExcludedSection(String sectionName) {
        if (sectionName == null) {
            return false;
        }
        String value = sectionName.toLowerCase();
        return value.startsWith("unavailable")
                || value.startsWith("cancel")
                || value.startsWith("refunded");
    }
}
