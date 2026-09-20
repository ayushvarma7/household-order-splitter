package com.householdsplitter.core.parse.layout;

import com.householdsplitter.core.money.Cents;
import com.householdsplitter.core.parse.model.OcrElement;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recognising and classifying money tokens. SPEC 8.3.1 to 8.3.3. */
public final class PriceTokens {

    /** SPEC 8.3.1, verbatim, plus an optional leading minus for refunded rows. */
    private static final Pattern PRICE_TOKEN =
            Pattern.compile("^-?[\\p{Sc}]?\\d{1,3}(,\\d{3})*\\.\\d{2}$");

    /**
     * SPEC 8.6.4: the free-delivery line prints a bare {@code $0} beside a struck-through
     * {@code $9.95}, so the summary block has to tolerate an amount with no cents. This
     * looser pattern is used only there, never to open an item block.
     */
    private static final Pattern LOOSE_AMOUNT =
            Pattern.compile("^-?[\\p{Sc}]\\d{1,3}(,\\d{3})*(\\.\\d{1,2})?$|^-?\\d{1,3}(,\\d{3})*\\.\\d{2}$");

    /**
     * SPEC 8.3.3, written tolerantly because SPEC 8.4 asks for tolerance of OCR noise and
     * these are the exact confusions a recogniser makes on this text: the l of "/lb" is
     * dropped or read as 1 or I, and the o of "/oz" is read as a zero. A unit price that is
     * not recognised as one does not merely lose the unit price, it leaks the whole
     * fragment into the product name and reads as an item of its own.
     *
     * <p>The denominators are the ordinary grocery set. Only /lb and cents-per-lb appear on
     * the orders traced so far, but an unlisted denominator does not fail quietly: the whole
     * "$0.71/qt" fragment becomes a product name. These are units of measure, not claims
     * about any product, so covering them costs nothing and closes that gap.
     */
    private static final String UNITS =
            "[l1i|]?b|lbs|[o0]z|fl\\s?[o0]z|ea|each|kg|g|qt|pt|gal|ct|pk|"
                    + "sheet|sheets|roll|rolls";

    private static final Pattern UNIT_SUFFIX =
            Pattern.compile("(?i)(/\\s?(" + UNITS + ")\\b|¢\\s?/)");

    private static final Pattern UNIT_PRICE_WHOLE =
            Pattern.compile("(?i)^-?\\$?\\d{1,3}(,\\d{3})*(\\.\\d{1,2})?\\s?"
                    + "/\\s?(" + UNITS + ")\\.?$"
                    + "|^\\d{1,3}(\\.\\d)?\\s?¢\\s?/\\s?\\w+$");

    private PriceTokens() {
    }

    static boolean isPriceToken(String text) {
        return text != null && PRICE_TOKEN.matcher(text.trim()).matches();
    }

    /** Tolerates {@code $0} (SPEC 8.6.4). Summary block only. */
    static boolean isLooseAmount(String text) {
        return text != null && LOOSE_AMOUNT.matcher(text.trim()).matches();
    }

    /** A complete unit price such as {@code $3.94/lb} in a single element. */
    static boolean isUnitPriceText(String text) {
        return text != null && UNIT_PRICE_WHOLE.matcher(text.trim()).matches();
    }

    static boolean hasUnitSuffix(String text) {
        return text != null && UNIT_SUFFIX.matcher(text).find();
    }

    /**
     * SPEC 8.3.3: a price is a unit price when the element itself or its immediate
     * neighbour carries a unit suffix. Walmart splits {@code $3.94/lb} either way
     * depending on how the recogniser segments it.
     */
    static boolean isUnitPriceAt(List<OcrElement> bandElements, int index) {
        OcrElement element = bandElements.get(index);
        if (isUnitPriceText(element.text())) {
            return true;
        }
        if (!isPriceToken(element.text())) {
            return false;
        }
        if (index + 1 < bandElements.size()
                && startsWithUnitSuffix(bandElements.get(index + 1).text())) {
            return true;
        }
        // The backward case is the recogniser splitting "$3.94 /lb" so the suffix lands
        // first. It must NOT fire for a complete unit price standing beside a line price,
        // which is a different element with its own amount.
        return index > 0 && isBareUnitSuffix(bandElements.get(index - 1).text());
    }

    /** A token that is nothing but a unit suffix, e.g. {@code /lb}. */
    static boolean isBareUnitSuffix(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.matches("(?i)^/ ?([l1i|]?b|[o0]z|ea|each|kg)$")
                || trimmed.matches("(?i)^([l1i|]?b|[o0]z|ea|each|kg)$");
    }

    /** The neighbour form: an element that is nothing but {@code /lb}. */
    static boolean startsWithUnitSuffix(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        return UNIT_SUFFIX.matcher(trimmed).lookingAt()
                || trimmed.matches("(?i)^([l1i|]?b|[o0]z|ea|each|kg)$");
    }

    /** The displayed unit price, joining the neighbour form back together. */
    static String unitPriceTextAt(List<OcrElement> bandElements, int index) {
        OcrElement element = bandElements.get(index);
        if (isUnitPriceText(element.text())) {
            return element.text().trim();
        }
        if (index + 1 < bandElements.size()
                && startsWithUnitSuffix(bandElements.get(index + 1).text())) {
            String suffix = bandElements.get(index + 1).text().trim();
            if (!suffix.startsWith("/")) {
                suffix = "/" + suffix;
            }
            return element.text().trim() + suffix;
        }
        return element.text().trim();
    }

    public static long toCents(String text) {
        return Cents.parse(text);
    }

    /**
     * SPEC 8.6.4 and 8.6.5: when a band prints two amounts, the right-most one is the
     * charged value and the other is the struck-through original.
     *
     * @return the index of the winning element, or -1 when the band has no amount
     */
    static int lastAmountIndex(List<OcrElement> bandElements, int fromIndexInclusive) {
        int found = -1;
        for (int i = fromIndexInclusive; i < bandElements.size(); i++) {
            if (isUnitPriceAt(bandElements, i)) {
                continue;
            }
            if (isLooseAmount(bandElements.get(i).text())) {
                found = i;
            }
        }
        return found;
    }

    static int countAmounts(List<OcrElement> bandElements) {
        int count = 0;
        for (int i = 0; i < bandElements.size(); i++) {
            if (!isUnitPriceAt(bandElements, i) && isLooseAmount(bandElements.get(i).text())) {
                count++;
            }
        }
        return count;
    }

    /** A bare unit price standing on its own metadata line (SPEC 8.3.7). */
    static boolean isBareUnitPriceLine(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.trim();
        if (isUnitPriceText(trimmed)) {
            return true;
        }
        Matcher matcher = UNIT_SUFFIX.matcher(trimmed);
        return matcher.find() && trimmed.length() <= 16 && trimmed.matches(".*\\d.*");
    }
}
