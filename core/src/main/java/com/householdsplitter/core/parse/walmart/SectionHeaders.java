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
