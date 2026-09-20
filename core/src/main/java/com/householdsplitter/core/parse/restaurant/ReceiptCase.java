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
