package com.householdsplitter.core.quality;

/**
 * How well the reader did, measured by what the user had to do afterwards.
 *
 * <p>There is no labelled test set for a household's real orders, and there never will be.
 * What there is instead is the review screen: the user reads every row before the money is
 * split, and whatever they change is a correction. A row they left alone is a row the
 * parser got right, judged by the only person who can see both the screenshot and the
 * truth. That makes ordinary use into a measurement, at no cost to the user.
 *
 * <p>Four outcomes, and the distinction between the last two is the useful part:
 *
 * <ul>
 *   <li>{@code keptAsRead}: found it, got it right, untouched.
 *   <li>{@code corrected}: found the row but got the name or the amount wrong.
 *   <li>{@code removed}: a row the user deleted. The parser invented a charge.
 *   <li>{@code addedByHand}: a row the user typed. The parser missed a charge.
 * </ul>
 *
 * <p>Both of the last two are failures, and they are not equally bad. An invented charge is
 * visible: it sits in the list with a price, and somebody notices they are being billed for
 * something nobody bought. A missed charge is invisible: the order simply comes out short,
 * and the only clue is the reconciliation strip. The breakdown is reported rather than
 * folded into the headline so that a parser failing in the quiet direction cannot hide
 * behind one that fails loudly.
 *
 * <p>Counted in rows, because that is the question asked, and in cents alongside, because
 * a missed bag of rice and a missed bottle of olive oil are one row each and are not the
 * same mistake.
 *
 * <p>Percentages are held as integer permille for the same reason money is held as cents:
 * a rate that is stored as a rounded double and then re-averaged across orders drifts, and
 * a number shown to the user to justify trusting the parser should not itself be
 * approximate.
 */
public final class ParserScorecard {

    /** Returned where no rows have been judged, so there is no rate to report. */
    public static final int UNKNOWN = -1;

    private final int keptAsRead;
    private final int corrected;
    private final int removed;
    private final int addedByHand;

    private final long keptCents;
    private final long correctedCents;
    private final long removedCents;
    private final long addedByHandCents;

    private ParserScorecard(int keptAsRead, int corrected, int removed, int addedByHand,
                            long keptCents, long correctedCents, long removedCents,
                            long addedByHandCents) {
        this.keptAsRead = keptAsRead;
        this.corrected = corrected;
        this.removed = removed;
        this.addedByHand = addedByHand;
        this.keptCents = keptCents;
        this.correctedCents = correctedCents;
        this.removedCents = removedCents;
        this.addedByHandCents = addedByHandCents;
    }

    public static ParserScorecard empty() {
        return new ParserScorecard(0, 0, 0, 0, 0L, 0L, 0L, 0L);
    }

    public static Builder builder() {
        return new Builder();
    }

    public int keptAsRead() {
        return keptAsRead;
    }

    public int corrected() {
        return corrected;
    }

    public int removed() {
        return removed;
    }

    public int addedByHand() {
        return addedByHand;
    }

    public long keptCents() {
        return keptCents;
    }

    public long correctedCents() {
        return correctedCents;
    }

    public long removedCents() {
        return removedCents;
    }

    public long addedByHandCents() {
        return addedByHandCents;
    }

    /** Every row the reader was judged on, whether it produced it or should have. */
    public int judged() {
        return keptAsRead + corrected + removed + addedByHand;
    }

    public long judgedCents() {
        return keptCents + correctedCents + removedCents + addedByHandCents;
    }

    /** Rows left exactly as read, per thousand, or {@link #UNKNOWN} when nothing was judged. */
    public int accuracyPermille() {
        return rate(keptAsRead, judged());
    }

    /**
     * The same measure by value rather than by row.
     *
     * <p>A household cares about the bill, and a reader that is right about nine cheap rows
     * and wrong about one expensive one is not ninety percent right about the money.
     */
    public int valueAccuracyPermille() {
        return rate(keptCents, judgedCents());
    }

    /** Invented charges as a share of everything judged. Visible failures. */
    public int inventedPermille() {
        return rate(removed, judged());
    }

    /** Missed charges as a share of everything judged. The quiet failures. */
    public int missedPermille() {
        return rate(addedByHand, judged());
    }

    /** Found but wrong, as a share of everything judged. */
    public int correctedPermille() {
        return rate(corrected, judged());
    }

    /** Adds two scorecards, for a rate over many orders rather than one. */
    public ParserScorecard plus(ParserScorecard other) {
        if (other == null) {
            return this;
        }
        return new ParserScorecard(
                keptAsRead + other.keptAsRead,
                corrected + other.corrected,
                removed + other.removed,
                addedByHand + other.addedByHand,
                keptCents + other.keptCents,
                correctedCents + other.correctedCents,
                removedCents + other.removedCents,
                addedByHandCents + other.addedByHandCents);
    }

    /**
     * Summed from the counts rather than averaged from the rates.
     *
     * <p>Averaging per-order percentages would let a one-row order weigh as much as a forty
     * row one, so a single perfect small order would paper over a bad large one.
     */
    private static int rate(long part, long whole) {
        if (whole <= 0L) {
            return UNKNOWN;
        }
        return (int) ((part * 1000L) / whole);
    }

    public static final class Builder {

        private int keptAsRead;
        private int corrected;
        private int removed;
        private int addedByHand;
        private long keptCents;
        private long correctedCents;
        private long removedCents;
        private long addedByHandCents;

        private Builder() {
        }

        public Builder keptAsRead(long cents) {
            keptAsRead++;
            keptCents += Math.abs(cents);
            return this;
        }

        public Builder corrected(long cents) {
            corrected++;
            correctedCents += Math.abs(cents);
            return this;
        }

        public Builder removed(long cents) {
            removed++;
            removedCents += Math.abs(cents);
            return this;
        }

        public Builder addedByHand(long cents) {
            addedByHand++;
            addedByHandCents += Math.abs(cents);
            return this;
        }

        public ParserScorecard build() {
            return new ParserScorecard(keptAsRead, corrected, removed, addedByHand,
                    keptCents, correctedCents, removedCents, addedByHandCents);
        }
    }
}
