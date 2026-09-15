package com.householdsplitter.core.parse.layout;

import com.householdsplitter.core.parse.model.OcrElement;

import java.util.List;

/** Reading the charged figure off a band, whatever store printed it. */
final class BandAmounts {

    private BandAmounts() {
    }

    /**
     * SPEC 8.6.4: the charged amount is the right-most one on the band, so a struck-through
     * original beside it never wins.
     *
     * <p>Both stores need this and neither states it the same way. Walmart's free delivery
     * line reads "$9.95 $0", Amazon Fresh prints "$13.95 $0.00" with the first struck
     * through. Taking the right-most amount is correct for both without either being
     * detected, which is why this is a rule and not a special case.
     *
     * @return the amount in cents, or null when the band carries none
     */
    static Long amountOnBand(TextBand band) {
        List<OcrElement> elements = band.elements();
        int index = PriceTokens.lastAmountIndex(elements, 0);
        if (index < 0) {
            return null;
        }
        if (PriceTokens.countAmounts(elements) > 1) {
            band.strikeThroughResolved(true);
        }
        try {
            return PriceTokens.toCents(elements.get(index).text());
        } catch (NumberFormatException notAnAmount) {
            return null;
        }
    }
}
