package com.householdsplitter.core.parse.walmart;

/**
 * Every geometric and threshold constant the Walmart parser uses, in one place.
 *
 * <p>SPEC 8.2.3 requires the crop margins to be tunable constants rather than magic
 * numbers buried in a loop, and the same argument applies to all of these: Walmart
 * changes its layout, and when it does this is the file to edit (see PARSING.md).
 *
 * <p>SPEC 8.2.2 and SPEC 11.9: all geometry is a fraction of the image dimensions,
 * expressed here in permille (parts per thousand) so it stays integer arithmetic and
 * behaves identically on a phone, a tablet and any screen density.
 */
public final class ParseTuning {

    /** SPEC 8.2.3: the status bar. */
    public final int topCropPermille;
    /** SPEC 8.2.3: the gesture bar. */
    public final int bottomCropPermille;
    /** SPEC 8.3.2: a line price sits in the right-most 30% of the content width. */
    public final int linePriceZoneStartPermille;
    /** SPEC 8.3.6: name text sits in the left 65%. */
    public final int nameZoneEndPermille;
    /**
     * SPEC 8.3.2 also asks for "larger or bolder than body text". OCR reports no font
     * weight, so height relative to the median element height stands in for it. Held
     * slightly under the median because Walmart's price and body text are often the same
     * size, and missing a price loses a whole row.
     */
    public final int linePriceMinHeightPermilleOfMedian;
    /** SPEC 8.7.3: how many rows back a cross-boundary duplicate may sit. */
    public final int dedupeWindow;
    /** SPEC 8.8.1: below this, the name is flagged for review. */
    public final int minConfidencePercent;
    /** SPEC 8.8.1: names shorter than this are flagged. */
    public final int minNameLength;
    /** SPEC 8.8.1: a configurable outlier threshold, in cents. */
    public final long priceOutlierCents;
    /** SPEC 8.2.1: the longest edge after downscaling. */
    public final int maxImageDimensionPx;
    /** Vertical overlap needed before two elements are treated as one band, in permille. */
    public final int bandOverlapPermille;

    private ParseTuning(Builder builder) {
        this.topCropPermille = builder.topCropPermille;
        this.bottomCropPermille = builder.bottomCropPermille;
        this.linePriceZoneStartPermille = builder.linePriceZoneStartPermille;
        this.nameZoneEndPermille = builder.nameZoneEndPermille;
        this.linePriceMinHeightPermilleOfMedian = builder.linePriceMinHeightPermilleOfMedian;
        this.dedupeWindow = builder.dedupeWindow;
        this.minConfidencePercent = builder.minConfidencePercent;
        this.minNameLength = builder.minNameLength;
        this.priceOutlierCents = builder.priceOutlierCents;
        this.maxImageDimensionPx = builder.maxImageDimensionPx;
        this.bandOverlapPermille = builder.bandOverlapPermille;
    }

    public static ParseTuning defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private int topCropPermille = 50;                     // SPEC 8.2.3: top 5%
        private int bottomCropPermille = 30;                  // SPEC 8.2.3: bottom 3%
        private int linePriceZoneStartPermille = 700;         // SPEC 8.3.2: right-most 30%
        private int nameZoneEndPermille = 650;                // SPEC 8.3.6: left 65%
        private int linePriceMinHeightPermilleOfMedian = 850;
        private int dedupeWindow = 4;                         // SPEC 8.7.3
        private int minConfidencePercent = 50;                // SPEC 8.8.1
        private int minNameLength = 3;                        // SPEC 8.8.1
        private long priceOutlierCents = 30_000L;             // SPEC 8.8.1: $300
        private int maxImageDimensionPx = 2048;               // SPEC 8.2.1
        private int bandOverlapPermille = 500;

        public Builder topCropPermille(int value) {
            this.topCropPermille = value;
            return this;
        }

        public Builder bottomCropPermille(int value) {
            this.bottomCropPermille = value;
            return this;
        }

        public Builder linePriceZoneStartPermille(int value) {
            this.linePriceZoneStartPermille = value;
            return this;
        }

        public Builder nameZoneEndPermille(int value) {
            this.nameZoneEndPermille = value;
            return this;
        }

        public Builder linePriceMinHeightPermilleOfMedian(int value) {
            this.linePriceMinHeightPermilleOfMedian = value;
            return this;
        }

        public Builder dedupeWindow(int value) {
            this.dedupeWindow = value;
            return this;
        }

        public Builder minConfidencePercent(int value) {
            this.minConfidencePercent = value;
            return this;
        }

        public Builder minNameLength(int value) {
            this.minNameLength = value;
            return this;
        }

        public Builder priceOutlierCents(long value) {
            this.priceOutlierCents = value;
            return this;
        }

        public Builder maxImageDimensionPx(int value) {
            this.maxImageDimensionPx = value;
            return this;
        }

        public Builder bandOverlapPermille(int value) {
            this.bandOverlapPermille = value;
            return this;
        }

        public ParseTuning build() {
            return new ParseTuning(this);
        }
    }
}
