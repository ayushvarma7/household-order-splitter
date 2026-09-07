package com.householdsplitter.core.parse.walmart;

import com.householdsplitter.core.parse.model.OcrElement;
import com.householdsplitter.core.parse.model.OrderField;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The app-bar date and the summary block. SPEC 8.6. */
final class OrderFieldExtractor {

    /**
     * SPEC 8.6.1, e.g. "Sep 03, 2026 order".
     *
     * <p>Searched for anywhere on the band rather than anchored to the whole of it. On a
     * real screenshot the blue app bar also carries a back chevron and a cart showing
     * "$0.00", and those land on the same horizontal band. Anchoring the match would leave
     * the band unclassified, and the cart total sits in the right-hand price column, so it
     * would then be read as a line price and open an item block for a product that does
     * not exist.
     */
    private static final Pattern APP_BAR_DATE =
            Pattern.compile("([A-Za-z]{3,9}) (\\d{1,2}), (\\d{4}) order");

    private static final DateTimeFormatter SHORT_MONTH =
            DateTimeFormatter.ofPattern("MMM d yyyy", Locale.US);
    private static final DateTimeFormatter LONG_MONTH =
            DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.US);
    private static final DateTimeFormatter LABEL_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd", Locale.US);

    private OrderFieldExtractor() {
    }

    /**
     * SPEC 8.6.3. Exact label matching, which is also how SPEC 8.6.8 is satisfied:
     * "Subtotal" contains "total", so a substring test would misread the subtotal as the
     * order total and break every downstream figure.
     */
    static OrderField labelOf(String leftText) {
        String label = ChromeFilter.normalise(leftText);
        if (label.isEmpty()) {
            return null;
        }
        switch (label) {
            case "subtotal":
            case "sub total":
                return OrderField.SUBTOTAL;
            case "tax":
            case "taxes":
            case "estimated taxes":
                return OrderField.TAX;
            case "tip":
            case "driver tip":
                return OrderField.TIP;
            case "delivery fee":
            case "free delivery from store":
            case "shipping":
                return OrderField.DELIVERY_FEE;
            case "service fee":
            case "bag fee":
            case "below minimum fee":
                return OrderField.OTHER_FEE;
            case "savings":
            case "discount":
            case "promo":
            case "promo code":
                return OrderField.DISCOUNT;
            case "total":
                return OrderField.TOTAL;
            default:
                return null;
        }
    }

    /** True when this band belongs to the summary block. */
    static boolean isSummaryLabel(String leftText) {
        return labelOf(leftText) != null;
    }

    /** SPEC 8.6.1: parses the app-bar title into epoch millis, or null. */
    static Long orderDateMillis(String bandText) {
        Matcher matcher = APP_BAR_DATE.matcher(bandText == null ? "" : bandText.trim());
        if (!matcher.find()) {
            return null;
        }
        LocalDate date = parseDate(matcher.group(1), matcher.group(2), matcher.group(3));
        if (date == null) {
            return null;
        }
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** SPEC 8.6.2, e.g. "Sep 03 Walmart". Editable afterwards (SPEC 7.7.1). */
    static String defaultLabel(long epochMillis) {
        LocalDate date = java.time.Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault()).toLocalDate();
        return date.format(LABEL_FORMAT) + " Walmart";
    }

    private static LocalDate parseDate(String month, String day, String year) {
        String candidate = month + " " + day + " " + year;
        try {
            return LocalDate.parse(candidate, month.length() == 3 ? SHORT_MONTH : LONG_MONTH);
        } catch (DateTimeParseException notThatFormat) {
            try {
                return LocalDate.parse(candidate, month.length() == 3 ? LONG_MONTH : SHORT_MONTH);
            } catch (DateTimeParseException notADate) {
                return null;
            }
        }
    }

    /**
     * SPEC 8.6.4: the charged amount is the right-most one on the band, so a struck-through
     * original beside it never wins.
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
