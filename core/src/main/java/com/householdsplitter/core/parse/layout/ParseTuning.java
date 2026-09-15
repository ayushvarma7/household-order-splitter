package com.householdsplitter.core.parse.layout;

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
    /**
     * The sticky blue app bar, which sits below the status bar on every Walmart order
     * screenshot and scrolls with nothing.
     *
     * <p>It carries the order date, which IS wanted (SPEC 8.6.1), and a cart showing the
     * live cart value and order status, which is not: it reflects what is in the basket
     * right now, not what this delivered order cost. The date is read from the bar and then
     * the whole zone is discarded, so the cart total can never be mistaken for a line price
     * even though it sits squarely in the right-hand price column.
     */
    public final int headerZonePermille;
    /** SPEC 8.3.2: a line price sits in the right-most 30% of the content width. */
    public final int linePriceZoneStartPermille;
    /** SPEC 8.3.6: name text sits in the left 65%. */
    public final int nameZoneEndPermille;

    /**
     * Where the name column starts, as a permille of image width.
     *
     * <p>Left of this sits the product thumbnail, and ML Kit reads the text printed on the
     * packaging inside it: a bag of apples contributes "GALA APPLES" as though it were part
     * of the item name. The name column has a left edge as well as a right one, and only the
     * right one was being applied.
     */
    public final int nameZoneStartPermille;
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
    /**
     * How close to the top or bottom of a screenshot a row has to be before a missing name
     * is read as the row having been cut off rather than misrecognised.
     *
     * <p>SPEC 8.7.5 says an edge fragment must not become a nameless line item, and that is
     * right at the edges, where the counterpart is on the adjacent screenshot. In the middle
     * of the page there is no counterpart: a price with no name there means the name failed
     * to recognise, and dropping the row would quietly lose a charge.
     */
    public final int edgeZonePermille;

    private ParseTuning(Builder builder) {
        this.topCropPermille = builder.topCropPermille;
        this.bottomCropPermille = builder.bottomCropPermille;
        this.headerZonePermille = builder.headerZonePermille;
        this.linePriceZoneStartPermille = builder.linePriceZoneStartPermille;
        this.nameZoneEndPermille = builder.nameZoneEndPermille;
        this.nameZoneStartPermille = builder.nameZoneStartPermille;
        this.linePriceMinHeightPermilleOfMedian = builder.linePriceMinHeightPermilleOfMedian;
        this.dedupeWindow = builder.dedupeWindow;
        this.minConfidencePercent = builder.minConfidencePercent;
        this.minNameLength = builder.minNameLength;
        this.priceOutlierCents = builder.priceOutlierCents;
        this.maxImageDimensionPx = builder.maxImageDimensionPx;
        this.bandOverlapPermille = builder.bandOverlapPermille;
        this.edgeZonePermille = builder.edgeZonePermille;
    }

    /** Walmart's calibration, and the historical default. */
    public static ParseTuning defaults() {
        return walmart();
    }

    /**
     * Walmart's calibration, measured on 899 x 1959 captures.
     *
     * <p>The builder's own initial values are Walmart's for historical reasons, so this
     * states them again rather than relying on that. A second store arriving is exactly
     * when an implicit default stops being harmless.
     */
    public static ParseTuning walmart() {
        return builder().build();
    }

    /**
     * Amazon Fresh's calibration, measured on real order pages at three capture widths
     * (921, 1079 and 1080 pixels).
     *
     * <p>Every number here came off the pixels rather than off an estimate, and the reason
     * to trust them is that the same permille figures fell out of all three widths: name
     * text begins at 177 to 179 permille on every page, the quantity line at 178, and the
     * price column at 877. That is the resolution independence SPEC 11.9 asks for,
     * confirmed rather than assumed.
     *
     * <p>Where it differs from Walmart, and why:
     *
     * <ul>
     *   <li>The name column starts much further left, 177 against Walmart's 290, because
     *       Amazon's product thumbnail is smaller. It occupies 43 to 140 permille, so 160
     *       clears the image without reaching the text. Without a left edge here ML Kit
     *       reads the packaging in the thumbnail into the product name.
     *   <li>The name column runs much further right. Amazon wraps long titles across four
     *       or five lines and indents a weight note under them; the widest text measured
     *       reached 733 permille, against Walmart's 650 limit. Cutting at Walmart's
     *       boundary would truncate names.
     *   <li>The price column is further right and narrower, 877 to 960 permille.
     *   <li>The bottom crop is much deeper, 90 against 30. Amazon keeps a five-icon
     *       navigation bar pinned over the page, and on a capture where the list runs to
     *       the bottom of the screen those icons land on the same horizontal band as the
     *       last row's text, merging chrome into content.
     * </ul>
     *
     * <p>The name and price zones meet at 800 rather than leaving a gap between them. Zone
     * membership is decided on an element's centre, so a gap is a band of positions that
     * belongs to neither column, and Amazon puts a struck-through delivery fee centred at
     * 792 permille. It is read correctly regardless, because the charged amount is the
     * right-most on the band rather than the one in a particular zone, but a dead zone
     * between the columns is a trap worth not setting.
     */
    public static ParseTuning amazonFresh() {
        return builder()
                .bottomCropPermille(90)
                .nameZoneStartPermille(160)
                .nameZoneEndPermille(800)
                .linePriceZoneStartPermille(800)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private int topCropPermille = 50;                     // SPEC 8.2.3: top 5%
        private int bottomCropPermille = 30;                  // SPEC 8.2.3: bottom 3%
        private int headerZonePermille = 120;                 // status bar plus the app bar
        private int linePriceZoneStartPermille = 700;         // SPEC 8.3.2: right-most 30%
        private int nameZoneEndPermille = 650;                // SPEC 8.3.6: left 65%
        // The thumbnail column. Measured at 75..240px on an 899px capture, so the name
        // starts around 290 permille; 260 leaves room without reaching the text.
        private int nameZoneStartPermille = 260;
        private int linePriceMinHeightPermilleOfMedian = 850;
        private int dedupeWindow = 4;                         // SPEC 8.7.3
        private int minConfidencePercent = 50;                // SPEC 8.8.1
        private int minNameLength = 3;                        // SPEC 8.8.1
        private long priceOutlierCents = 30_000L;             // SPEC 8.8.1: $300
        private int maxImageDimensionPx = 2048;               // SPEC 8.2.1
        private int bandOverlapPermille = 500;
        private int edgeZonePermille = 90;

        public Builder topCropPermille(int value) {
            this.topCropPermille = value;
            return this;
        }

        public Builder bottomCropPermille(int value) {
            this.bottomCropPermille = value;
            return this;
        }

        public Builder headerZonePermille(int value) {
            this.headerZonePermille = value;
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

        public Builder nameZoneStartPermille(int value) {
            this.nameZoneStartPermille = value;
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

        public Builder edgeZonePermille(int value) {
            this.edgeZonePermille = value;
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
