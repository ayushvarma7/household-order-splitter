package com.householdsplitter.core.parse.restaurant;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Turns a till's shouting into something that reads like a name.
 *
 * <p>Thermal printers have one case. A bill prints CAESAR SALAD and GRILLED SALMON because
 * that is all the hardware can do, not because anybody chose it, and carrying that choice
 * into the app puts a row shouting CAESAR SALAD directly beneath "Organic Bananas, 3 lb"
 * from a grocery order.
 *
 * <p>This is a presentation change and it is applied to restaurant names only. The two
 * screenshot stores print their product names in the case their own designers chose, and
 * second-guessing that would be meddling rather than tidying.
 *
 * <p>Two rules keep it from doing damage:
 *
 * <ul>
 *   <li>A name that already contains a lowercase letter is left exactly as it is. Mixed
 *       case means the till was capable of it and the restaurant used it, and there is
 *       nothing here to improve.
 *   <li>A word on the acronym list is left capital. BBQ, BLT and IPA are initialisms, and
 *       "Bbq Ribs" is a worse answer than the shouting was.
 * </ul>
 *
 * <p>An explicit list rather than a length rule, which is the second version of this. A
 * rule that kept every word of three letters or fewer capitalised turned ICED TEA into
 * "Iced TEA", and a menu is full of short ordinary words: tea, egg, ham, pie, rib, bun,
 * ale, rum, dip, jam. Those are far commoner than acronyms, so the list fails in the
 * cheaper direction: an initialism nobody thought of comes out as "Ipa" rather than every
 * short word on the bill shouting.
 *
 * <p>It will still get things wrong, and the wrongness is bounded and visible: a name is
 * shown to the user on the review screen, next to an edit field, before it reaches a split.
 * Nothing here can touch an amount.
 */
public final class ReceiptCase {

    /**
     * Words that stay lowercase inside a name. Short enough to be caught by the acronym
     * rule otherwise, and "Steak AND Eggs" is not an improvement on "STEAK AND EGGS".
     */
    private static final Set<String> MINOR = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("a", "an", "and", "at", "for", "in", "n", "of", "on", "or",
                    "the", "to", "w", "with")));

    /**
     * Initialisms that appear on menus and bills. Every entry is a word that would read
     * worse capitalised only on its first letter.
     */
    private static final Set<String> ACRONYMS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("bbq", "blt", "byo", "ayce", "ipa", "apa", "neipa", "abv", "ibu",
                    "pb", "pbj", "gf", "df", "vg", "msg", "oj", "bogo", "diy", "vip",
                    "ny", "nyc", "la", "sf", "dc", "uk", "us", "usa", "bc",
                    "xl", "xxl", "sm", "md", "lg", "pc", "pcs", "ea", "dz")));

    private ReceiptCase() {
    }

    /**
     * Drops the parts of a till line that are not what the thing is called.
     *
     * <p>A receipt row carries more than a name. A Burlington line reads
     * {@code CPM253FH68 POWERBLEND HOODIE-MAROON 029 M337784517 1 16.99}, of which the
     * product is three words and the rest is a style code, a SKU, a count and a unit price.
     * All of it sits left of the amount column, so all of it reaches the name.
     *
     * <p>Three things go, and each is recognisable without knowing the shop:
     *
     * <ul>
     *   <li>an amount, because the charged one is taken from the amount column and any
     *       other figure on the row is a unit price or a discount;
     *   <li>a long run of digits, which is a barcode or a receipt number, never a name;
     *   <li>a long token mixing letters and digits, which is a SKU. Short ones are left
     *       alone, because "500ML", "7UP" and "A1" are all real and all look like this.
     * </ul>
     *
     * <p>If that removes everything, the original is kept. A name that reads badly is a
     * great deal better than a row labelled with nothing at all, and the user is looking
     * at a screen built for correcting exactly this.
     */
    public static String withoutCodes(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        String[] tokens = name.trim().split("\\s+");
        int words = 0;
        for (String token : tokens) {
            if (token.matches(".*[A-Za-z]{3,}.*")) {
                words++;
            }
        }
        StringBuilder kept = new StringBuilder(name.length());
        for (String token : tokens) {
            if (isAmount(token) || isLongDigitRun(token) || isProductCode(token)) {
                continue;
            }
            // A bare small integer stranded between words is the count column bleeding into
            // the name: "POWERBLEND 1 HOODIE". The count is captured separately, so it is
            // not being lost here, only stopped from appearing twice.
            //
            // Only when two real words survive without it, because "7 UP" and "PIZZA 12"
            // exist and a name is worth more than a tidy one.
            if (words >= 2 && token.matches("\\d{1,2}")) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append(' ');
            }
            kept.append(token);
        }
        String cleaned = kept.toString().trim();
        return cleaned.isEmpty() ? name : cleaned;
    }

    private static boolean isAmount(String token) {
        return token.matches("-?[\\p{Sc}]?\\d{1,3}(,\\d{3})*\\.\\d{2}[.,;:]?");
    }

    /** Five digits or more with nothing else in them: a barcode, not a word. */
    private static boolean isLongDigitRun(String token) {
        String bare = token.replaceAll("[^0-9]", "");
        return bare.length() >= 5 && bare.length() == token.replaceAll("[^0-9A-Za-z]", "").length();
    }

    /**
     * A long token that mixes letters and digits, which on a receipt is a stock code.
     *
     * <p>Six characters is the floor, and it is chosen to protect real names rather than to
     * catch every code: sizes and product names are routinely alphanumeric and short, and
     * losing "500ML" off a drink is a worse outcome than keeping one SKU.
     */
    private static boolean isProductCode(String token) {
        String bare = token.replaceAll("[^0-9A-Za-z]", "");
        if (bare.length() < 6) {
            return false;
        }
        int digits = 0;
        int letters = 0;
        for (int i = 0; i < bare.length(); i++) {
            if (Character.isDigit(bare.charAt(i))) {
                digits++;
            } else {
                letters++;
            }
        }
        return digits >= 2 && letters >= 1;
    }

    public static String title(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (hasLowerCase(name)) {
            return name;
        }
        String[] words = name.trim().split("\\s+");
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(word(words[i], i == 0));
        }
        return out.toString();
    }

    private static String word(String word, boolean isFirst) {
        if (word.isEmpty()) {
            return word;
        }
        String lower = word.toLowerCase(Locale.US);
        if (!isFirst && MINOR.contains(lower)) {
            return lower;
        }
        if (isAcronym(word)) {
            return word;
        }
        // A word may open with punctuation, as "(GF)" does. Capitalise the first letter
        // wherever it actually is rather than the first character.
        int firstLetter = -1;
        for (int i = 0; i < word.length(); i++) {
            if (Character.isLetter(word.charAt(i))) {
                firstLetter = i;
                break;
            }
        }
        if (firstLetter < 0) {
            return word;
        }
        return word.substring(0, firstLetter)
                + Character.toUpperCase(word.charAt(firstLetter))
                + lower.substring(firstLetter + 1);
    }

    private static boolean isAcronym(String word) {
        StringBuilder letters = new StringBuilder(word.length());
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (Character.isLetter(c)) {
                letters.append(Character.toLowerCase(c));
            }
        }
        return letters.length() > 0 && ACRONYMS.contains(letters.toString());
    }

    private static boolean hasLowerCase(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLowerCase(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
