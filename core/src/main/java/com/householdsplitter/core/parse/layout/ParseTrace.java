package com.householdsplitter.core.parse.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A record of what the reader did to every piece of text it saw.
 *
 * <p>Written for one purpose: when a user types in a row by hand, the app can re-read the
 * screenshots and say why that row was not found. Without this, the only available answer
 * is "it was missed", which is a count and not a diagnosis. With it, the answer names the
 * stage that lost it, and every stage points at one specific pattern list or one named
 * constant.
 *
 * <p>Optional and inert. A parse with no trace behaves identically, which matters because
 * the thing being diagnosed has to be the parser that actually runs, not a reimplementation
 * of it that can drift.
 *
 * <p>Holds recognised text, which is the user's own receipt, so it lives as long as the
 * diagnosis and is not persisted.
 */
public final class ParseTrace {

    /** How far a band got before the reader stopped considering it. */
    public enum Stage {
        /** Cropped out before anything was read: the status bar or the navigation bar. */
        CROPPED_AT_THE_MARGIN,
        /** Inside the no-scan header zone. */
        INSIDE_THE_HEADER,
        /** A chrome pattern matched it, so it was treated as interface furniture. */
        FILTERED_AS_CHROME,
        /** Read as a section header rather than as a product. */
        READ_AS_A_SECTION_HEADER,
        /** Read as an order-level figure rather than as a product. */
        READ_AS_A_SUMMARY_LINE,
        /** Below the point where purchased rows stop. */
        PAST_THE_ITEM_REGION,
        /** In the item region, but with no amount in the price column. */
        NO_PRICE_IN_THE_COLUMN,
        /** In the item region with a price. This band was eligible to become a row. */
        ELIGIBLE
    }

    /** One band, and how far it got. */
    public static final class BandNote {

        public final int imageIndex;
        public final int topPermille;
        public final String text;
        public final String nameZoneText;
        public final Stage stage;

        BandNote(int imageIndex, int topPermille, String text, String nameZoneText,
                 Stage stage) {
            this.imageIndex = imageIndex;
            this.topPermille = topPermille;
            this.text = text == null ? "" : text;
            this.nameZoneText = nameZoneText == null ? "" : nameZoneText;
            this.stage = stage;
        }

        @Override
        public String toString() {
            return stage + " [page " + imageIndex + " @" + topPermille + "] " + text;
        }
    }

    private final List<BandNote> notes = new ArrayList<>();

    /**
     * Text discarded by the margin crop, before it could be grouped into a band.
     *
     * <p>Kept separately because at that point there is no band to attach it to, and a row
     * lost here is lost for a completely different reason from one the vocabulary rejected.
     */
    void addCropped(int imageIndex, String text) {
        notes.add(new BandNote(imageIndex, -1, text, text, Stage.CROPPED_AT_THE_MARGIN));
    }

    void addBand(int imageIndex, int topPermille, String text, String nameZoneText,
                 Stage stage) {
        notes.add(new BandNote(imageIndex, topPermille, text, nameZoneText, stage));
    }

    public List<BandNote> notes() {
        return Collections.unmodifiableList(notes);
    }

    public boolean isEmpty() {
        return notes.isEmpty();
    }
}
