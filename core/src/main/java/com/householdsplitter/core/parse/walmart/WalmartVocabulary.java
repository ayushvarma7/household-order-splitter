package com.householdsplitter.core.parse.walmart;

import com.householdsplitter.core.parse.layout.Normalise;
import com.householdsplitter.core.parse.layout.ParseTuning;
import com.householdsplitter.core.parse.layout.StoreVocabulary;
import com.householdsplitter.core.parse.model.OrderField;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Walmart's wording, gathered behind the one seam a store is allowed to be.
 *
 * <p>The pattern lists themselves stay in {@link ChromeFilter}, {@link OrderFieldExtractor},
 * {@link RowAnnotations} and {@link SectionHeaders}, which is where PARSING.md points a
 * reader and where they are documented row by row. This class only says which of them
 * answers which question, so that nothing above it has to know the answers come from
 * Walmart.
 */
public final class WalmartVocabulary implements StoreVocabulary {

    /** Shown on the review screen. Nothing branches on it. */
    public static final String STORE_NAME = "Walmart";

    /** "Qty 1" and "Qty: 1". */
    private static final Pattern QTY = Pattern.compile("(?i)^qty:? (\\d+)$");

    /**
     * A multipack count, which is not a quantity: "Multipack Quantity: 12" on a twelve-pack
     * describes the pack, and the row still charges for one of them.
     */
    private static final Pattern MULTIPACK =
            Pattern.compile("(?i)^multipack quantity:? ?(\\d+)$");

    private final ParseTuning tuning;

    public WalmartVocabulary() {
        this(ParseTuning.walmart());
    }

    public WalmartVocabulary(ParseTuning tuning) {
        this.tuning = tuning == null ? ParseTuning.walmart() : tuning;
    }

    @Override
    public String storeName() {
        return STORE_NAME;
    }

    @Override
    public ParseTuning tuning() {
        return tuning;
    }

    @Override
    public boolean isChrome(String text) {
        return ChromeFilter.isChrome(text);
    }

    @Override
    public boolean endsItemRegion(String text) {
        return ChromeFilter.endsItemRegion(text);
    }

    @Override
    public boolean isCarouselMarker(String text) {
        return ChromeFilter.hasStarGlyph(text);
    }

    @Override
    public String orderNumberIn(String text) {
        return ChromeFilter.orderNumberIn(text);
    }

    @Override
    public Long orderDateMillis(String text) {
        return OrderFieldExtractor.orderDateMillis(text);
    }

    @Override
    public String defaultLabel(long epochMillis) {
        return OrderFieldExtractor.defaultLabel(epochMillis);
    }

    @Override
    public OrderField summaryLabelOf(String leftText) {
        return OrderFieldExtractor.labelOf(leftText);
    }

    @Override
    public String sectionNameOf(String text) {
        return SectionHeaders.sectionNameOf(text);
    }

    @Override
    public int deliveredUnitCount(String text) {
        return SectionHeaders.deliveredUnitCount(text);
    }

    @Override
    public boolean isExcludedSection(String sectionName) {
        return SectionHeaders.isExcludedSection(sectionName);
    }

    @Override
    public int quantityIn(String line) {
        Matcher matcher = QTY.matcher(Normalise.text(line));
        if (!matcher.matches()) {
            return -1;
        }
        try {
            return Math.max(1, Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    @Override
    public boolean isRowMetadata(String line) {
        return MULTIPACK.matcher(Normalise.text(line)).matches()
                || RowAnnotations.isPriceCommentary(line);
    }

    @Override
    public long savingsCentsIn(String line) {
        return RowAnnotations.savingsCentsIn(line);
    }
}
