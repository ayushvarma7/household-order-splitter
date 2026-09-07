package com.householdsplitter.core.parse.model;

import com.householdsplitter.core.calc.Scope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One candidate row produced by the parser, before the user reviews it. SPEC 5.6. */
public final class ParsedItem {

    /**
     * The name a row carries when a price was read but its name was not.
     *
     * <p>Kept as a named constant because two other places have to recognise it: the review
     * screen must refuse to continue while a row still says this, and the parser must be
     * able to set it. A row bearing this is a charge nobody has identified yet, which is
     * worse than an unpriced one, so it must not be possible to split an order containing
     * it (SPEC 7.6.9).
     */
    public static final String NAME_NOT_READ = "(name not read)";

    private final String name;
    private final String rawOcrText;
    private final int quantity;
    private final long lineTotalCents;
    private final String unitPriceText;
    private final Scope scope;
    private final String sourceSection;
    private final Set<ReviewReason> reviewReasons;
    private final int imageIndex;
    /** Where on the screenshot this row was read from, for showing the user. */
    private final ItemBounds bounds;

    private ParsedItem(Builder builder) {
        this.name = builder.name == null ? "" : builder.name;
        this.rawOcrText = builder.rawOcrText == null ? "" : builder.rawOcrText;
        this.quantity = Math.max(1, builder.quantity);
        this.lineTotalCents = builder.lineTotalCents;
        this.unitPriceText = builder.unitPriceText;
        this.scope = builder.scope == null ? Scope.UNASSIGNED : builder.scope;
        this.sourceSection = builder.sourceSection;
        this.reviewReasons = Collections.unmodifiableSet(new LinkedHashSet<>(builder.reviewReasons));
        this.imageIndex = builder.imageIndex;
        this.bounds = builder.bounds == null ? ItemBounds.UNKNOWN : builder.bounds;
    }

    public String name() {
        return name;
    }

    /** SPEC 5.6: preserved verbatim so a wrong parse can be diagnosed (SPEC 7.6.5). */
    public String rawOcrText() {
        return rawOcrText;
    }

    public int quantity() {
        return quantity;
    }

    public long lineTotalCents() {
        return lineTotalCents;
    }

    /** SPEC 8.3.3: display only, never charged. */
    public String unitPriceText() {
        return unitPriceText;
    }

    /** UNASSIGNED for a normal row, EXCLUDED for an unavailable one (SPEC 8.5.3). */
    public Scope scope() {
        return scope;
    }

    public String sourceSection() {
        return sourceSection;
    }

    /** SPEC 8.8. */
    public boolean needsReview() {
        return !reviewReasons.isEmpty();
    }

    public Set<ReviewReason> reviewReasons() {
        return reviewReasons;
    }

    public ItemBounds bounds() {
        return bounds;
    }

    public int imageIndex() {
        return imageIndex;
    }

    public Builder toBuilder() {
        return new Builder()
                .name(name)
                .rawOcrText(rawOcrText)
                .quantity(quantity)
                .lineTotalCents(lineTotalCents)
                .unitPriceText(unitPriceText)
                .scope(scope)
                .sourceSection(sourceSection)
                .reviewReasons(reviewReasons)
                .imageIndex(imageIndex)
                .bounds(bounds);
    }

    @Override
    public String toString() {
        return "ParsedItem{'" + name + "' " + lineTotalCents + "c qty=" + quantity
                + (unitPriceText == null ? "" : " unit=" + unitPriceText)
                + (sourceSection == null ? "" : " section=" + sourceSection) + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String name;
        private String rawOcrText;
        private int quantity = 1;
        private long lineTotalCents;
        private String unitPriceText;
        private Scope scope = Scope.UNASSIGNED;
        private String sourceSection;
        private final List<ReviewReason> reviewReasons = new ArrayList<>();
        private int imageIndex;
        private ItemBounds bounds;

        public Builder name(String value) {
            this.name = value;
            return this;
        }

        public Builder rawOcrText(String value) {
            this.rawOcrText = value;
            return this;
        }

        public Builder quantity(int value) {
            this.quantity = value;
            return this;
        }

        public Builder lineTotalCents(long value) {
            this.lineTotalCents = value;
            return this;
        }

        public Builder unitPriceText(String value) {
            this.unitPriceText = value;
            return this;
        }

        public Builder scope(Scope value) {
            this.scope = value;
            return this;
        }

        public Builder sourceSection(String value) {
            this.sourceSection = value;
            return this;
        }

        public Builder flag(ReviewReason reason) {
            if (reason != null && !reviewReasons.contains(reason)) {
                reviewReasons.add(reason);
            }
            return this;
        }

        public Builder reviewReasons(Iterable<ReviewReason> reasons) {
            if (reasons != null) {
                for (ReviewReason reason : reasons) {
                    flag(reason);
                }
            }
            return this;
        }

        public Builder bounds(ItemBounds value) {
            this.bounds = value;
            return this;
        }

        public Builder imageIndex(int value) {
            this.imageIndex = value;
            return this;
        }

        public ParsedItem build() {
            return new ParsedItem(this);
        }
    }
}
