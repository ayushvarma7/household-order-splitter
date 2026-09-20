package com.householdsplitter.core.parse.layout;

import com.householdsplitter.core.parse.model.OcrElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Straightens a photographed receipt before anything tries to read columns off it.
 *
 * <p>Every geometric rule in {@link ParseTuning} rests on one assumption: that a line price
 * sits in a fixed band of the image width. That holds for a screenshot, which is captured
 * square by definition. It does not hold for a photograph of paper. Tilt a receipt five
 * degrees in the hand and a right-aligned price near the top of the page lands in a
 * different permille column from one near the bottom, so the price column stops being a
 * column and the parser stops finding prices.
 *
 * <p>Correcting that normally means a document-corner detector, which means an imaging
 * library and several megabytes. It is not needed here, because the receipt already
 * contains the answer. Prices are right-aligned decimals, so their right edges lie on a
 * line that is vertical when the page is square and tilted by exactly the page's tilt when
 * it is not. Measure that line and the skew falls out of the receipt's own structure.
 *
 * <p>The estimate is a Theil-Sen median of pairwise slopes rather than a least-squares fit.
 * Least squares has a breakdown point of zero: one stray decimal in the middle of the page,
 * a phone number or a table number read as an amount, drags the whole line. The median of
 * pairwise slopes tolerates nearly a third of the anchors being wrong, which is the right
 * property when the inputs are OCR guesses rather than measurements.
 *
 * <p>All integer arithmetic, in keeping with the rest of this package: the slope is carried
 * in permille and the rotation uses an integer square root rather than trigonometry, so the
 * result is bit-identical on every device.
 *
 * <p>Opt in per store through {@link StoreVocabulary#needsDeskew()}. Walmart and Amazon
 * return false, so a screenshot is never rotated by a fraction of a degree it does not have
 * and their fixtures cannot be affected by anything in this file.
 */
public final class Deskew {

    /**
     * Beyond about fifteen degrees the anchors are more likely to be a coincidence than a
     * column, and a wrong rotation is worse than none. tan(15 degrees) is 0.268.
     */
    static final int MAX_SLOPE_PERMILLE = 268;

    /** Fewer anchors than this and the median is not measuring anything. */
    private static final int MIN_ANCHORS = 4;

    /**
     * The anchors must span this much of the image height. Four prices crowded into one
     * corner describe that corner, not the page.
     */
    private static final int MIN_SPAN_PERMILLE = 150;

    /**
     * Pairs closer together than this contribute nothing but noise: a few pixels of OCR
     * jitter over a short baseline produces an enormous apparent slope.
     */
    private static final int MIN_PAIR_SEPARATION_PERMILLE = 60;

    private Deskew() {
    }

    /**
     * The tilt of the price column, as {@code dx/dy} in permille.
     *
     * <p>Zero for a square page. Positive when the column leans right as it descends.
     * Returns zero rather than a guess whenever the evidence is too thin to measure, so a
     * caller never has to distinguish "square" from "unmeasurable": in both cases the right
     * thing to do is leave the page alone.
     */
    public static int slopePermille(List<OcrElement> elements) {
        if (elements == null || elements.size() < MIN_ANCHORS) {
            return 0;
        }
        List<int[]> anchors = anchors(elements);
        if (anchors.size() < MIN_ANCHORS) {
            return 0;
        }
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int[] anchor : anchors) {
            minY = Math.min(minY, anchor[1]);
            maxY = Math.max(maxY, anchor[1]);
        }
        int height = elements.get(0).imageHeight();
        if ((int) (((long) (maxY - minY) * 1000L) / height) < MIN_SPAN_PERMILLE) {
            return 0;
        }

        int separation = Math.max(1, (height * MIN_PAIR_SEPARATION_PERMILLE) / 1000);
        List<Integer> slopes = new ArrayList<>();
        for (int i = 0; i < anchors.size(); i++) {
            for (int j = i + 1; j < anchors.size(); j++) {
                int dy = anchors.get(j)[1] - anchors.get(i)[1];
                if (Math.abs(dy) < separation) {
                    continue;
                }
                long dx = anchors.get(j)[0] - anchors.get(i)[0];
                slopes.add((int) ((dx * 1000L) / dy));
            }
        }
        if (slopes.isEmpty()) {
            return 0;
        }
        Collections.sort(slopes);
        int median = slopes.get(slopes.size() / 2);
        if (Math.abs(median) > MAX_SLOPE_PERMILLE) {
            return 0;
        }
        return median;
    }

    /**
     * The right edge and vertical centre of every element that reads as an amount.
     *
     * <p>The right edge rather than the centre, because that is the edge the printer
     * aligned. Amounts differ in width, so their centres wander by a character or two even
     * on a perfectly square page, and that wander is noise added to the signal being
     * measured.
     */
    private static List<int[]> anchors(List<OcrElement> elements) {
        List<int[]> anchors = new ArrayList<>();
        for (OcrElement element : elements) {
            if (PriceTokens.isPriceToken(element.text())) {
                anchors.add(new int[]{element.right(), (element.top() + element.bottom()) / 2});
            }
        }
        return anchors;
    }

    /**
     * The page with its measured tilt taken out, or the same list when there is none.
     *
     * <p>The identity case returns the argument unchanged rather than a copy, so a store
     * that opts in but photographs straight pays nothing.
     */
    public static List<OcrElement> straighten(List<OcrElement> elements) {
        int slope = slopePermille(elements);
        return slope == 0 ? elements : rotate(elements, slope);
    }

    /**
     * Rotates every element so that a column of the given slope becomes vertical.
     *
     * <p>Each element's centre is rotated about the image centre and its width and height
     * are kept. That is an approximation, and the honest description of it is this: the box
     * ML Kit reported was axis-aligned around text that was already tilted, so it was never
     * a tight fit in the first place. Re-deriving a bounding box from the rotated corners
     * would grow every box by the tilt, which spreads neighbouring rows into each other and
     * makes band grouping worse rather than better.
     *
     * <p>The canvas is then translated and grown so nothing lands at a negative coordinate.
     * Everything downstream measures in permille of the image, so what matters is that the
     * elements and the canvas they are measured against agree, not that the canvas still
     * matches the original photograph.
     */
    static List<OcrElement> rotate(List<OcrElement> elements, int slopePermille) {
        if (elements.isEmpty() || slopePermille == 0) {
            return elements;
        }
        int width = elements.get(0).imageWidth();
        int height = elements.get(0).imageHeight();
        long centreX = width / 2;
        long centreY = height / 2;

        // The column direction (slope, 1000) has length hyp, so cos and sin of the tilt are
        // 1000/hyp and slope/hyp. Undoing the tilt is the rotation that maps that direction
        // back onto (0, 1).
        long slope = slopePermille;
        long hyp = isqrt(1_000_000L + slope * slope);

        int count = elements.size();
        int[] newLeft = new int[count];
        int[] newTop = new int[count];
        int[] newRight = new int[count];
        int[] newBottom = new int[count];
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int i = 0; i < count; i++) {
            OcrElement element = elements.get(i);
            long dx = (long) (element.left() + element.right()) / 2 - centreX;
            long dy = (long) (element.top() + element.bottom()) / 2 - centreY;

            long x = centreX + (dx * 1000L - dy * slope) / hyp;
            long y = centreY + (dx * slope + dy * 1000L) / hyp;

            int halfWidth = Math.max(0, element.right() - element.left()) / 2;
            int halfHeight = element.heightPx() / 2;
            newLeft[i] = (int) (x - halfWidth);
            newRight[i] = (int) (x + halfWidth);
            newTop[i] = (int) (y - halfHeight);
            newBottom[i] = (int) (y + halfHeight);

            minX = Math.min(minX, newLeft[i]);
            minY = Math.min(minY, newTop[i]);
            maxX = Math.max(maxX, newRight[i]);
            maxY = Math.max(maxY, newBottom[i]);
        }

        int shiftX = minX < 0 ? -minX : 0;
        int shiftY = minY < 0 ? -minY : 0;
        int canvasWidth = Math.max(width, maxX + shiftX);
        int canvasHeight = Math.max(height, maxY + shiftY);

        List<OcrElement> rotated = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            OcrElement element = elements.get(i);
            rotated.add(new OcrElement(
                    element.text(), element.imageIndex(),
                    newLeft[i] + shiftX, newTop[i] + shiftY,
                    newRight[i] + shiftX, newBottom[i] + shiftY,
                    canvasWidth, canvasHeight, element.confidencePercent()));
        }
        return rotated;
    }

    /** Integer square root, so the rotation needs no floating point. */
    static long isqrt(long value) {
        if (value <= 0L) {
            return 0L;
        }
        long guess = value;
        long next = (guess + 1L) / 2L;
        while (next < guess) {
            guess = next;
            next = (guess + value / guess) / 2L;
        }
        return guess;
    }
}
