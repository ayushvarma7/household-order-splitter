package com.householdsplitter.core.suggest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns a product name into the key that assignment memory is looked up by. SPEC 9.1.
 *
 * <p>SPEC 7.9.13 and 9.4 are the constraint that shapes this class: there is no shipped
 * list of which groceries are "usually shared", and no global knowledge of brands either.
 * The brand prefixes stripped here are <em>inferred from this household's own confirmed
 * history</em> and from nothing else.
 */
public final class NameNormalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Trailing size and pack fragments, e.g. ", 8 oz Bag" or ", 24 oz". */
    private static final Pattern TRAILING_SIZE = Pattern.compile(
            "(?i)[,\\s]+\\d+(\\.\\d+)?\\s?(fl\\s?oz|oz|lb|lbs|ct|count|pk|pack|gal|qt|kg|g|ml|l)\\b"
                    + "(\\s+(bag|box|pack|bottle|can|jar|carton|container|tub|each|ea))?\\s*$");

    /** A trailing count fragment such as ", 12 Count" or " (6 pack)". */
    private static final Pattern TRAILING_COUNT = Pattern.compile(
            "(?i)[,\\s]*\\(?\\d+\\s?(count|ct|pack|pk)\\)?\\s*$");

    private static final Pattern PUNCTUATION_TAIL = Pattern.compile("[\\s,.;:\\-]+$");

    private NameNormalizer() {
    }

    /** Normalisation without brand stripping, for a household with no history yet. */
    public static String normalize(String name) {
        return normalize(name, null);
    }

    /**
     * @param brandPrefixes prefixes inferred from this household's history, or null
     */
    public static String normalize(String name, Collection<String> brandPrefixes) {
        if (name == null) {
            return "";
        }
        String value = WHITESPACE.matcher(name.trim().toLowerCase()).replaceAll(" ");
        if (value.isEmpty()) {
            return "";
        }

        value = stripBrandPrefix(value, brandPrefixes);

        // Repeat, because Walmart names carry both a size and a pack fragment.
        String previous;
        do {
            previous = value;
            value = TRAILING_SIZE.matcher(value).replaceAll("");
            value = TRAILING_COUNT.matcher(value).replaceAll("");
            value = PUNCTUATION_TAIL.matcher(value).replaceAll("");
        } while (!value.equals(previous));

        value = collapsePlurals(value);
        return WHITESPACE.matcher(value.trim()).replaceAll(" ");
    }

    private static String stripBrandPrefix(String value, Collection<String> brandPrefixes) {
        if (brandPrefixes == null || brandPrefixes.isEmpty()) {
            return value;
        }
        // Longest prefix first, so "great value organic" beats "great value".
        List<String> ordered = new ArrayList<>(brandPrefixes);
        ordered.sort(Comparator.comparingInt(String::length).reversed());
        for (String prefix : ordered) {
            String candidate = prefix.trim().toLowerCase();
            if (candidate.isEmpty()) {
                continue;
            }
            if (value.startsWith(candidate + " ") && value.length() > candidate.length() + 1) {
                return value.substring(candidate.length() + 1).trim();
            }
        }
        return value;
    }

    /** SPEC 9.1: "collapse simple plurals". Deliberately simple, and only at word ends. */
    private static String collapsePlurals(String value) {
        String[] words = value.split(" ");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            String singular = word;
            if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss")
                    && !word.endsWith("us") && !word.endsWith("is")) {
                if (word.endsWith("ies") && word.length() > 4) {
                    singular = word.substring(0, word.length() - 3) + "y";
                } else if (word.endsWith("es") && word.length() > 4
                        && (word.endsWith("ches") || word.endsWith("shes") || word.endsWith("xes"))) {
                    singular = word.substring(0, word.length() - 2);
                } else {
                    singular = word.substring(0, word.length() - 1);
                }
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(singular);
        }
        return out.toString();
    }

    /**
     * SPEC 9.1: "strip a leading brand prefix where it repeats across the household's
     * history". A prefix counts as a brand only once this household has bought it under
     * several different names.
     *
     * @param historyNames every item name this household has confirmed
     * @param minDistinctUses how many different products must share the prefix
     */
    public static Set<String> inferBrandPrefixes(Collection<String> historyNames,
                                                 int minDistinctUses) {
        Map<String, Set<String>> tails = new HashMap<>();
        if (historyNames != null) {
            for (String raw : historyNames) {
                if (raw == null) {
                    continue;
                }
                String value = WHITESPACE.matcher(raw.trim().toLowerCase()).replaceAll(" ");
                String[] words = value.split(" ");
                for (int length = 1; length <= Math.min(3, words.length - 1); length++) {
                    StringBuilder prefix = new StringBuilder();
                    for (int i = 0; i < length; i++) {
                        if (i > 0) {
                            prefix.append(' ');
                        }
                        prefix.append(words[i]);
                    }
                    tails.computeIfAbsent(prefix.toString(), key -> new LinkedHashSet<>())
                            .add(value.substring(prefix.length()).trim());
                }
            }
        }
        Set<String> candidates = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : tails.entrySet()) {
            if (entry.getValue().size() >= minDistinctUses) {
                candidates.add(entry.getKey());
            }
        }

        // "great" and "great value" both qualify when every purchase says "Great Value",
        // but only the longer one is the brand. Drop a prefix that a longer prefix
        // subsumes with the same reach, so stripping never leaves a fragment like "value".
        Set<String> brands = new LinkedHashSet<>();
        for (String candidate : candidates) {
            boolean subsumed = false;
            for (String other : candidates) {
                if (other.length() > candidate.length()
                        && other.startsWith(candidate + " ")
                        && tails.get(other).size() == tails.get(candidate).size()) {
                    subsumed = true;
                    break;
                }
            }
            if (!subsumed) {
                brands.add(candidate);
            }
        }
        return brands;
    }
}
