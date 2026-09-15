package com.householdsplitter.core.parse;

import com.householdsplitter.core.parse.amazon.AmazonFreshVocabulary;
import com.householdsplitter.core.parse.layout.StoreVocabulary;
import com.householdsplitter.core.parse.walmart.WalmartVocabulary;

/**
 * Which store's order page a set of screenshots came from.
 *
 * <p>Chosen by the user before importing, and stored on the order. It is not detected.
 * Detecting it would mean classifying a receipt, and a misclassified receipt does not fail
 * loudly: it produces a plausible figure that is wrong, which is the one outcome this whole
 * parser is arranged to avoid. Told which store it is reading, a vocabulary is free to be
 * strict and to flag a row it cannot account for; asked to guess, every vocabulary has to
 * be loose enough to be a candidate, and loose vocabularies are the ones that mis-read
 * money.
 *
 * <p>The two layouts are also genuinely hard to tell apart cheaply. Both print a thumbnail,
 * a wrapping title, a quantity line and a right-aligned price. What distinguishes them is
 * the summary block wording, which is the most fragile part of either vocabulary, so a
 * detector would rest the choice of parser on the least reliable evidence available.
 *
 * <p>Nothing outside the parse package branches on this value. It travels with the order so
 * that the order can say where it came from, and so that re-parsing an old order years
 * later uses the vocabulary it was read with rather than today's default.
 */
public enum StoreKind {

    WALMART("Walmart") {
        @Override
        public StoreVocabulary vocabulary() {
            return new WalmartVocabulary();
        }
    },

    AMAZON_FRESH("Amazon Fresh") {
        @Override
        public StoreVocabulary vocabulary() {
            return new AmazonFreshVocabulary();
        }
    };

    private final String displayName;

    StoreKind(String displayName) {
        this.displayName = displayName;
    }

    /** The store's name as a person writes it. */
    public String displayName() {
        return displayName;
    }

    /** The wording and geometry for reading this store's pages. */
    public abstract StoreVocabulary vocabulary();

    /** A ready-to-use reader for this store. */
    public LayoutParser layoutParser() {
        return new com.householdsplitter.core.parse.layout.ReceiptLayoutParser(vocabulary());
    }

    /**
     * The kind stored under this name, or {@link #WALMART} when the name is unknown.
     *
     * <p>Walmart is the fallback because every order that existed before this column did
     * was a Walmart order, so an unreadable value means an old row rather than a new store.
     */
    public static StoreKind fromName(String name) {
        if (name == null) {
            return WALMART;
        }
        for (StoreKind kind : values()) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        return WALMART;
    }
}
