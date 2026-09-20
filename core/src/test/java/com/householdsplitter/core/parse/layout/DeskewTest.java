package com.householdsplitter.core.parse.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.parse.model.OcrElement;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The deskew stage, checked by tilting a known-square page and measuring what comes back.
 *
 * <p>This is the one part of the receipt pipeline where the truth is available exactly. A
 * real photograph has an unknown tilt, so a test against one could only assert that the
 * answer looks plausible. Tilting a synthetic page by a slope chosen here means the
 * expected answer is known to the permille before the code runs, which is what lets these
 * tests assert recovery rather than reasonableness.
 */
public class DeskewTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 2400;

    /** A square receipt: a right-aligned price column and a left-aligned name column. */
    private static List<OcrElement> squarePage(int rows) {
        List<OcrElement> elements = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            int y = 200 + i * 90;
            elements.add(new OcrElement("ITEM " + i, 0, 80, y, 420, y + 40, WIDTH, HEIGHT, 95));
            elements.add(new OcrElement("1" + i + ".50", 0, 880, y, 1000, y + 40,
                    WIDTH, HEIGHT, 95));
        }
        return elements;
    }

    /**
     * Tilts a page so that its vertical columns acquire the given slope. The exact inverse
     * of what {@link Deskew} has to undo.
     */
    private static List<OcrElement> tilt(List<OcrElement> elements, int slopePermille) {
        long hyp = Deskew.isqrt(1_000_000L + (long) slopePermille * slopePermille);
        long centreX = WIDTH / 2;
        long centreY = HEIGHT / 2;
        List<OcrElement> out = new ArrayList<>(elements.size());
        for (OcrElement e : elements) {
            long dx = (long) (e.left() + e.right()) / 2 - centreX;
            long dy = (long) (e.top() + e.bottom()) / 2 - centreY;
            long x = centreX + (dx * 1000L + dy * slopePermille) / hyp;
            long y = centreY + (-dx * slopePermille + dy * 1000L) / hyp;
            int halfWidth = (e.right() - e.left()) / 2;
            int halfHeight = e.heightPx() / 2;
            out.add(new OcrElement(e.text(), e.imageIndex(),
                    (int) (x - halfWidth), (int) (y - halfHeight),
                    (int) (x + halfWidth), (int) (y + halfHeight),
                    e.imageWidth(), e.imageHeight(), e.confidencePercent()));
        }
        return out;
    }

    /** How far apart the price right-edges sit, in pixels. Zero on a perfect column. */
    private static int priceColumnSpread(List<OcrElement> elements) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (OcrElement e : elements) {
            if (PriceTokens.isPriceToken(e.text())) {
                min = Math.min(min, e.right());
                max = Math.max(max, e.right());
            }
        }
        return max - min;
    }

    @Test
    public void aSquarePageHasNoTiltToFind() {
        assertEquals(0, Deskew.slopePermille(squarePage(12)));
    }

    @Test
    public void aSquarePageIsReturnedWithoutCopying() {
        List<OcrElement> page = squarePage(12);
        assertSame("a straight page should cost nothing at all", page, Deskew.straighten(page));
    }

    @Test
    public void theMeasuredTiltIsTheTiltThatWasApplied() {
        for (int slope : new int[]{-175, -88, -35, 35, 88, 175}) {
            int measured = Deskew.slopePermille(tilt(squarePage(14), slope));
            assertTrue("expected about " + slope + " but measured " + measured,
                    Math.abs(measured - slope) <= 4);
        }
    }

    @Test
    public void straighteningRestoresThePriceColumn() {
        List<OcrElement> tilted = tilt(squarePage(14), 120);
        assertTrue("the tilt should have broken the column first",
                priceColumnSpread(tilted) > 100);
        assertTrue("straightened column still spread by "
                        + priceColumnSpread(Deskew.straighten(tilted)),
                priceColumnSpread(Deskew.straighten(tilted)) <= 4);
    }

    /**
     * The reason for a median of pairwise slopes rather than a least-squares fit. A phone
     * number, a table number or a time read as an amount sits nowhere near the price
     * column, and a fit that averages would follow it.
     */
    @Test
    public void aStrayAmountInTheMiddleOfThePageDoesNotMoveTheEstimate() {
        List<OcrElement> clean = tilt(squarePage(14), 100);
        List<OcrElement> polluted = new ArrayList<>(clean);
        polluted.add(new OcrElement("4.09", 0, 300, 400, 420, 440, WIDTH, HEIGHT, 70));
        polluted.add(new OcrElement("8.36", 0, 120, 1400, 240, 1440, WIDTH, HEIGHT, 70));
        polluted.add(new OcrElement("3.10", 0, 500, 2100, 620, 2140, WIDTH, HEIGHT, 70));

        assertEquals(Deskew.slopePermille(clean), Deskew.slopePermille(polluted), 6);
    }

    @Test
    public void tooFewAmountsToMeasureLeavesThePageAlone() {
        List<OcrElement> page = squarePage(2);
        assertEquals(0, Deskew.slopePermille(tilt(page, 120)));
        assertSame(page, Deskew.straighten(page));
    }

    /** Four prices crowded into one corner describe the corner, not the page. */
    @Test
    public void amountsCrowdedIntoOneCornerAreNotEnough() {
        List<OcrElement> crowded = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            int y = 200 + i * 20;
            crowded.add(new OcrElement("1" + i + ".50", 0, 880 + i * 6, y, 1000 + i * 6, y + 40,
                    WIDTH, HEIGHT, 95));
        }
        assertEquals(0, Deskew.slopePermille(crowded));
    }

    @Test
    public void anImplausibleTiltIsRefusedRatherThanApplied() {
        int beyondTheLimit = Deskew.MAX_SLOPE_PERMILLE + 200;
        assertEquals(0, Deskew.slopePermille(tilt(squarePage(14), beyondTheLimit)));
    }

    @Test
    public void straighteningNeverProducesANegativeCoordinate() {
        for (int slope : new int[]{-260, -120, 120, 260}) {
            for (OcrElement e : Deskew.rotate(tilt(squarePage(14), slope), slope)) {
                assertTrue("left " + e.left(), e.left() >= 0);
                assertTrue("top " + e.top(), e.top() >= 0);
                assertTrue("right beyond canvas", e.right() <= e.imageWidth());
                assertTrue("bottom beyond canvas", e.bottom() <= e.imageHeight());
            }
        }
    }

    @Test
    public void rowsStayTogetherAfterStraightening() {
        // A tilted row's two elements sit at different heights; straightening has to put
        // them back on the same band or BandBuilder will split every row in two.
        List<OcrElement> straightened = Deskew.straighten(tilt(squarePage(14), 140));
        for (int i = 0; i < straightened.size(); i += 2) {
            int nameCentre = (straightened.get(i).top() + straightened.get(i).bottom()) / 2;
            int priceCentre =
                    (straightened.get(i + 1).top() + straightened.get(i + 1).bottom()) / 2;
            assertTrue("row " + i / 2 + " split by " + Math.abs(nameCentre - priceCentre),
                    Math.abs(nameCentre - priceCentre) <= 4);
        }
    }

    @Test
    public void theIntegerSquareRootIsExactOnSquaresAndFloorsOtherwise() {
        assertEquals(0L, Deskew.isqrt(0L));
        assertEquals(1L, Deskew.isqrt(1L));
        assertEquals(1000L, Deskew.isqrt(1_000_000L));
        assertEquals(3L, Deskew.isqrt(15L));
        assertEquals(4L, Deskew.isqrt(16L));
        assertEquals(1_000_000L, Deskew.isqrt(1_000_000_000_000L));
    }
}
