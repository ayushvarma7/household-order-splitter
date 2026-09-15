package com.householdsplitter.parse;

import android.content.Context;

import com.householdsplitter.core.parse.StoreKind;

/**
 * Builds the reader for one store's pages.
 *
 * <p>There is one reader, and it runs on the device. SPEC 8.10 allowed an optional cloud
 * parser as a second implementation of {@link ReceiptParser}, selectable in Settings, and
 * it is gone: it was never built, never had a key, and was the only file in the tree that
 * could open a socket, in an app whose entire claim is that it cannot. Keeping an unused
 * escape hatch that contradicts the app's one guarantee is a worse trade than losing it.
 *
 * <p>What replaced it is better suited to the actual problem anyway. When Amazon Fresh
 * arrived, the answer was a new {@link com.householdsplitter.core.parse.layout.StoreVocabulary}
 * and column positions measured off real pages, which is checkable, testable and offline.
 * A model that reads a receipt is none of those things.
 */
public final class ParserFactory {

    private ParserFactory() {
    }

    public static ReceiptParser create(Context context, StoreKind store) {
        return new MlKitReceiptParser(context, store == null ? StoreKind.WALMART : store);
    }
}
