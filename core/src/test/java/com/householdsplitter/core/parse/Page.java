package com.householdsplitter.core.parse;

import com.householdsplitter.core.parse.model.OcrElement;

import java.util.ArrayList;
import java.util.List;

/**
 * A fixture screenshot: text plus bounding boxes, exactly what SPEC 12.4 calls for. No
 * images, no ML Kit and no network are involved in any parser test.
 *
 * <p>Geometry mirrors a real phone screenshot at 1080 x 2400. The left column runs to
 * roughly 37% of the width and the right-aligned price column sits near 90%, which is what
 * the zones in {@code ParseTuning} are calibrated against.
 */
public final class Page {

    private static final int DEFAULT_WIDTH = 1080;
    private static final int DEFAULT_HEIGHT = 2400;
    private static final int LINE_HEIGHT = 40;
    private static final int LINE_STEP = 62;

    private static final int LEFT_X = 180;
    private static final int LEFT_X_END = 620;
    private static final int PRICE_X = 900;
    private static final int PRICE_X_END = 1040;

    private final int imageIndex;
    private final int width;
    private final int height;
    private final List<OcrElement> elements = new ArrayList<>();
    private int cursorY;

    private Page(int imageIndex, int width, int height) {
        this.imageIndex = imageIndex;
        this.width = width;
        this.height = height;
        // Content starts below the sticky header zone that ParseTuning discards: the
        // status bar and the blue app bar, which on a real capture run to about 12%.
        this.cursorY = (height * 140) / 1000;
    }

    public static Page image(int index) {
        return new Page(index, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /** A longer screenshot, for fixtures that need more rows than a phone screen holds. */
    public static Page tallImage(int index, int height) {
        return new Page(index, DEFAULT_WIDTH, height);
    }

    /** Exact dimensions, for fixtures traced off a real screenshot. */
    public static Page sized(int index, int width, int height) {
        return new Page(index, width, height);
    }

    public List<OcrElement> elements() {
        return elements;
    }

    public int cursorY() {
        return cursorY;
    }

    public Page cursorY(int value) {
        this.cursorY = value;
        return this;
    }

    /** Raw placement, for the cases that need exact coordinates. */
    public Page at(int y, int xLeft, int xRight, String text) {
        return at(y, xLeft, xRight, text, LINE_HEIGHT, 95);
    }

    public Page at(int y, int xLeft, int xRight, String text, int glyphHeight, int confidence) {
        elements.add(new OcrElement(text, imageIndex, xLeft, y, xRight, y + glyphHeight,
                this.width, this.height, confidence));
        return this;
    }

    /** One line of ordinary left-column text. */
    public Page line(String text) {
        at(cursorY, LEFT_X, LEFT_X_END, text);
        cursorY += LINE_STEP;
        return this;
    }

    public Page line(String text, int confidence) {
        at(cursorY, LEFT_X, LEFT_X_END, text, LINE_HEIGHT, confidence);
        cursorY += LINE_STEP;
        return this;
    }

    /** The first line of an item: name fragment on the left, line price on the right. */
    public Page itemStart(String nameFragment, String price) {
        at(cursorY, LEFT_X, LEFT_X_END, nameFragment);
        at(cursorY, PRICE_X, PRICE_X_END, price);
        cursorY += LINE_STEP;
        return this;
    }

    /** An item row printing a struck-through original beside the charged price. */
    public Page itemStartDiscounted(String nameFragment, String struckPrice, String chargedPrice) {
        at(cursorY, LEFT_X, LEFT_X_END, nameFragment);
        at(cursorY, 780, 870, struckPrice);
        at(cursorY, PRICE_X, PRICE_X_END, chargedPrice);
        cursorY += LINE_STEP;
        return this;
    }

    /** A complete single-line item. */
    public Page item(String name, String price) {
        return itemStart(name, price);
    }

    /** A section header such as "16 shopped" (SPEC 8.5.1). */
    public Page section(String header) {
        at(cursorY, 120, 480, header, 48, 95);
        cursorY += LINE_STEP;
        return this;
    }

    /** The blue app bar of SPEC 8.6.1. */
    public Page appBar(String title) {
        at(cursorY, 260, 820, title, 50, 95);
        cursorY += LINE_STEP;
        return this;
    }

    /** A summary row: label on the left, one or more amounts on the band (SPEC 8.6.3). */
    public Page summary(String label, String... amounts) {
        at(cursorY, 120, 120 + 26 * label.length(), label);
        int x = 780;
        for (String amount : amounts) {
            at(cursorY, x, x + 26 * amount.length(), amount);
            x += 26 * amount.length() + 30;
        }
        cursorY += LINE_STEP;
        return this;
    }

    /** Interface furniture between rows (SPEC 8.4.1). */
    public Page chrome(String text) {
        at(cursorY, LEFT_X, LEFT_X + 22 * text.length(), text);
        cursorY += LINE_STEP;
        return this;
    }

    /** A rating carousel card: name, then five empty stars, and no price (SPEC 8.4.8). */
    public Page carouselCard(String productName) {
        at(cursorY, 120, 120 + 20 * productName.length(), productName);
        cursorY += 44;
        at(cursorY, 120, 300, "☆ ☆ ☆ ☆ ☆");
        cursorY += LINE_STEP;
        return this;
    }

    /** The status bar of SPEC 8.4.7, inside the top 5% that SPEC 8.2.3 crops. */
    public Page statusBar(String clockOrTimer, String battery) {
        at(40, 40, 160, clockOrTimer, 34, 90);
        at(40, 940, 1040, battery, 34, 90);
        return this;
    }

    public Page gap(int pixels) {
        cursorY += pixels;
        return this;
    }
}
