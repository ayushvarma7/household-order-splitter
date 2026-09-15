package com.householdsplitter.core.parse.walmart;

import com.householdsplitter.core.parse.model.ParsedItem;
import com.householdsplitter.core.parse.model.ReviewReason;

/**
 * SPEC 8.8. Flags are advisory only: every parse lands in the editable review screen
 * regardless, and nothing here can block the user (PROMPT hard rule 7).
 */
final class ConfidenceRules {

    private ConfidenceRules() {
    }

    static void apply(ParsedItem.Builder builder, String name, int quantity, long lineTotalCents,
                      int lowestConfidencePercent, boolean strikeThroughResolved,
                      String sectionName, ParseTuning tuning) {
        if (lowestConfidencePercent >= 0 && lowestConfidencePercent < tuning.minConfidencePercent) {
            builder.flag(ReviewReason.LOW_CONFIDENCE);
        }
        if (name.length() < tuning.minNameLength) {
            builder.flag(ReviewReason.SHORT_NAME);
        }
        if (quantity > 1) {
            builder.flag(ReviewReason.QUANTITY_ABOVE_ONE);
        }
        // A row that printed an original price beside the charged one is deliberately NOT
        // flagged. The only figure that matters is what was actually billed for that item,
        // which is the right-most amount in the price column, and taking it is the rule
        // rather than an exception worth interrupting the user about. The same goes for a
        // unit price such as "$3.94/lb" printed on the left: it is never the line total and
        // never worth a warning.
        if (SectionHeaders.isExcludedSection(sectionName)) {
            builder.flag(ReviewReason.EXCLUDED_SECTION);
        }
        if (Math.abs(lineTotalCents) > tuning.priceOutlierCents) {
            builder.flag(ReviewReason.PRICE_OUTLIER);
        }
    }
}
