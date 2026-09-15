package com.householdsplitter.core.parse.amazon;

import com.householdsplitter.core.parse.layout.ParseTuning;
import com.householdsplitter.core.parse.layout.ReceiptLayoutParser;

/**
 * The Amazon Fresh order page reader.
 *
 * <p>Holds no reading logic of its own. The pipeline is
 * {@link ReceiptLayoutParser}, the wording is {@link AmazonFreshVocabulary} and the column
 * positions are {@link ParseTuning#amazonFresh()}. That this class is four lines long is
 * the point of SPEC 3.5.
 */
public final class AmazonFreshLayoutParser extends ReceiptLayoutParser {

    /** Shown on the review screen. Nothing branches on it. */
    public static final String STORE_NAME = AmazonFreshVocabulary.STORE_NAME;

    public AmazonFreshLayoutParser() {
        this(ParseTuning.amazonFresh());
    }

    public AmazonFreshLayoutParser(ParseTuning tuning) {
        super(new AmazonFreshVocabulary(tuning == null ? ParseTuning.amazonFresh() : tuning));
    }
}
