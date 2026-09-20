package com.householdsplitter.core.parse.restaurant;

import com.householdsplitter.core.parse.layout.ColumnCalibration;
import com.householdsplitter.core.parse.layout.Normalise;
import com.householdsplitter.core.parse.layout.ParseTuning;
import com.householdsplitter.core.parse.layout.StoreVocabulary;
import com.householdsplitter.core.parse.model.OcrElement;
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
 * A printed restaurant bill, photographed rather than screenshotted.
 *
 * <p>This store differs from the other two in kind, not just in wording, and the difference
 * is worth stating plainly because it changes what can be promised. Walmart has one layout.
 * Amazon has one layout. Both were measured off real captures, and their tuning is a record
 * of that measurement. Restaurants have as many layouts as there are point-of-sale systems,
 * and no amount of care here produces a constant that is right for the next one.
 *
 * <p>So this vocabulary asserts as little as possible about position and as much as
 * possible about arithmetic:
 *
 * <ul>
 *   <li>The page is straightened first ({@link #needsDeskew()}), because a photograph has a
 *       tilt and every column rule in the pipeline assumes it does not.
 *   <li>The amount column is measured off the page rather than written down
 *       ({@link #calibrate}), because it is different on every till.
 *   <li>The item region is closed at the subtotal, which every restaurant bill prints and
 *       always prints below the food.
 * </ul>
 *
 * <p>That last point carries more weight here than on either screenshot store. A paper bill
 * states its own subtotal, and the items must add up to it exactly. A screenshot can be
 * cropped mid-list, so a shortfall there is ambiguous; a photograph of a whole bill is not,
 * and a shortfall is proof of a missed row together with its exact value. The reader is
 * arranged around making that check possible, and
 * {@link com.householdsplitter.core.parse.Reconciler} is what performs it.
 *
 * <p>The wording below is the vocabulary of till receipts, not of one restaurant. Where a
 * label is genuinely ambiguous it is left unmatched rather than guessed at, on the same
 * rule the other two vocabularies follow: an unread line becomes a question for the user,
 * a misread one becomes a wrong number nobody notices.
 */
public final class RestaurantVocabulary implements StoreVocabulary {

    public static final String STORE_NAME = "Restaurant";

    /**
     * Furniture. Everything a till prints that is not a dish and not a total.
     *
     * <p>Nearly all of it sits above the food or below the subtotal, where it is already out
     * of the item region, so most of these patterns are a second line of defence rather than
     * the only one. They matter for the cases where the subtotal failed to recognise: with
     * the anchor gone, a payment line carrying an amount in the price column would otherwise
     * open a block and bill somebody for a product called "VISA".
     */
    private static final List<Pattern> CHROME = Collections.unmodifiableList(Arrays.asList(
            // The column header above the items. Matched tightly: a loose pattern here
            // would eat the first dish.
            Pattern.compile("^qty (desc|description|item|items)( amt| amount| price| total)?$"),
            Pattern.compile("^(item|items|description|desc) (amt|amount|price|total)$"),

            // Who served it and where they served it.
            Pattern.compile("^table:? ?[a-z0-9-]{1,6}$"),
            Pattern.compile("^(server|host|cashier|waiter|waitress|attendant|employee)"
                    + ":? ?[a-z0-9 .'-]{0,24}$"),
            Pattern.compile("^(guests?|covers|party|pax):? ?\\d{1,3}$"),
            Pattern.compile("^(dine ?in|take ?out|to ?go|takeaway|delivery|pick ?up|counter)$"),

            // Registration and terminal identifiers.
            Pattern.compile("^tax invoice.*$"),
            Pattern.compile("^(abn|acn|gst|vat|hst|tin|ein)\\b.*$"),
            Pattern.compile("^(terminal|term|store|reg|register|lane|pos|merchant|mid|tid)"
                    + " ?#? ?[a-z0-9-]{0,12}$"),
            Pattern.compile("^(trans|transaction|txn|batch|seq|sequence) ?#? ?[a-z0-9-]{0,14}$"),

            // Payment. SPEC 8.4.3, and the reason Redact exists.
            Pattern.compile("^(visa|master ?card|amex|american express|discover|diners|jcb"
                    + "|unionpay|maestro|interac|debit|credit|card|chip|contactless|tap"
                    + "|swipe|apple pay|google pay|cash|change|tender|tendered|payment"
                    + "|paid|account)\\b.*$"),
            Pattern.compile("^\\[redacted\\].*$"),
            Pattern.compile(".*\\[redacted\\]$"),
            Pattern.compile("^(entry|entry method|mode|cvm|app|application|label)\\b.*$"),
            Pattern.compile("^x[_ -]{3,}$"),

            // Rules, borders and the sign-off.
            Pattern.compile("^[-_=*~.#]{3,}$"),
            Pattern.compile("^\\**\\s*(thank ?you|thanks)[^a-z]*\\s*\\**$"),
            Pattern.compile("^(please )?(come again|visit again|call again).*$"),
            Pattern.compile("^have a (nice|good|great|lovely) (day|evening|night).*$"),
            Pattern.compile("^(customer|merchant|restaurant|store) copy$"),
            Pattern.compile("^(duplicate|reprint|copy|voided|void)$"),
            Pattern.compile("^signature.*$"),
            Pattern.compile("^(tip|gratuity):? ?_{2,}$"),
            Pattern.compile("^(www\\.|http).*$"),
            Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$"),

            // A bare date, time or telephone number on its own line.
            Pattern.compile("^\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}.*$"),
            Pattern.compile("^\\d{1,2}:\\d{2}(:\\d{2})?( ?[ap]\\.?m\\.?)?$"),
            Pattern.compile("^\\(?\\d{3}\\)?[ .-]?\\d{3}[ .-]\\d{4}$"),

            // The suggested-tip table many tills print under the total. Every figure on it
            // is a proposal, not a charge, and it sits in the amount column.
            Pattern.compile("^\\d{1,2}(\\.\\d)?% ?(tip|gratuity)?$"),
            Pattern.compile("^(suggested|suggestion|sample) (tip|gratuity).*$"),
            Pattern.compile("^tip (guide|calculator|suggestions?)$")
    ));

    /**
     * Where the food stops.
     *
     * <p>The subtotal is the anchor, and it is a better one than either screenshot store
     * has. Walmart and Amazon anchor on a card header whose wording could change with a
     * redesign; a printed bill states its subtotal because the arithmetic requires it, and
     * has done on every till receipt for decades.
     *
     * <p>The total labels are here as well, for the small bill that prints no subtotal at
     * all. Without them a two-item lunch would leave "TOTAL 18.40" inside the item region,
     * where it has an amount in the amount column and nothing else to be, and the split
     * would charge somebody 18.40 for a dish called "TOTAL" on top of the two real ones.
     */
    private static final List<Pattern> ENDS_ITEMS = Collections.unmodifiableList(Arrays.asList(
            Pattern.compile("^sub ?-? ?total$"),
            Pattern.compile("^(food|item|items|order) sub ?-? ?total$"),
            Pattern.compile("^(grand |order |check |net )?total$"),
            Pattern.compile("^(amount|balance|total) due$"),
            Pattern.compile("^amount$"),
            Pattern.compile("^total amount$")
    ));

    /** "CHECK #1042", "Order # 77", "Ticket 12". */
    private static final Pattern ORDER_NUMBER = Pattern.compile(
            "^(?:check|chk|ticket|tkt|order|receipt|invoice|bill|trans)\\s*#?\\s*:?\\s*"
                    + "([a-z0-9][a-z0-9-]{0,11})$");

    /** "1 CAPUCCINO", "2 Caesar Salad". The count column, run together by the recogniser. */
    private static final Pattern QUANTITY_PREFIX =
            Pattern.compile("(?i)^(\\d{1,2})\\s*[x*]?\\s+([a-z].*)$");

    /** "Qty: 2", "x2", "2x" standing alone. */
    private static final Pattern QUANTITY_ALONE =
            Pattern.compile("^(?:qty|quantity)?\\s*:?\\s*(?:x\\s*)?(\\d{1,2})\\s*x?$");

    /** "2 @ 4.00", the unit rate line beneath a multiple. */
    private static final Pattern QUANTITY_AT_RATE =
            Pattern.compile("^(\\d{1,2})\\s*@\\s*\\$?[\\d,.]+$");

    /**
     * Kitchen instructions printed under a dish. They describe how it was made, not what it
     * is called, and a row that reads "Caesar Salad NO CROUTONS ADD CHICKEN" is a worse
     * answer than "Caesar Salad".
     *
     * <p>A modifier that was charged for prints its own amount, which opens its own block,
     * so nothing here can lose money: these only ever match lines that had no price of their
     * own to begin with.
     */
    private static final List<Pattern> MODIFIERS = Collections.unmodifiableList(Arrays.asList(
            Pattern.compile("^(no|without|hold|sub|substitute|add|extra|side of|w/|with|less"
                    + "|light|easy|only|plus|minus) .{1,40}$"),
            Pattern.compile("^(on the side|to share|for the table|split|shared)$"),
            Pattern.compile("^(rare|medium|medium rare|medium well|well done|blue)$"),
            Pattern.compile("^\\*+.{0,40}$"),
            Pattern.compile("^(\\d{1,2})\\s*@\\s*\\$?[\\d,.]+$"),
            Pattern.compile("^seat ?\\d{1,2}$")
    ));

    private static final Pattern CURRENCY_SUFFIX =
            Pattern.compile("\\s*\\((?:[a-z]{1,4}|[\\p{Sc}])\\)\\s*$");

    private static final DateTimeFormatter LABEL_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd", Locale.US);

    private static final DateTimeFormatter SHORT_MONTH = new DateTimeFormatterBuilder()
            .parseCaseInsensitive().appendPattern("MMM d yyyy").toFormatter(Locale.US);

    private static final DateTimeFormatter LONG_MONTH = new DateTimeFormatterBuilder()
            .parseCaseInsensitive().appendPattern("MMMM d yyyy").toFormatter(Locale.US);

    /** "Sep 4, 2024" and "4 September 2024", in either order. */
    private static final Pattern NAMED_MONTH_FIRST =
            Pattern.compile("(?i)\\b([a-z]{3,9})\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s+(\\d{4})\\b");

    private static final Pattern DAY_FIRST =
            Pattern.compile("(?i)\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+([a-z]{3,9})\\.?,?\\s+(\\d{4})\\b");

    /** "9/4/2024", "04-09-24". */
    private static final Pattern NUMERIC_DATE =
            Pattern.compile("\\b(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})\\b");

    /** "2024-09-04". Unambiguous, so it is tried first. */
    private static final Pattern ISO_DATE =
            Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");

    private final ParseTuning tuning;

    public RestaurantVocabulary() {
        this(ParseTuning.restaurant());
    }

    public RestaurantVocabulary(ParseTuning tuning) {
        this.tuning = tuning == null ? ParseTuning.restaurant() : tuning;
    }

    @Override
    public String storeName() {
        return STORE_NAME;
    }

    @Override
    public ParseTuning tuning() {
        return tuning;
    }

    /** Paper, held in a hand. */
    @Override
    public boolean needsDeskew() {
        return true;
    }

    /** Every till is a different width, so the columns are read off the page. */
    @Override
    public ParseTuning calibrate(List<OcrElement> page, ParseTuning store) {
        return ColumnCalibration.against(page, store);
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

    /** No restaurant bill advertises anything. */
    @Override
    public boolean isCarouselMarker(String text) {
        return false;
    }

    @Override
    public String orderNumberIn(String text) {
        Matcher matcher = ORDER_NUMBER.matcher(Normalise.text(text));
        if (!matcher.matches()) {
            return null;
        }
        String number = matcher.group(1);
        // A bare word is a name, not a number: "CHECK CLOSED" is not check number "closed".
        return number.matches(".*\\d.*") ? number.toUpperCase(Locale.US) : null;
    }

    /**
     * The date printed on the bill.
     *
     * <p>ISO first because it is unambiguous, then a named month because it is too, then the
     * numeric form. A numeric date is read month first, which is the United States
     * convention and this app's audience. It is a genuine guess and the only one in this
     * class: 04/09/2024 is April 9th here and September 4th in most of the world. It is
     * survivable in a way the other guesses would not be, because a wrong date mislabels an
     * order in a list and a wrong amount misbills a person, and the user can see and correct
     * the date on the review screen.
     */
    @Override
    public Long orderDateMillis(String text) {
        String raw = text == null ? "" : text.trim();
        LocalDate date = isoDate(raw);
        if (date == null) {
            date = namedMonthDate(raw);
        }
        if (date == null) {
            date = numericDate(raw);
        }
        if (date == null) {
            return null;
        }
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDate isoDate(String raw) {
        Matcher matcher = ISO_DATE.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        return dateOf(Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
    }

    private static LocalDate namedMonthDate(String raw) {
        Matcher matcher = NAMED_MONTH_FIRST.matcher(raw);
        if (matcher.find()) {
            LocalDate parsed = byMonthName(matcher.group(1), matcher.group(2), matcher.group(3));
            if (parsed != null) {
                return parsed;
            }
        }
        matcher = DAY_FIRST.matcher(raw);
        if (matcher.find()) {
            return byMonthName(matcher.group(2), matcher.group(1), matcher.group(3));
        }
        return null;
    }

    private static LocalDate byMonthName(String month, String day, String year) {
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

    private static LocalDate numericDate(String raw) {
        Matcher matcher = NUMERIC_DATE.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        int first = Integer.parseInt(matcher.group(1));
        int second = Integer.parseInt(matcher.group(2));
        int year = Integer.parseInt(matcher.group(3));
        if (year < 100) {
            year += 2000;
        }
        // Month first, except where that is impossible and the other reading is not.
        if (first > 12 && second <= 12) {
            return dateOf(year, second, first);
        }
        return dateOf(year, first, second);
    }

    private static LocalDate dateOf(int year, int month, int day) {
        if (month < 1 || month > 12 || day < 1 || day > 31 || year < 2000 || year > 2100) {
            return null;
        }
        try {
            return LocalDate.of(year, month, day);
        } catch (java.time.DateTimeException notADate) {
            return null;
        }
    }

    @Override
    public String defaultLabel(long epochMillis) {
        LocalDate date = java.time.Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault()).toLocalDate();
        return date.format(LABEL_FORMAT) + " " + STORE_NAME;
    }

    /**
     * The summary block, matched exactly.
     *
     * <p>Three of these contain the word "total" and only one of them is the bill. A
     * substring test would take whichever printed last, which on a bill that states both a
     * pre-tip total and an amount due is a coin toss over the tip.
     *
     * <p>A tip label is read and kept even though the user is asked for the tip anyway, on
     * the observed grounds that a bill printed after the tip was entered states the real one
     * and a bill printed before it does not. Reading it costs nothing and saves typing when
     * it is there.
     */
    @Override
    public OrderField summaryLabelOf(String leftText) {
        String label = currencySuffix(Normalise.text(leftText));
        if (label.isEmpty()) {
            return null;
        }
        switch (label) {
            case "subtotal":
            case "sub total":
            case "sub-total":
            case "food subtotal":
            case "item subtotal":
            case "items subtotal":
            case "order subtotal":
            case "total sales (excluding gst)":
            case "total excluding gst":
                return OrderField.SUBTOTAL;
            case "tax":
            case "taxes":
            case "sales tax":
            case "sale tax":
            case "state tax":
            case "local tax":
            case "city tax":
            case "tax total":
            case "total tax":
            case "gst":
            case "hst":
            case "pst":
            case "qst":
            case "vat":
                return OrderField.TAX;
            case "tip":
            case "tips":
            case "gratuity":
            case "tip amount":
            case "gratuity amount":
                return OrderField.TIP;
            case "service charge":
            case "service chg":
            case "svc charge":
            case "service fee":
            case "auto gratuity":
            case "autograt":
            case "auto grat":
            case "admin fee":
            case "kitchen fee":
            case "delivery fee":
            // A rounding adjustment is a real charge of a cent or two, up or down, and is
            // the difference between a bill that reconciles and one that is off by one.
            case "rounding":
            case "rounding adj":
            case "rounding adjustment":
                return OrderField.OTHER_FEE;
            case "discount":
            case "discounts":
            case "comp":
            case "comps":
            case "promo":
            case "promotion":
            case "coupon":
            case "savings":
                return OrderField.DISCOUNT;
            case "total":
            case "grand total":
            case "order total":
            case "check total":
            case "net total":
            case "total amount":
            case "total amt":
            case "amount":
            case "amount due":
            case "amount payable":
            case "amount paid":
            case "balance due":
            case "total due":
            case "nett total":
            case "net amount":
            case "rounded total":
            case "total rounded":
            case "total sales (inclusive of gst)":
            case "total inclusive of gst":
                return OrderField.TOTAL;
            default:
                return null;
        }
    }

    /**
     * Strips the currency a till prints after a summary label.
     *
     * <p>"Rounded Total (RM)", "Total ($)", "Amount (USD)". The parenthetical says which
     * currency the column is in, which is a fact about the column and not about the label,
     * and leaving it on means every one of these has to be written out once per currency.
     *
     * <p>Only a short bracketed run of letters and currency symbols at the very end, so
     * "Total Sales (Inclusive of GST)" is not mistaken for one: that parenthetical changes
     * which figure the label refers to and has to be matched, not discarded.
     */
    private static String currencySuffix(String label) {
        return CURRENCY_SUFFIX.matcher(label).replaceAll("").trim();
    }

    /**
     * Course headers are not read.
     *
     * <p>Some tills group a bill into APPETIZERS, ENTREES and DRINKS and most do not, and
     * the words they use for it are the words dishes are also named with. A header carries
     * no amount, so it opens no block and costs nothing by being ignored; matching it
     * wrongly would silently retitle a dish's section.
     */
    @Override
    public String sectionNameOf(String text) {
        return null;
    }

    @Override
    public int deliveredUnitCount(String text) {
        return -1;
    }

    /**
     * Nothing is excluded by section, because nothing is grouped into sections.
     *
     * <p>A voided item is normally not printed at all: the till removes it before the bill
     * is produced. Where a void does print, it prints with a negative amount, which is
     * charged as a negative and comes out right.
     */
    @Override
    public boolean isExcludedSection(String sectionName) {
        return false;
    }

    /**
     * The count, whether it is printed in its own column or run into the dish name.
     *
     * <p>"2 CAESAR SALAD" is read as two Caesar salads. "2 EGGS" is read the same way, which
     * is wrong, and is worth being precise about: the quantity is a display figure. What is
     * charged comes from the amount column and is unaffected, so the cost of this mistake is
     * a "2" beside a name on the review screen, not a wrong bill.
     */
    @Override
    public int quantityIn(String line) {
        String normalised = Normalise.text(line);
        Matcher alone = QUANTITY_ALONE.matcher(normalised);
        if (alone.matches()) {
            return clamp(alone.group(1));
        }
        Matcher atRate = QUANTITY_AT_RATE.matcher(normalised);
        if (atRate.matches()) {
            return clamp(atRate.group(1));
        }
        Matcher prefix = QUANTITY_PREFIX.matcher(normalised);
        if (prefix.matches()) {
            return clamp(prefix.group(1));
        }
        return -1;
    }

    /** A printed count, bounded. A till that prints "99" means 99; one that prints 400
     * has been misread, and a quantity that large would dominate every display it reaches. */
    private static int clamp(String digits) {
        try {
            return Math.max(1, Math.min(99, Integer.parseInt(digits)));
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    /**
     * What is left of the line once the count in front of it has been taken off, or null
     * when the line was nothing but a count.
     */
    @Override
    public String nameAfterQuantity(String line) {
        Matcher prefix = QUANTITY_PREFIX.matcher(line == null ? "" : line.trim());
        String remainder = null;
        if (prefix.matches()) {
            remainder = prefix.group(2).trim();
        } else {
            // Case is preserved above where possible, because the name reaches the user.
            Matcher lowered = QUANTITY_PREFIX.matcher(Normalise.text(line));
            if (lowered.matches()) {
                remainder = lowered.group(2).trim();
            }
        }
        // "1 PC 9.00 0.00" is a count, a unit, a price and a discount. Stripping the count
        // leaves "PC 9.00 0.00", which is not what the thing was called, and on a till that
        // prints the description on the line above it is not the name of anything at all.
        return namesSomething(remainder) ? remainder : null;
    }

    /** True when a string contains a word long enough to be a product rather than a unit. */
    private static boolean namesSomething(String text) {
        if (text == null) {
            return false;
        }
        int run = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) {
                if (++run >= 3) {
                    return true;
                }
            } else {
                run = 0;
            }
        }
        return false;
    }

    /** Plenty of tills print the description above the figures. See the interface. */
    @Override
    public boolean nameMayPrecedePrice() {
        return true;
    }

    /** A till prints in capitals because it has no choice. See {@link ReceiptCase}. */
    @Override
    public String presentName(String name) {
        return ReceiptCase.title(ReceiptCase.withoutCodes(name));
    }

    @Override
    public boolean isRowMetadata(String line) {
        String normalised = Normalise.text(line);
        for (Pattern pattern : MODIFIERS) {
            if (pattern.matcher(normalised).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Always -1. A restaurant bill prints one amount per line.
     *
     * <p>This figure exists to prove that a larger amount printed beside a charged one is a
     * struck-through original. Tills do not strike anything through: a discount is printed
     * as its own negative line, which is read as a discount and needs no proof.
     */
    @Override
    public long savingsCentsIn(String line) {
        return -1L;
    }
}
