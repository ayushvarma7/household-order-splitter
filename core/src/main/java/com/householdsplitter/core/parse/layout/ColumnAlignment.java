package com.householdsplitter.core.parse.layout;

import com.householdsplitter.core.parse.model.OcrElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts an amount back on the same line as its label.
 *
 * <p>A till receipt printed both columns on one line, so the reader groups elements into
 * horizontal bands and reads a label on the left and its figure on the right. A photograph
 * of one does not always preserve that. On a curled receipt the two columns slide against
 * each other, and on a real bill measured here every amount sat about one line's height
 * above the label it belongs to: the $95.75 beside "SubTotal" landed on the last dish
 * instead, which made the subtotal a seventh item and left the summary block reading the
 * tax as the subtotal.
 *
 * <p>Rotation cannot fix that. {@link Deskew} turns the whole page about its centre, which
 * is the right correction for a page held at an angle and the wrong one for a page that is
 * curved: it moves both columns together. What is needed here is to move one column
 * relative to the other, which is one number.
 *
 * <p>That number is searched for rather than assumed. Every candidate shift is scored by
 * how many amounts end up level with something in the left column, and the best wins, with
 * ties going to the smallest shift so a page that needs no correction gets none.
 *
 * <p>A constant shift is knowingly an approximation. The offsets on a curled page vary
 * along it, from 42 permille at the top to 11 at the bottom on the bill this was built
 * against, so one number cannot line up every row. It does not have to: it is offered as
 * one more candidate reading, and {@link ReceiptLayoutParser} keeps whichever reading adds
 * up to the figures the bill prints.
 */
public final class ColumnAlignment {

    /** How far the column is allowed to have slid, either way, in permille of height. */
    private static final int MAX_SHIFT_PERMILLE = 60;

    /** Steps of two permille: finer than the search can resolve is wasted work. */
    private static final int STEP_PERMILLE = 2;

    /** Level enough to count, as a fraction of a typical line's height. */
    private static final int TOLERANCE_PERMILLE_OF_LINE = 600;

    private ColumnAlignment() {
    }

    /**
     * The page with its amount column slid back into line, or the same list when it is
     * already in line or there is not enough to measure.
     */
    public static List<OcrElement> aligned(List<OcrElement> elements) {
        if (elements == null || elements.size() < 4) {
            return elements;
        }
        int columnStart = ColumnCalibration.columnStartPermille(elements);
        if (columnStart < 0) {
            return elements;
        }
        int height = elements.get(0).imageHeight();
        int width = elements.get(0).imageWidth();

        List<OcrElement> right = new ArrayList<>();
        List<Integer> leftCentres = new ArrayList<>();
        int lineHeight = 0;
        for (OcrElement element : elements) {
            int centreX = (int) (((long) (element.left() + element.right()) * 500L) / width);
            if (centreX >= columnStart) {
                right.add(element);
                lineHeight = Math.max(lineHeight, element.heightPx());
            } else {
                leftCentres.add((element.top() + element.bottom()) / 2);
            }
        }
        if (right.isEmpty() || leftCentres.isEmpty() || lineHeight <= 0) {
            return elements;
        }

        int tolerance = Math.max(1, (lineHeight * TOLERANCE_PERMILLE_OF_LINE) / 1000);
        int limit = (height * MAX_SHIFT_PERMILLE) / 1000;
        int step = Math.max(1, (height * STEP_PERMILLE) / 1000);

        int bestShift = 0;
        int bestScore = levelCount(right, leftCentres, 0, tolerance);
        for (int shift = -limit; shift <= limit; shift += step) {
            if (shift == 0) {
                continue;
            }
            int score = levelCount(right, leftCentres, shift, tolerance);
            // Strictly better, so a shift has to earn its place against doing nothing and
            // against every smaller shift already tried.
            if (score > bestScore) {
                bestScore = score;
                bestShift = shift;
            }
        }
        if (bestShift == 0) {
            return elements;
        }

        List<OcrElement> moved = new ArrayList<>(elements.size());
        for (OcrElement element : elements) {
            int centreX = (int) (((long) (element.left() + element.right()) * 500L) / width);
            if (centreX < columnStart) {
                moved.add(element);
                continue;
            }
            moved.add(new OcrElement(element.text(), element.imageIndex(),
                    element.left(), element.top() + bestShift,
                    element.right(), element.bottom() + bestShift,
                    width, height, element.confidencePercent()));
        }
        return moved;
    }

    /** How many amounts sit level with something in the left column at this shift. */
    private static int levelCount(List<OcrElement> right, List<Integer> leftCentres,
                                  int shift, int tolerance) {
        int level = 0;
        for (OcrElement element : right) {
            int centre = (element.top() + element.bottom()) / 2 + shift;
            for (int leftCentre : leftCentres) {
                if (Math.abs(leftCentre - centre) <= tolerance) {
                    level++;
                    break;
                }
            }
        }
        return level;
    }
}
