package com.householdsplitter.core.parse.amazon;

import com.householdsplitter.core.parse.layout.Normalise;
import com.householdsplitter.core.parse.layout.ParseTuning;
import com.householdsplitter.core.parse.layout.StoreVocabulary;
import com.householdsplitter.core.parse.model.OrderField;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Amazon Fresh's wording, read off real order pages.
 *
 * <p>Every pattern below matches something printed on a screenshot that was actually
 * examined. Nothing is here because Amazon might say it: a label that never appears does
 * nothing useful, and a label invented to look thorough can match something else and drop
 * or invent a charge. Where Amazon's behaviour has not been observed, this class says so
 * and declines to answer rather than guessing.
 *
 * <p>What differs most from Walmart is not the item rows, which are near enough identical
 * in structure, but the summary block. Amazon labels it differently on every line
 * ("Item(s) Subtotal", "Grand Total", "Estimated tax to be collected"), and it prints a
 * line, "Total before tax", that looks like a total and is not a charge at all. Reading
 * those labels is the whole job: they feed
 * {@link com.householdsplitter.core.parse.Reconciler}, which is the only arithmetic check
 * on a parse, and a summary that fails to read leaves that check comparing zero to zero.
 */
public final class AmazonFreshVocabulary implements StoreVocabulary {

    /** Shown on the review screen. Nothing branches on it. */
    public static final String STORE_NAME = "Amazon Fresh";

    private static final List<Pattern> CHROME = Collections.unmodifiableList(Arrays.asList(
            // The pinned search bar, which survives a permille header crop on a capture
            // that is a crop rather than a full screen.
            Pattern.compile("^search or ask a question$"),

            // Card headers and the delivery block.
            Pattern.compile("^order summary$"),
            Pattern.compile("^payment method$"),
            Pattern.compile("^delivery instructions$"),
            Pattern.compile("^none provided$"),
            Pattern.compile("^view related transactions$"),
            Pattern.compile("^items? in your order( \\(\\d+\\))?$"),

            // Masked payment lines, SPEC 8.4.3.
            Pattern.compile("^visa\\b.*$"),
            Pattern.compile("^mastercard\\b.*$"),
            Pattern.compile("^amex\\b.*$"),
            Pattern.compile("^discover\\b.*$"),
            Pattern.compile("^ebt\\b.*$"),
            Pattern.compile(".*\\bending in \\d{3,4}$"),

            // "Total before tax" is the subtotal plus fees restated. It is not a charge and
            // it is not the order total, and it is discarded here explicitly rather than
            // left unrecognised, so that nobody later mistakes the silence for an oversight.
            // Matching it as a summary label would be worse: it would overwrite the real
            // total, because it is printed above "Grand Total".
            Pattern.compile("^total before tax$"),

            // The subscriber savings banner. It states a dollar amount that was never
            // charged, in body text wide enough to reach the price column.
            Pattern.compile("^you'?re saving \\$?[\\d,.]+ on this order.*$"),
            Pattern.compile("^includes deals & discounts on items and delivery savings$"),

            // The repeat-items carousel, which shows products that are not in this order.
            Pattern.compile("^save time with repeat items$"),
            Pattern.compile("^purchased [a-z]{3,9} ?\\d{4}$"),

            // The status bar, SPEC 8.4.7.
            Pattern.compile("^\\d{1,2}:\\d{2}(:\\d{2})?( ?[ap]m)?$"),
            Pattern.compile("^\\d{1,3}%$"),
            Pattern.compile("^lte$|^5g$|^4g$|^wi-?fi$")
    ));

    /**
     * SPEC 8.4.2 and 8.6.3: the purchased rows end here.
     *
     * <p>Anchored on the card headers that always sit below the list, not on the first
     * summary label that happens to be recognised. On the observed pages "Item(s) Subtotal"
     * is printed above "Delivery Fee", so a parser that stopped at its first recognised
     * label would leave the subtotal inside the item region, where it has an amount in the
     * price column and nothing else to be: it opens a block and bills the household $38.93
     * for a product called "Item(s) Subtotal".
     */
    private static final List<Pattern> ENDS_ITEMS = Collections.unmodifiableList(Arrays.asList(
            Pattern.compile("^order summary$"),
            Pattern.compile("^payment method$")
    ));

    /** "Order #: 111-0000000-0000000". The colon is Amazon's; Walmart prints none. */
    private static final Pattern ORDER_NUMBER =
            Pattern.compile("^order ?#:? ?([0-9][0-9 -]{5,})$");

    /**
     * "Ordered September 6, 2026 6:02PM".
     *
     * <p>Searched for rather than anchored, because the band also carries the time, and the
     * word comes before the date here where Walmart's comes after it ("Sep 03, 2026 order").
     */
    private static final Pattern ORDERED_DATE =
            Pattern.compile("(?i)\\bordered\\s+([A-Za-z]{3,9})\\s+(\\d{1,2}),?\\s+(\\d{4})");

    /** "Qty: 2", on its own line beneath the name. */
    private static final Pattern QTY = Pattern.compile("^qty:? (\\d+)$");

    /**
     * "Weight: 2.08 lb ($3.49/lb)" on a weighed item, and the note Amazon adds when the
     * pack that arrived was not the pack that was estimated, "Weight adjusted from est.
     * 2.00 lb".
     *
     * <p>Both describe the row rather than name the product, so both are stripped from the
     * name and kept in the row's raw text. They matter because the charged amount is the
     * weight times the rate: 2.08 lb at $3.49/lb is $7.2592, printed and charged as $7.26.
     * The rate is in the name column and so is never eligible to be charged, which is the
     * existing rule for a unit price and needs no exception here.
     */
    private static final Pattern WEIGHT_LINE = Pattern.compile(
            "^weight:? [\\d.]+ ?(lb|lbs|oz|kg|g)\\b.*$");

    private static final Pattern WEIGHT_ADJUSTED = Pattern.compile(
            "^weight adjusted from est\\.? [\\d.]+ ?(lb|lbs|oz|kg|g)$");

    /** "Items in your order (18)", which opens the list. */
    private static final Pattern ITEMS_HEADER =
            Pattern.compile("^items? in your order \\((\\d+)\\)$");

    private static final DateTimeFormatter MONTH_DAY_YEAR = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMMM d yyyy")
            .toFormatter(Locale.US);

    private static final DateTimeFormatter SHORT_MONTH_DAY_YEAR = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMM d yyyy")
            .toFormatter(Locale.US);

    private static final DateTimeFormatter LABEL_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd", Locale.US);

    private final ParseTuning tuning;

    public AmazonFreshVocabulary() {
        this(ParseTuning.amazonFresh());
    }

    public AmazonFreshVocabulary(ParseTuning tuning) {
        this.tuning = tuning == null ? ParseTuning.amazonFresh() : tuning;
    }

    @Override
    public String storeName() {
        return STORE_NAME;
    }

    @Override
    public ParseTuning tuning() {
        return tuning;
    }

    @Override
    public boolean isChrome(String text) {
        String normalised = Normalise.text(text);
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

    @Override
    public boolean endsItemRegion(String text) {
        String normalised = Normalise.text(text);
        for (Pattern pattern : ENDS_ITEMS) {
            if (pattern.matcher(normalised).matches()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isCarouselMarker(String text) {
        String normalised = Normalise.text(text);
        return normalised.equals("save time with repeat items")
                || normalised.matches("^purchased [a-z]{3,9} ?\\d{4}$");
    }

    @Override
    public String orderNumberIn(String text) {
        Matcher matcher = ORDER_NUMBER.matcher(Normalise.text(text));
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1).replace(" ", "").trim();
    }

    @Override
    public Long orderDateMillis(String text) {
        Matcher matcher = ORDERED_DATE.matcher(text == null ? "" : text.trim());
        if (!matcher.find()) {
            return null;
        }
        LocalDate date = parseDate(matcher.group(1), matcher.group(2), matcher.group(3));
        if (date == null) {
            return null;
        }
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDate parseDate(String month, String day, String year) {
        String candidate = month + " " + day + " " + year;
        DateTimeFormatter first = month.length() == 3 ? SHORT_MONTH_DAY_YEAR : MONTH_DAY_YEAR;
        DateTimeFormatter second = month.length() == 3 ? MONTH_DAY_YEAR : SHORT_MONTH_DAY_YEAR;
        try {
            return LocalDate.parse(candidate, first);
        } catch (DateTimeParseException notThatFormat) {
            try {
                return LocalDate.parse(candidate, second);
            } catch (DateTimeParseException notADate) {
                return null;
            }
        }
    }

    @Override
    public String defaultLabel(long epochMillis) {
        LocalDate date = java.time.Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault()).toLocalDate();
        return date.format(LABEL_FORMAT) + " " + STORE_NAME;
    }

    /**
     * SPEC 8.6.3, matched exactly rather than by substring.
     *
     * <p>Three of these labels contain the word "total" and only one of them is the order
     * total. "Item(s) Subtotal" is the items alone, "Total before tax" is those plus fees
     * and is discarded as chrome, and "Grand Total" is what was billed. A substring test on
     * "total" would pick whichever came last and be wrong about the bill.
     */
    @Override
    public OrderField summaryLabelOf(String leftText) {
        String label = Normalise.text(leftText);
        if (label.isEmpty()) {
            return null;
        }
        switch (label) {
            case "item(s) subtotal":
            case "items subtotal":
            case "item subtotal":
            case "subtotal":
                return OrderField.SUBTOTAL;
            case "estimated tax to be collected":
            case "estimated tax":
            case "tax":
                return OrderField.TAX;
            case "delivery fee":
            case "shipping":
                return OrderField.DELIVERY_FEE;
            case "driver tip":
            case "tip":
                return OrderField.TIP;
            case "service fee":
            case "bag fee":
            case "bottle deposit":
                return OrderField.OTHER_FEE;
            case "promotion applied":
            case "promotions applied":
            case "coupon savings":
                return OrderField.DISCOUNT;
            case "grand total":
            case "order total":
                return OrderField.TOTAL;
            default:
                return null;
        }
    }

    /**
     * "Items in your order (18)" is treated as a section header so that it leaves the item
     * region cleanly, and nothing more is read from it.
     *
     * <p>The count is deliberately not returned by {@link #deliveredUnitCount}. Walmart's
     * "33 items delivered" is documented as a unit count, and whether Amazon's number
     * counts rows or units has not been established from a complete order. A count whose
     * meaning is unknown cannot help a user and could mislead one, so it is dropped.
     */
    @Override
    public String sectionNameOf(String text) {
        Matcher matcher = ITEMS_HEADER.matcher(Normalise.text(text));
        return matcher.matches() ? "in your order" : null;
    }

    @Override
    public int deliveredUnitCount(String text) {
        return -1;
    }

    /**
     * No Amazon Fresh section defaults to excluded.
     *
     * <p>Walmart groups an order into delivered, substituted, unavailable and refunded
     * sections, and the last two are rows nobody should be charged for. The observed Amazon
     * pages carry one flat list under a single header, with no such grouping, so there is
     * nothing here to exclude. If Amazon does print an unavailable group, its rows will
     * arrive unassigned and visible rather than silently dropped, which is the safe
     * direction for this to be wrong in.
     */
    @Override
    public boolean isExcludedSection(String sectionName) {
        return false;
    }

    @Override
    public int quantityIn(String line) {
        Matcher matcher = QTY.matcher(Normalise.text(line));
        if (!matcher.matches()) {
            return -1;
        }
        try {
            return Math.max(1, Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    @Override
    public boolean isRowMetadata(String line) {
        String normalised = Normalise.text(line);
        return WEIGHT_LINE.matcher(normalised).matches()
                || WEIGHT_ADJUSTED.matcher(normalised).matches();
    }

    /**
     * Always -1, because no observed Amazon Fresh row prints a savings figure.
     *
     * <p>This is the honest answer rather than a placeholder. The savings amount exists to
     * prove that a larger amount printed beside a charged one is a struck-through original
     * and may be discarded. Amazon does print a struck-through original, on the delivery
     * fee line, but it prints it on the <em>same</em> band as the charged amount, where the
     * right-most-amount rule already resolves it and no proof is needed. A struck original
     * on its own band, which is the Walmart case this figure was introduced for, has not
     * been seen here.
     *
     * <p>The consequence of returning -1 is that such a row, if Amazon ever prints one,
     * opens a second visible line the user can delete, instead of a charge disappearing
     * without trace. Given the two ways to be wrong, that is the one to choose.
     */
    @Override
    public long savingsCentsIn(String line) {
        return -1L;
    }
}
