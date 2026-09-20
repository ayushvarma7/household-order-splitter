package com.householdsplitter.core.parse.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.parse.model.OcrElement;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Measuring a receipt's amount column off the receipt.
 *
 * <p>The tests that matter most here are the ones where it declines to answer. A default
 * column produces rows the user can see and fix; a confidently wrong column moves the
 * boundary between the dish and its price, which produces numbers nobody questions.
 */
public class ColumnCalibrationTest {

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 2000;

    private static OcrElement at(String text, int left, int right, int y) {
        return new OcrElement(text, 0, left, y, right, y + 34, WIDTH, HEIGHT, 92);
    }

    /** Amounts right-aligned on a shared edge, as a printer aligns them. */
    private static List<OcrElement> column(int rightEdge, int count) {
        List<OcrElement> elements = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int y = 200 + i * 60;
            elements.add(at("DISH " + i, 200, 700, y));
            String amount = "$" + (10 + i) + ".50";
            elements.add(at(amount, rightEdge - 19 * amount.length(), rightEdge, y));
        }
        return elements;
    }

    @Test
    public void theColumnIsFoundWhereTheAmountsAre() {
        // Right edge 990 of 1200 is 825 permille; the amounts are 6 characters wide, so
        // they start near 730, and the boundary sits just left of that.
        int start = ColumnCalibration.columnStartPermille(column(990, 6));
        assertTrue("column start was " + start, start > 660 && start < 740);
    }

    @Test
    public void aNarrowerRollPutsTheColumnSomewhereElse() {
        int wide = ColumnCalibration.columnStartPermille(column(1150, 6));
        int narrow = ColumnCalibration.columnStartPermille(column(820, 6));
        assertTrue("a narrower roll should measure a different column",
                wide - narrow > 100);
    }

    /**
     * The reason the densest cluster is used rather than the leftmost amount or the median
     * of all of them. A bill prints numbers that are not in the column.
     */
    @Test
    public void scatteredAmountsElsewhereOnThePageDoNotMoveTheColumn() {
        int clean = ColumnCalibration.columnStartPermille(column(990, 8));
        List<OcrElement> polluted = new ArrayList<>(column(990, 8));
        polluted.add(at("8.36", 120, 240, 150));
        polluted.add(at("4.09", 300, 420, 180));
        polluted.add(at("11.50", 480, 620, 1600));
        assertEquals(clean, ColumnCalibration.columnStartPermille(polluted));
    }

    @Test
    public void tooFewAmountsMeansNoAnswer() {
        assertEquals(-1, ColumnCalibration.columnStartPermille(column(990, 2)));
    }

    @Test
    public void amountsScatteredWithNoColumnAtAllMeansNoAnswer() {
        List<OcrElement> scattered = new ArrayList<>();
        scattered.add(at("1.00", 100, 220, 200));
        scattered.add(at("2.00", 400, 520, 300));
        scattered.add(at("3.00", 700, 820, 400));
        scattered.add(at("4.00", 250, 370, 500));
        scattered.add(at("5.00", 550, 670, 600));
        assertEquals(-1, ColumnCalibration.columnStartPermille(scattered));
    }

    /** A column too far left is a coincidence, not a column. */
    @Test
    public void aColumnInTheLeftHalfIsRefused() {
        assertEquals(-1, ColumnCalibration.columnStartPermille(column(400, 6)));
    }

    @Test
    public void refusingToAnswerLeavesTheTuningExactlyAsItWas() {
        ParseTuning fallback = ParseTuning.restaurant();
        assertSame(fallback, ColumnCalibration.against(column(990, 2), fallback));
        assertSame(fallback, ColumnCalibration.against(null, fallback));
    }

    /**
     * The name column has to close exactly where the amount column opens. Zone membership
     * is decided on an element's centre, so a gap between them is a band of positions that
     * is neither a name nor a price.
     */
    @Test
    public void theTwoColumnsMeetWithNoGapBetweenThem() {
        ParseTuning calibrated =
                ColumnCalibration.against(column(990, 6), ParseTuning.restaurant());
        assertEquals(calibrated.nameZoneEndPermille, calibrated.linePriceZoneStartPermille);
    }

    @Test
    public void nothingElseAboutTheTuningIsDisturbed() {
        ParseTuning base = ParseTuning.restaurant();
        ParseTuning calibrated = ColumnCalibration.against(column(990, 6), base);
        assertEquals(base.topCropPermille, calibrated.topCropPermille);
        assertEquals(base.bottomCropPermille, calibrated.bottomCropPermille);
        assertEquals(base.nameZoneStartPermille, calibrated.nameZoneStartPermille);
        assertEquals(base.maxImageDimensionPx, calibrated.maxImageDimensionPx);
        assertEquals(base.dedupeWindow, calibrated.dedupeWindow);
        assertEquals(base.priceOutlierCents, calibrated.priceOutlierCents);
    }
}
