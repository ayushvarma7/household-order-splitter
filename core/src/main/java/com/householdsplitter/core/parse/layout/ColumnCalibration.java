package com.householdsplitter.core.parse.layout;

import com.householdsplitter.core.parse.model.OcrElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Finds a page's amount column by looking at the page, for receipts whose layout is not
 * known in advance.
 *
 * <p>{@link ParseTuning} answers "where is the price column" with a constant, because for
 * a store with one layout that constant can be measured off a real capture once and then
 * relied on. A restaurant bill has no such constant. A 32-character till roll and a 42
 * character one put their amounts in visibly different places, and the same restaurant
 * photographed closer or further away moves them again. Any single number written down
 * here would be wrong for most receipts.
 *
 * <p>What is reliably true is narrower and more useful: on one receipt, the amounts line up
 * with each other. Right-aligned decimals share a right edge, so the densest cluster of
 * right edges on the page is the amount column, and everything left of it is description.
 * That is a fact about the receipt in hand rather than about restaurants in general, which
 * is why it can be trusted where a constant cannot.
 *
 * <p>Densest cluster rather than leftmost or median, because a receipt prints amounts that
 * are not in the column: a phone number with a decimal, a table number, a time, a unit
 * rate. Those are scattered, and scattered points do not form a cluster. The column does.
 *
 * <p>Refuses to answer rather than answering badly. Too few amounts, or a cluster in an
 * implausible place, returns the fallback tuning unchanged, and the parse proceeds on the
 * default column with rows flagged for review. A wrong column is worse than a default one:
 * it silently moves the boundary between "this is the product name" and "this is what it
 * cost".
 */
public final class ColumnCalibration {

    /** Right edges within this much of each other count as the same column. */
    private static final int CLUSTER_WIDTH_PERMILLE = 70;

    /** Below this, a "cluster" is a coincidence. */
    private static final int MIN_IN_COLUMN = 3;

    /** Breathing room left of the widest amount, so a long total is not clipped. */
    private static final int COLUMN_MARGIN_PERMILLE = 20;

    /**
     * An amount column left of this is not an amount column. Even a wide description and a
     * narrow price leave the amounts in the right-hand half of the paper.
     */
    private static final int MIN_COLUMN_START_PERMILLE = 380;

    /** And a column starting beyond this leaves no room for the amounts themselves. */
    private static final int MAX_COLUMN_START_PERMILLE = 930;

    private ColumnCalibration() {
    }

    /**
     * The given tuning with its two column boundaries moved to where this page puts them,
     * or the same tuning when the page does not say.
     *
     * <p>The name column is closed exactly where the amount column opens, with no gap
     * between them. Zone membership is decided on an element's centre, so a gap would be a
     * band of positions belonging to neither column, and anything centred there would be
     * neither a name nor a price.
     */
    public static ParseTuning against(List<OcrElement> page, ParseTuning fallback) {
        int start = columnStartPermille(page);
        if (start < 0) {
            return fallback;
        }
        return fallback.toBuilder()
                .nameZoneEndPermille(start)
                .linePriceZoneStartPermille(start)
                .build();
    }

    /**
     * Where this page's amount column begins, in permille of image width, or -1 when the
     * page does not carry enough aligned amounts to say.
     */
    public static int columnStartPermille(List<OcrElement> page) {
        if (page == null || page.size() < MIN_IN_COLUMN) {
            return -1;
        }
        List<int[]> amounts = new ArrayList<>();
        for (OcrElement element : page) {
            if (!PriceTokens.isPriceToken(element.text())) {
                continue;
            }
            int width = element.imageWidth();
            amounts.add(new int[]{
                    (int) (((long) element.right() * 1000L) / width),
                    (int) (((long) element.left() * 1000L) / width)});
        }
        if (amounts.size() < MIN_IN_COLUMN) {
            return -1;
        }
        Collections.sort(amounts, (a, b) -> Integer.compare(a[0], b[0]));

        // The densest window of right edges, found by sliding a fixed-width window over the
        // sorted values. Linear, and it needs no threshold on what "dense" means: whichever
        // window holds the most amounts is the column.
        int bestStart = 0;
        int bestEnd = 0;
        int bestCount = 0;
        int low = 0;
        for (int high = 0; high < amounts.size(); high++) {
            while (amounts.get(high)[0] - amounts.get(low)[0] > CLUSTER_WIDTH_PERMILLE) {
                low++;
            }
            int count = high - low + 1;
            if (count > bestCount) {
                bestCount = count;
                bestStart = low;
                bestEnd = high;
            }
        }
        if (bestCount < MIN_IN_COLUMN) {
            return -1;
        }

        int leftMost = Integer.MAX_VALUE;
        for (int i = bestStart; i <= bestEnd; i++) {
            leftMost = Math.min(leftMost, amounts.get(i)[1]);
        }
        int start = leftMost - COLUMN_MARGIN_PERMILLE;
        if (start < MIN_COLUMN_START_PERMILLE || start > MAX_COLUMN_START_PERMILLE) {
            return -1;
        }
        return start;
    }
}
