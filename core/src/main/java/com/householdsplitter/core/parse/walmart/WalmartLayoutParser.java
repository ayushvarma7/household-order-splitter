package com.householdsplitter.core.parse.walmart;

import com.householdsplitter.core.parse.layout.ParseTuning;
import com.householdsplitter.core.parse.layout.ReceiptLayoutParser;

/**
 * The Walmart order page reader. SPEC 8.2 to 8.9.
 *
 * <p>All of the reading happens in {@link ReceiptLayoutParser}. What is Walmart's about it
 * is {@link WalmartVocabulary}, which supplies the wording, and {@link ParseTuning#walmart()},
 * which supplies the column positions. This class exists so that calling code can name the
 * store it means.
 */
public final class WalmartLayoutParser extends ReceiptLayoutParser {

    /** Shown on the review screen. Nothing branches on it. */
    public static final String STORE_NAME = WalmartVocabulary.STORE_NAME;

    public WalmartLayoutParser() {
        this(ParseTuning.walmart());
    }

    public WalmartLayoutParser(ParseTuning tuning) {
        super(new WalmartVocabulary(tuning == null ? ParseTuning.walmart() : tuning));
    }
}
