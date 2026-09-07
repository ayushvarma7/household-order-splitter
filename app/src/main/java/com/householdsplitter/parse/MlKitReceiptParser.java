package com.householdsplitter.parse;

import android.content.Context;
import android.net.Uri;

import com.householdsplitter.core.parse.LayoutParser;
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
 *
 * <p>The layout parser is a constructor argument rather than something built here, so
 * SPEC 3.5's second store is a different {@link LayoutParser} passed in and no edit to this
 * class. The OCR step is store-independent: it reads whatever text is on the screen.
 */
public class MlKitReceiptParser implements ReceiptParser {

    private final MlKitTextSource textSource;
    private final LayoutParser layoutParser;

    /** The only store supported in v1 (SPEC 3.5). */
    public MlKitReceiptParser(Context context) {
        this(context, ParseTuning.defaults());
    }

    public MlKitReceiptParser(Context context, ParseTuning tuning) {
        this(context, tuning.maxImageDimensionPx, new WalmartLayoutParser(tuning));
    }

    public MlKitReceiptParser(Context context, int maxImageDimensionPx,
                              LayoutParser layoutParser) {
        this.textSource = new MlKitTextSource(context, maxImageDimensionPx);
        this.layoutParser = layoutParser;
    }

    @Override
    public String displayName() {
        return "On-device";
    }

    /** Which store's layout this parser is reading, for the review screen. */
    public String storeName() {
        return layoutParser.storeName();
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
