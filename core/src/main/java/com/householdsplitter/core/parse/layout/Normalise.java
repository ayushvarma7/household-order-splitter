package com.householdsplitter.core.parse.layout;

/**
 * The one text-tidying rule every store vocabulary matches against.
 *
 * <p>Matching a pattern against raw OCR output is brittle for reasons that have nothing to
 * do with the store: a recognised line arrives with inconsistent case, doubled spaces
 * where the engine split a word, and a trailing colon or bullet picked up from the
 * interface. Normalising first means a vocabulary can be written in the wording a human
 * reads off the screen, and every store gets the same tolerance (SPEC 8.4).
 */
public final class Normalise {

    private Normalise() {
    }

    /** Lowercase, collapse whitespace, drop trailing punctuation. */
    public static String text(String value) {
        if (value == null) {
            return "";
        }
        String result = value.toLowerCase().trim();
        result = result.replaceAll("\\s+", " ");
        result = result.replaceAll("[\\.,;:•·]+$", "");
        return result.trim();
    }
}
