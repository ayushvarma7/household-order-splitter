package com.householdsplitter.core.parse.model;

/**
 * Where on its screenshot a row was read from.
 *
 * <p>Kept so the app can show the user the picture their money came from, with the row
 * ringed. "Which one is this?" is a fair question about a name the reader mangled or a
 * charge that looks wrong, and the honest answer is the original page.
 *
 * <p>Stored in permille of the image rather than pixels, for the same reason the parser
 * works in permille throughout: the screenshot is displayed at whatever size the device
 * gives it, and a pixel box measured on a 1080-wide capture means nothing on a 1440-wide
 * one. Integers, so no float enters the geometry.
 */
public final class ItemBounds {

    /** Used when a row's origin is not known, such as a row the user typed by hand. */
    public static final ItemBounds UNKNOWN = new ItemBounds(-1, 0, 0, 0, 0);

    private final int imageIndex;
    private final int leftPermille;
    private final int topPermille;
    private final int rightPermille;
    private final int bottomPermille;

    public ItemBounds(int imageIndex, int leftPermille, int topPermille,
                      int rightPermille, int bottomPermille) {
        this.imageIndex = imageIndex;
        this.leftPermille = leftPermille;
        this.topPermille = topPermille;
        this.rightPermille = rightPermille;
        this.bottomPermille = bottomPermille;
    }

    /** True when there is a real screenshot region to show. */
    public boolean isKnown() {
        return imageIndex >= 0 && rightPermille > leftPermille && bottomPermille > topPermille;
    }

    public int imageIndex() {
        return imageIndex;
    }

    public int leftPermille() {
        return leftPermille;
    }

    public int topPermille() {
        return topPermille;
    }

    public int rightPermille() {
        return rightPermille;
    }

    public int bottomPermille() {
        return bottomPermille;
    }

    /**
     * The same box grown by {@code permille} on every side, clamped to the image.
     *
     * <p>A box drawn tight against the text touches the glyphs and reads as a redaction
     * rather than a highlight, so the viewer breathes a little air into it.
     */
    public ItemBounds padded(int permille) {
        if (!isKnown()) {
            return this;
        }
        return new ItemBounds(imageIndex,
                Math.max(0, leftPermille - permille),
                Math.max(0, topPermille - permille),
                Math.min(1000, rightPermille + permille),
                Math.min(1000, bottomPermille + permille));
    }

    @Override
    public String toString() {
        return isKnown()
                ? "image " + imageIndex + " [" + leftPermille + "," + topPermille + ".."
                        + rightPermille + "," + bottomPermille + "]"
                : "unknown";
    }
}
