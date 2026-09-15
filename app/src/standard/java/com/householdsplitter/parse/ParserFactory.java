package com.householdsplitter.parse;

import android.content.Context;

import com.householdsplitter.core.parse.StoreKind;

/**
 * SPEC 4.10: the default flavour has no network permission and no cloud parser on its
 * classpath at all, so there is nothing to accidentally enable. The Settings toggle reads
 * {@link #isCloudAvailable()} and disables itself with an explanation (SPEC 8.10.3).
 */
public final class ParserFactory {

    private ParserFactory() {
    }

    public static boolean isCloudAvailable() {
        return false;
    }

    public static String unavailableReason() {
        return "This build has no network access. The cloud parser ships in a separate build.";
    }

    public static ReceiptParser create(Context context, boolean preferCloud) {
        return create(context, preferCloud, StoreKind.WALMART);
    }

    public static ReceiptParser create(Context context, boolean preferCloud, StoreKind store) {
        return new MlKitReceiptParser(context, store);
    }
}
