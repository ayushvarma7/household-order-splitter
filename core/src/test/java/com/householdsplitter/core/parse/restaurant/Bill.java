package com.householdsplitter.core.parse.restaurant;

import com.householdsplitter.core.parse.model.OcrElement;

import java.util.ArrayList;
import java.util.List;

/**
 * A photographed till receipt, as positioned OCR elements.
 *
 * <p>Laid out the way a thermal printer lays one out and a phone camera sees one: a narrow
 * column of monospace text somewhere in the middle of a much larger frame, with the counts
 * in a left column, the descriptions beside them, and the amounts right-aligned against a
 * common edge. The amounts share that edge exactly, because a printer aligns them exactly,
 * and the whole of {@link com.householdsplitter.core.parse.layout.ColumnCalibration} rests
 * on their doing so.
 *
 * <p>{@link #tilted(int)} hands back the same receipt photographed askew, which is how a
 * receipt is actually photographed. A test that only ever sees a square page would pass
 * while the shipped app failed on every real bill.
 */
public final class Bill {

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 2000;
    private static final int GLYPH = 34;
    private static final int STEP = 54;
    private static final int CELL = 19;

    private static final int QTY_X = 190;
    private static final int DESC_X = 260;
    private static final int AMOUNT_RIGHT = 990;

    private final List<OcrElement> elements = new ArrayList<>();
    private int cursorY = 120;

    public static Bill paper() {
        return new Bill();
    }

    public List<OcrElement> elements() {
        return elements;
    }

    public List<List<OcrElement>> pages() {
        List<List<OcrElement>> pages = new ArrayList<>();
        pages.add(elements);
        return pages;
    }

    /** The same receipt, photographed at an angle. */
    public List<List<OcrElement>> tilted(int slopePermille) {
        long hyp = isqrt(1_000_000L + (long) slopePermille * slopePermille);
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
                    WIDTH, HEIGHT, e.confidencePercent()));
        }
        List<List<OcrElement>> pages = new ArrayList<>();
        pages.add(out);
        return pages;
    }

    /** Inlined rather than borrowed, so widening the parser's API is not a test concern. */
    private static long isqrt(long value) {
        long guess = value;
        long next = (guess + 1L) / 2L;
        while (next < guess) {
            guess = next;
            next = (guess + value / guess) / 2L;
        }
        return guess;
    }

    private Bill add(int x, String text) {
        elements.add(new OcrElement(text, 0, x, cursorY, x + CELL * text.length(),
                cursorY + GLYPH, WIDTH, HEIGHT, 92));
        return this;
    }

    /** A right-aligned amount, sharing its right edge with every other amount. */
    private Bill amount(String text) {
        int right = AMOUNT_RIGHT;
        elements.add(new OcrElement(text, 0, right - CELL * text.length(), cursorY,
                right, cursorY + GLYPH, WIDTH, HEIGHT, 92));
        return this;
    }

    /** A free line of text across the description column: the header, the footer, a rule. */
    public Bill line(String text) {
        add(DESC_X, text);
        cursorY += STEP;
        return this;
    }

    /** A line starting at the far left, where the count column sits. */
    public Bill leftLine(String text) {
        add(QTY_X, text);
        cursorY += STEP;
        return this;
    }

    /** A dish: count in its own column, description beside it, amount right-aligned. */
    public Bill item(int quantity, String description, String amount) {
        add(QTY_X, String.valueOf(quantity));
        add(DESC_X, description);
        amount(amount);
        cursorY += STEP;
        return this;
    }

    /** A dish whose count the recogniser ran into the description. */
    public Bill itemRunTogether(String countAndDescription, String amount) {
        add(QTY_X, countAndDescription);
        amount(amount);
        cursorY += STEP;
        return this;
    }

    /** A dish with no printed count at all, which is how many tills print a single. */
    public Bill item(String description, String amount) {
        add(DESC_X, description);
        amount(amount);
        cursorY += STEP;
        return this;
    }

    /** A kitchen instruction printed under a dish, with no amount of its own. */
    public Bill modifier(String text) {
        add(DESC_X + CELL, text);
        cursorY += STEP;
        return this;
    }

    /** A summary row: label on the left, amount right-aligned. */
    public Bill summary(String label, String amount) {
        add(QTY_X, label);
        amount(amount);
        cursorY += STEP;
        return this;
    }

    public Bill gap(int pixels) {
        cursorY += pixels;
        return this;
    }
}
