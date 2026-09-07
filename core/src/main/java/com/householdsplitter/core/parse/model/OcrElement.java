package com.householdsplitter.core.parse.model;

/**
 * One recognised text element with its bounding box. SPEC 8.2.1.
 *
 * <p>This is our own type on purpose. ML Kit's {@code Text.Element} is an Android class,
 * and keeping it out of the parsing algorithm is what lets SPEC 12.4 run as plain JUnit
 * against captured fixtures, with no images, no emulator and no network.
 *
 * <p>SPEC 8.2.2 and SPEC 11.9 require geometry to be expressed as fractions of the image
 * dimensions rather than as pixel constants, so every derived accessor below returns
 * permille (parts per thousand) as an {@code int}. Integers rather than fractions keep the
 * whole module free of {@code float} and {@code double}, and make the geometry exactly
 * reproducible across devices and densities.
 */
public final class OcrElement {

    private final String text;
    private final int imageIndex;
    private final int left;
    private final int top;
    private final int right;
    private final int bottom;
    private final int imageWidth;
    private final int imageHeight;
    private final int confidencePercent;

    public OcrElement(String text, int imageIndex, int left, int top, int right, int bottom,
                      int imageWidth, int imageHeight, int confidencePercent) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException("image dimensions must be positive");
        }
        this.text = text == null ? "" : text;
        this.imageIndex = imageIndex;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.confidencePercent = confidencePercent;
    }

    public String text() {
        return text;
    }

    /** Which screenshot this came from, in the user's chosen order (SPEC 7.4.4). */
    public int imageIndex() {
        return imageIndex;
    }

    public int left() {
        return left;
    }

    public int top() {
        return top;
    }

    public int right() {
        return right;
    }

    public int bottom() {
        return bottom;
    }

    public int imageWidth() {
        return imageWidth;
    }

    public int imageHeight() {
        return imageHeight;
    }

    /** 0 to 100, or -1 when the recogniser did not report one. */
    public int confidencePercent() {
        return confidencePercent;
    }

    public int heightPx() {
        return Math.max(0, bottom - top);
    }

    public int centerXPermille() {
        return (int) (((long) (left + right) * 500L) / imageWidth);
    }

    public int centerYPermille() {
        return (int) (((long) (top + bottom) * 500L) / imageHeight);
    }

    public int topPermille() {
        return (int) (((long) top * 1000L) / imageHeight);
    }

    public int bottomPermille() {
        return (int) (((long) bottom * 1000L) / imageHeight);
    }

    @Override
    public String toString() {
        return "OcrElement{'" + text + "' img=" + imageIndex
                + " y=" + topPermille() + ".." + bottomPermille()
                + " x=" + centerXPermille() + "}";
    }
}
