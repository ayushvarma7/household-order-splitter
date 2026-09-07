package com.householdsplitter.parse;

import android.net.Uri;

import com.householdsplitter.core.parse.model.ParsedOrder;

import java.util.List;

/**
 * Turns screenshots into a {@link ParsedOrder}. SPEC 8.1, and the same interface the
 * optional cloud parser implements (SPEC 8.10.1).
 *
 * <p>The interface lives in the app module because its input is a list of {@code Uri}. The
 * layout algorithm it delegates to is plain Java in :core, which is what lets SPEC 12.4
 * run on the JVM against fixtures.
 */
public interface ReceiptParser {

    /** Progress for the determinate bar of SPEC 7.5.1. */
    interface ProgressListener {
        void onProgress(int imageIndex, int imageCount);

        /** SPEC 7.5.2: Cancel aborts cleanly, without creating an order. */
        boolean isCancelled();
    }

    /** A short name for the settings screen and error messages. */
    String displayName();

    /**
     * Runs on {@code AppExecutors.parsing()} (SPEC 7.5.2), never the main thread.
     *
     * @throws ParseException when nothing could be read, so S5 can offer Retry,
     *                        Choose different images, or Enter manually (SPEC 7.5.3)
     */
    ParsedOrder parse(List<Uri> images, ProgressListener listener) throws ParseException;

    /** Thrown with a reason the user can act on. */
    class ParseException extends Exception {

        public ParseException(String message) {
            super(message);
        }

        public ParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
