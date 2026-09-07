package com.householdsplitter.parse;

import android.content.Context;

import com.householdsplitter.BuildConfig;

/**
 * The cloud flavour's wiring. SPEC 7.14.2 and 8.10.
 *
 * <p>Same fully qualified name as the standard flavour's version, so nothing in the shared
 * source set has to know which build it is in. Without a key in {@code local.properties} the
 * option reports itself unavailable and Settings disables it with an explanation
 * (SPEC 8.10.3).
 */
public final class ParserFactory {

    private ParserFactory() {
    }

    public static boolean isCloudAvailable() {
        return BuildConfig.LLM_API_KEY != null && !BuildConfig.LLM_API_KEY.trim().isEmpty();
    }

    public static String unavailableReason() {
        return "Add llm.api.key to local.properties and rebuild to use the cloud reader.";
    }

    public static ReceiptParser create(Context context, boolean preferCloud) {
        if (preferCloud && isCloudAvailable()) {
            return new LlmReceiptParser(context, BuildConfig.LLM_API_KEY);
        }
        return new MlKitReceiptParser(context);
    }
}
