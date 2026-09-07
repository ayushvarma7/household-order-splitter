package com.householdsplitter.parse;

import android.content.Context;
import android.net.Uri;

import com.householdsplitter.core.parse.model.OcrElement;
import com.householdsplitter.core.parse.model.ParsedOrder;
import com.householdsplitter.core.parse.walmart.ParseTuning;
import com.householdsplitter.core.parse.walmart.WalmartLayoutParser;
import com.householdsplitter.ocr.MlKitTextSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The on-device parser, and the default (SPEC 7.14.2).
 *
 * <p>It does the Android half only: read each screenshot in the user's chosen order,
 * convert it to elements, and hand the lot to the pure-Java layout parser, which does the
 * work of SPEC 8.3 to 8.9.
 */
public class MlKitReceiptParser implements ReceiptParser {

    private final MlKitTextSource textSource;
    private final WalmartLayoutParser layoutParser;

    public MlKitReceiptParser(Context context) {
        this(context, ParseTuning.defaults());
    }

    public MlKitReceiptParser(Context context, ParseTuning tuning) {
        this.textSource = new MlKitTextSource(context, tuning.maxImageDimensionPx);
        this.layoutParser = new WalmartLayoutParser(tuning);
    }

    @Override
    public String displayName() {
        return "On-device";
    }

    @Override
    public ParsedOrder parse(List<Uri> images, ProgressListener listener) throws ParseException {
        if (images == null || images.isEmpty()) {
            throw new ParseException("No screenshots to read");
        }

        List<List<OcrElement>> pages = new ArrayList<>();
        Set<String> seenHashes = new LinkedHashSet<>();
        int index = 0;

        for (Uri uri : images) {
            if (listener != null && listener.isCancelled()) {
                throw new ParseException("Cancelled");
            }
            if (listener != null) {
                listener.onProgress(index, images.size());
            }
            try {
                // SPEC 11.10: the same screenshot picked twice is dropped before parsing.
                String hash = textSource.contentHash(uri);
                if (!seenHashes.add(hash)) {
                    continue;
                }
                pages.add(textSource.read(uri, index));
                index++;
            } catch (Exception failed) {
                throw new ParseException("Could not read screenshot " + (index + 1)
                        + ": " + failed.getMessage(), failed);
            }
        }

        if (pages.isEmpty()) {
            throw new ParseException("Nothing could be read from those images");
        }
        if (listener != null) {
            listener.onProgress(images.size(), images.size());
        }
        // SPEC 8.7.1: pages are stitched in the user's chosen order.
        return layoutParser.parse(pages);
    }

    public void close() {
        textSource.close();
    }
}
