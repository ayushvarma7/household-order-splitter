package com.householdsplitter.core.insight;

import com.householdsplitter.core.suggest.NameNormalizer;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Everything bought, folded into a tree: all groceries, then categories, then
 * sub-categories, then single items, each with this month's spending, last month's, and
 * who it was for.
 *
 * <p>Every number is {@code long} cents and every ratio is an integer, so a bubble's
 * figure is exactly the sum of the bubbles inside it. That is what lets the picture zoom
 * without a total ever changing as you look closer.
 *
 * <p>Only things bought in the chosen month become bubbles, because a bubble's size is its
 * spending and a thing that was not bought has none. Last month is still counted in full,
 * so a category that shrank because one item disappeared reads as down rather than steady.
 */
public final class InsightTree {

    /** SPEC 9.1, as in assignment memory: a prefix is a brand once three products share it. */
    private static final int BRAND_MIN_DISTINCT_USES = 3;

    /** One bubble. */
    public static final class Node {

        private final String name;
        private final Node parent;
        private final int depth;
        private final List<Node> children = new ArrayList<>();
        private long thisCents;
        private long lastCents;
        private int purchaseCount;
        private final Map<Long, Long> thisByMember = new TreeMap<>();
        private final Map<String, Node> childByName = new LinkedHashMap<>();

        Node(String name, Node parent, int depth) {
            this.name = name;
            this.parent = parent;
            this.depth = depth;
        }

        public String name() {
            return name;
        }

        /**
         * True for the missing-money bubble and everything inside it. That money is a gap in
         * the rows, not something bought, so "up 8000% on last month" says nothing true
         * about it.
         */
        public boolean isMissing() {
            for (Node n = this; n != null; n = n.parent) {
                if (n.depth == 1 && ItemCategorizer.MISSING.equals(n.name)) {
                    return true;
                }
            }
            return false;
        }

        /** Null for the root. */
        public Node parent() {
            return parent;
        }

        /** 0 for the root, 1 for a category, 2 for a sub-category, 3 for an item. */
        public int depth() {
            return depth;
        }

        /** Biggest first, and only things bought this month. */
        public List<Node> children() {
            return Collections.unmodifiableList(children);
        }

        public boolean isLeaf() {
            return children.isEmpty();
        }

        public long thisCents() {
            return thisCents;
        }

        public long lastCents() {
            return lastCents;
        }

        /** How many rows went into this month's figure. */
        public int purchaseCount() {
            return purchaseCount;
        }

        /** This month's cents for each person, keyed by member id. */
        public Map<Long, Long> thisByMember() {
            return Collections.unmodifiableMap(thisByMember);
        }

        /** Stable across rebuilds, so a remembered position survives new data. */
        public String path() {
            return parent == null ? "" : (parent.parent == null
                    ? name : parent.path() + "/" + name);
        }
    }

    private final Node root;
    private final YearMonth month;
    private final YearMonth previousMonth;
    private final boolean hasPreviousMonth;

    private InsightTree(Node root, YearMonth month, boolean hasPreviousMonth) {
        this.root = root;
        this.month = month;
        this.previousMonth = month.minusMonths(1);
        this.hasPreviousMonth = hasPreviousMonth;
    }

    public Node root() {
        return root;
    }

    public YearMonth month() {
        return month;
    }

    public YearMonth previousMonth() {
        return previousMonth;
    }

    /**
     * Whether anything was bought in the month before. Without it there is nothing to
     * compare against, and every "versus last month" figure would be a made-up zero.
     */
    public boolean hasPreviousMonth() {
        return hasPreviousMonth;
    }

    /** The money the reader missed, or null if every order's rows add up. */
    public Node missing() {
        for (Node child : root.children) {
            if (ItemCategorizer.MISSING.equals(child.name)) {
                return child;
            }
        }
        return null;
    }

    /** The node at a path from {@link Node#path()}, or the root if it no longer exists. */
    public Node find(String path) {
        if (path == null || path.isEmpty()) {
            return root;
        }
        Node current = root;
        for (String part : path.split("/")) {
            Node next = null;
            for (Node child : current.children) {
                if (child.name.equals(part)) {
                    next = child;
                    break;
                }
            }
            if (next == null) {
                return current;
            }
            current = next;
        }
        return current;
    }

    /** The most recent month that has any purchase, or null with none at all. */
    public static YearMonth latestMonth(List<Purchase> purchases, ZoneId zone) {
        YearMonth latest = null;
        for (Purchase purchase : purchases) {
            YearMonth month = monthOf(purchase.dateMillis(), zone);
            if (latest == null || month.isAfter(latest)) {
                latest = month;
            }
        }
        return latest;
    }

    public static YearMonth monthOf(long millis, ZoneId zone) {
        return YearMonth.from(Instant.ofEpochMilli(millis).atZone(zone));
    }

    /** Builds the tree for {@code month}, compared with the month before it. */
    public static InsightTree build(List<Purchase> purchases, ZoneId zone, YearMonth month) {
        YearMonth before = month.minusMonths(1);
        Node root = new Node("All groceries", null, 0);
        boolean sawPrevious = false;
        // Read from every month, not just this one, so a brand is known before it repeats.
        Set<String> brands = brandsOf(purchases);

        for (Purchase purchase : purchases) {
            YearMonth when = monthOf(purchase.dateMillis(), zone);
            boolean isThis = when.equals(month);
            boolean isLast = when.equals(before);
            if (!isThis && !isLast) {
                continue;
            }
            sawPrevious |= isLast;

            ItemCategorizer.Placement place = purchase.placement() != null
                    ? purchase.placement() : ItemCategorizer.place(purchase.name());
            Node category = child(root, place.category(), place.category());
            Node sub = child(category, place.subCategory(), place.subCategory());
            // A row put in place by hand keeps its own name; the tidying is for receipts.
            Node item = purchase.placement() != null
                    ? child(sub, purchase.name(), purchase.name())
                    : child(sub, keyOf(purchase.name(), brands),
                            displayName(purchase.name(), brands));
            for (Node node : new Node[] {root, category, sub, item}) {
                if (isThis) {
                    node.thisCents += purchase.cents();
                    node.purchaseCount++;
                    for (Map.Entry<Long, Long> share : purchase.centsByMember().entrySet()) {
                        node.thisByMember.merge(share.getKey(), share.getValue(), Long::sum);
                    }
                } else {
                    node.lastCents += purchase.cents();
                }
            }
        }
        prune(root);
        return new InsightTree(root, month, sawPrevious);
    }

    /**
     * The bubble under {@code parent} for {@code key}, made with {@code display} the first
     * time it is seen. Matching on a key and showing a name are separate jobs: the key is
     * tidied hard so near-identical receipt lines merge, and the name stays readable.
     */
    private static Node child(Node parent, String key, String display) {
        Node existing = parent.childByName.get(key);
        if (existing != null) {
            return existing;
        }
        Node created = new Node(display, parent, parent.depth + 1);
        parent.children.add(created);
        parent.childByName.put(key, created);
        return created;
    }

    /** Drops what was not bought this month and puts the biggest first. */
    private static void prune(Node node) {
        node.children.removeIf(child -> child.thisCents <= 0L);
        node.children.sort(Comparator.comparingLong((Node n) -> n.thisCents).reversed()
                .thenComparing(n -> n.name));
        for (Node child : node.children) {
            prune(child);
        }
    }

    /**
     * What two receipt lines are compared on. "Great Value Whole Milk, 1 Gallon" and "great
     * value whole milk 1 gal" are one thing to buy, so they get one key, and so one bubble.
     */
    static String keyOf(String raw) {
        return keyOf(raw, Collections.emptySet());
    }

    /**
     * The key with this household's store brands taken off the front, so "Great Value Whole
     * Milk" and another shop's "Whole Milk" land in the same bubble.
     */
    static String keyOf(String raw, Set<String> brands) {
        // Made from the shown name, so two lines that read the same always share a bubble,
        // whatever size or pack followed the comma.
        String tidy = NameNormalizer.normalize(raw == null ? null : displayName(raw, brands));
        if (!tidy.isEmpty()) {
            return tidy;
        }
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    static String displayName(String raw) {
        return displayName(raw, Collections.emptySet());
    }

    /**
     * How a line is shown: as printed, minus the size and pack after the first comma and
     * minus a leading store brand, with spaces tidied. Not the matching key, which is made
     * to compare and reads badly ("cooky"). The brand goes because a bubble only has room
     * for a few words, and "Great Value" told you nothing about what was bought.
     */
    static String displayName(String raw, Set<String> brands) {
        if (raw == null) {
            return "Item";
        }
        String text = raw.trim().replaceAll("\\s+", " ");
        int comma = text.indexOf(',');
        if (comma > 0) {
            text = text.substring(0, comma).trim();
        }
        text = withoutBrand(text, brands);
        return text.isEmpty() ? "Item" : text;
    }

    /**
     * Takes the longest matching brand off the front, keeping the rest as printed. A name
     * that is nothing but the brand is left alone, since an empty bubble helps nobody.
     */
    private static String withoutBrand(String text, Set<String> brands) {
        if (brands == null || brands.isEmpty()) {
            return text;
        }
        // "Great Value" on its own is the brand's own product line, not "Value" from the
        // brand "Great", so a name that is all brand stays whole.
        if (brands.contains(text.toLowerCase(Locale.ROOT))) {
            return text;
        }
        String[] words = text.split(" ");
        int drop = 0;
        for (String brand : brands) {
            String[] brandWords = brand.trim().split(" ");
            if (brandWords.length <= drop || brandWords.length >= words.length) {
                continue;
            }
            boolean matches = true;
            for (int i = 0; i < brandWords.length; i++) {
                if (!words[i].toLowerCase(Locale.ROOT).equals(brandWords[i])) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                drop = brandWords.length;
            }
        }
        if (drop == 0) {
            return text;
        }
        StringBuilder rest = new StringBuilder();
        for (int i = drop; i < words.length; i++) {
            if (rest.length() > 0) {
                rest.append(' ');
            }
            rest.append(words[i]);
        }
        String out = rest.toString();
        return Character.toUpperCase(out.charAt(0)) + out.substring(1);
    }

    /**
     * The store brands this household buys, worked out from its own receipts the same way
     * assignment memory does (SPEC 9.1): a first word or few that several different
     * products share. There is no shipped list of brands.
     */
    static Set<String> brandsOf(List<Purchase> purchases) {
        List<String> names = new ArrayList<>();
        for (Purchase purchase : purchases) {
            if (purchase.placement() == null && purchase.name() != null) {
                names.add(purchase.name());
            }
        }
        return NameNormalizer.inferBrandPrefixes(names, BRAND_MIN_DISTINCT_USES);
    }
}
