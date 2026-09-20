package com.householdsplitter.core.parse.layout;

import com.householdsplitter.core.parse.LayoutParser;
import com.householdsplitter.core.parse.model.OcrElement;
import com.householdsplitter.core.parse.model.OrderField;
import com.householdsplitter.core.parse.model.ParsedItem;
import com.householdsplitter.core.parse.model.ParsedOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads an order page into a {@link ParsedOrder}, given a store's wording and geometry.
 *
 * <p>This is the shape of the job, and it is the same for every store: crop the furniture,
 * group elements into horizontal bands, classify each band, find the right-hand price
 * column, open an item block at a price and grow it downward, stop at the summary block,
 * then read the order-level figures. What a store supplies is the wording
 * ({@link StoreVocabulary}) and the column positions ({@link ParseTuning}). Neither appears
 * as a literal anywhere below.
 *
 * <p>Pure Java over {@link OcrElement}s, which is what lets the whole of SPEC 12.4 run as
 * plain JUnit against captured fixture element lists, with no images and no network. The
 * Android and ML Kit half of the job lives in the app module.
 *
 * <p>SPEC 3.5: nothing above this class knows which store it is reading, so a second store
 * is a second vocabulary and no change to the domain layer.
 */
public class ReceiptLayoutParser implements LayoutParser {

    private final StoreVocabulary vocabulary;
    private final ParseTuning tuning;

    public ReceiptLayoutParser(StoreVocabulary vocabulary) {
        if (vocabulary == null) {
            throw new IllegalArgumentException("a layout parser needs a store vocabulary");
        }
        this.vocabulary = vocabulary;
        this.tuning = vocabulary.tuning();
    }

    public ParseTuning tuning() {
        return tuning;
    }

    public StoreVocabulary vocabulary() {
        return vocabulary;
    }

    @Override
    public String storeName() {
        return vocabulary.storeName();
    }

    /**
     * @param pages one element list per screenshot, in the user's chosen order (SPEC 7.4.4,
     *              SPEC 8.7.1)
     */
    @Override
    public ParsedOrder parse(List<List<OcrElement>> pages) {
        return parse(pages, null);
    }

    /**
     * The same parse, recording what became of every band.
     *
     * <p>The trace changes nothing. That is the point of doing it this way rather than
     * writing a separate diagnostic pass: what gets explained has to be the parser that
     * actually ran, or the explanation drifts from the behaviour it claims to describe.
     *
     * @param trace filled in as the parse proceeds, or null to skip recording
     */
    public ParsedOrder parse(List<List<OcrElement>> pages, ParseTrace trace) {
        if (pages == null || pages.isEmpty()) {
            return ParsedOrder.empty();
        }
        List<PageParse> parsed = new ArrayList<>(pages.size());
        for (int index = 0; index < pages.size(); index++) {
            parsed.add(parsePage(pages.get(index), index, trace));
        }
        return Stitcher.merge(parsed, tuning);
    }

    /** Convenience for the single screenshot case. */
    public ParsedOrder parseSinglePage(List<OcrElement> elements) {
        return parse(Collections.singletonList(elements));
    }

    private PageParse parsePage(List<OcrElement> input, int imageIndex, ParseTrace trace) {
        PageParse page = new PageParse(imageIndex);
        if (input == null || input.isEmpty()) {
            return page;
        }

        // A photographed page is straightened before any column rule is applied to it,
        // because every one of those rules assumes the page is square (SPEC 8.3.2). A
        // screenshot store takes the default and is not touched.
        List<OcrElement> raw = vocabulary.needsDeskew() ? Deskew.straighten(input) : input;

        // A store with a fixed layout returns its own calibration unchanged, so this is the
        // identity for every screenshot store and cannot alter what they read.
        ParseTuning pageTuning = vocabulary.calibrate(raw, tuning);

        List<OcrElement> cropped = crop(raw, pageTuning);
        recordCropped(raw, cropped, imageIndex, trace);
        if (cropped.isEmpty()) {
            return page;
        }

        List<TextBand> bands = BandBuilder.build(cropped, pageTuning);
        int medianHeight = medianHeight(cropped);

        classify(bands, page, pageTuning);
        markHeaderZone(bands, pageTuning);
        markSuggestionCarousel(bands);
        propagateSections(bands);

        int itemsEnd = itemsEndIndex(bands, pageTuning);
        detectLinePrices(bands, itemsEnd, medianHeight, pageTuning);

        page.items.addAll(new BlockAssembler(vocabulary, pageTuning).assemble(bands, itemsEnd));
        extractOrderFields(bands, page, pageTuning);
        recordBands(bands, itemsEnd, imageIndex, trace, pageTuning);
        return page;
    }

    /** Text the margin crop threw away, which is where a clipped row goes. */
    private void recordCropped(List<OcrElement> raw, List<OcrElement> kept, int imageIndex,
                               ParseTrace trace) {
        if (trace == null || raw.size() == kept.size()) {
            return;
        }
        java.util.Set<OcrElement> survived = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<OcrElement, Boolean>());
        survived.addAll(kept);
        for (OcrElement element : raw) {
            if (!survived.contains(element)) {
                trace.addCropped(imageIndex, element.text());
            }
        }
    }

    /**
     * How far each band got, read off the state the parse has already settled.
     *
     * <p>Recorded after the fact rather than as decisions are taken, so no stage of the
     * parse has to carry a reporting concern and none of them can behave differently
     * because a trace is present.
     */
    private void recordBands(List<TextBand> bands, int itemsEnd, int imageIndex,
                             ParseTrace trace, ParseTuning tuning) {
        if (trace == null) {
            return;
        }
        for (int i = 0; i < bands.size(); i++) {
            TextBand band = bands.get(i);
            String text = band.text();
            if (text.isEmpty()) {
                continue;
            }
            trace.addBand(imageIndex, band.topPermille(), text,
                    band.leftText(tuning.nameZoneEndPermille),
                    stageOf(band, i, itemsEnd, tuning));
        }
    }

    private ParseTrace.Stage stageOf(TextBand band, int index, int itemsEnd,
                                     ParseTuning tuning) {
        if (band.topPermille() < tuning.headerZonePermille) {
            return ParseTrace.Stage.INSIDE_THE_HEADER;
        }
        if (index >= itemsEnd) {
            return ParseTrace.Stage.PAST_THE_ITEM_REGION;
        }
        if (band.kind() == TextBand.Kind.CHROME) {
            return ParseTrace.Stage.FILTERED_AS_CHROME;
        }
        if (band.kind() == TextBand.Kind.SECTION) {
            return ParseTrace.Stage.READ_AS_A_SECTION_HEADER;
        }
        if (band.kind() == TextBand.Kind.SUMMARY) {
            return ParseTrace.Stage.READ_AS_A_SUMMARY_LINE;
        }
        if (!band.hasLinePrice()) {
            return ParseTrace.Stage.NO_PRICE_IN_THE_COLUMN;
        }
        return ParseTrace.Stage.ELIGIBLE;
    }

    /**
     * SPEC 8.2.3: discard the status bar and the gesture bar. Tunable constants, not magic
     * numbers in a loop, and expressed as fractions of image height so a tablet screenshot
     * behaves the same as a phone one (SPEC 11.9).
     */
    private List<OcrElement> crop(List<OcrElement> elements, ParseTuning tuning) {
        List<OcrElement> kept = new ArrayList<>(elements.size());
        int bottomLimit = 1000 - tuning.bottomCropPermille;
        for (OcrElement element : elements) {
            int center = element.centerYPermille();
            if (center < tuning.topCropPermille || center > bottomLimit) {
                continue;
            }
            kept.add(element);
        }
        return kept;
    }

    private static int medianHeight(List<OcrElement> elements) {
        List<Integer> heights = new ArrayList<>(elements.size());
        for (OcrElement element : elements) {
            heights.add(element.heightPx());
        }
        Collections.sort(heights);
        if (heights.isEmpty()) {
            return 0;
        }
        return heights.get(heights.size() / 2);
    }

    private void classify(List<TextBand> bands, PageParse page, ParseTuning tuning) {
        for (TextBand band : bands) {
            String full = band.text();
            String left = band.leftText(tuning.nameZoneEndPermille);

            if (full.isEmpty()) {
                band.kind(TextBand.Kind.CHROME);
                continue;
            }
            // SPEC 8.4.5 and 8.6.6: the order number is captured, then excluded.
            if (vocabulary.orderNumberIn(full) != null) {
                band.kind(TextBand.Kind.ORDER_NUMBER);
                continue;
            }
            // SPEC 8.6.1
            if (vocabulary.orderDateMillis(full) != null) {
                band.kind(TextBand.Kind.APP_BAR);
                continue;
            }
            // SPEC 8.5.1
            String section = vocabulary.sectionNameOf(left.isEmpty() ? full : left);
            if (section != null) {
                band.kind(TextBand.Kind.SECTION);
                band.sectionName(section);
                int units = vocabulary.deliveredUnitCount(left.isEmpty() ? full : left);
                if (units > 0) {
                    page.deliveredUnitCount = units;
                }
                continue;
            }
            // SPEC 8.6.3. Checked before chrome so a label such as
            // "Free delivery from store" is read as a summary row, not discarded.
            if (vocabulary.isSummaryLabel(left)) {
                band.kind(TextBand.Kind.SUMMARY);
                continue;
            }
            // SPEC 8.4. A band holding nothing but a right-aligned price has empty left
            // text, and must not be mistaken for empty chrome: it is what opens a block.
            String probe = left.isEmpty() ? full : left;
            if (!probe.isEmpty() && vocabulary.isChrome(probe)) {
                band.kind(TextBand.Kind.CHROME);
                continue;
            }
            band.kind(TextBand.Kind.CONTENT);
        }
    }

    /**
     * The sticky header is a no-scan zone.
     *
     * <p>Everything above {@code headerZonePermille} is the status bar and the blue app
     * bar. The bar's date title has already been read by {@link #classify} for SPEC 8.6.1,
     * and everything else up there is discarded outright: the back chevron, the recording
     * timer, the battery, and in particular the cart, which prints the live basket value
     * and order status in the right-hand price column. That value has nothing to do with
     * this delivered order, so it is never eligible to become a line price or an item.
     *
     * <p>Zone based rather than detection based on purpose. Relying on spotting the bar
     * would leave the cart readable on any screenshot where the title failed to recognise,
     * and relying on the nameless-row guard of SPEC 8.7.5 to clean up afterwards would mean
     * one stray word beneath the bar turns the cart into a phantom purchase.
     */
    private void markHeaderZone(List<TextBand> bands, ParseTuning tuning) {
        int appBarBottom = -1;
        for (TextBand band : bands) {
            // Only a date band that is genuinely up in the header defines the bar's extent.
            // Walmart prints the order date in the app bar, so other elements really do sit
            // level with it and really are furniture. Amazon prints the same date as body
            // text inside the order summary card, two thirds of the way down the page;
            // treating that as a bar would sweep everything above it into chrome, and what
            // sits just above it is the summary block itself.
            if (band.kind() == TextBand.Kind.APP_BAR
                    && band.topPermille() < tuning.headerZonePermille) {
                appBarBottom = Math.max(appBarBottom, band.bottom());
            }
        }
        for (TextBand band : bands) {
            if (band.kind() == TextBand.Kind.APP_BAR) {
                continue;
            }
            boolean inZone = band.topPermille() < tuning.headerZonePermille;
            boolean levelWithBar = appBarBottom >= 0
                    && (band.top() + band.bottom()) / 2 <= appBarBottom;
            if (inZone || levelWithBar) {
                band.kind(TextBand.Kind.CHROME);
            }
        }
    }

    /**
     * SPEC 8.4.8, the single most likely parsing failure. Walmart puts a horizontally
     * scrolling strip of rating prompts near the top of the order page: a product image, a
     * name such as "Fresh Banana, Each", and five empty stars. They are suggestions, not
     * purchases.
     *
     * <p>Two defences, because one of them is structural and could quietly stop being true
     * if the layout moves. Structurally, a block only ever opens at a line price and grows
     * downward, so a card with no price in the right-hand column can never open one.
     * Explicitly, any band carrying a star glyph, and its neighbours that carry no line
     * price, are marked as chrome here.
     */
    private void markSuggestionCarousel(List<TextBand> bands) {
        for (int i = 0; i < bands.size(); i++) {
            if (!vocabulary.isCarouselMarker(bands.get(i).text())) {
                continue;
            }
            bands.get(i).kind(TextBand.Kind.CHROME);
            for (int j = i - 1; j >= 0 && j >= i - 3; j--) {
                TextBand candidate = bands.get(j);
                if (candidate.kind() == TextBand.Kind.SECTION
                        || candidate.kind() == TextBand.Kind.SUMMARY
                        || candidate.kind() == TextBand.Kind.APP_BAR) {
                    break;
                }
                candidate.kind(TextBand.Kind.CHROME);
            }
            for (int j = i + 1; j < bands.size() && j <= i + 3; j++) {
                TextBand candidate = bands.get(j);
                if (candidate.kind() == TextBand.Kind.SECTION
                        || candidate.kind() == TextBand.Kind.SUMMARY) {
                    break;
                }
                candidate.kind(TextBand.Kind.CHROME);
            }
        }
    }

    /** SPEC 8.5.2: a header opens a section and every row below carries its name. */
    private void propagateSections(List<TextBand> bands) {
        String current = null;
        for (TextBand band : bands) {
            if (band.kind() == TextBand.Kind.SECTION) {
                current = band.sectionName();
                continue;
            }
            band.sectionName(current);
        }
    }

    /**
     * Item extraction stops at the payment card or the summary block (SPEC 8.4.2, 8.6.3):
     * every amount below that point is an order-level figure, not a purchased row.
     */
    private int itemsEndIndex(List<TextBand> bands, ParseTuning tuning) {
        for (int i = 0; i < bands.size(); i++) {
            TextBand band = bands.get(i);
            if (band.kind() == TextBand.Kind.SUMMARY) {
                return i;
            }
            if (vocabulary.endsItemRegion(band.leftText(tuning.nameZoneEndPermille))
                    || vocabulary.endsItemRegion(band.text())) {
                return i;
            }
        }
        return bands.size();
    }

    /**
     * The item's price is the final amount billed for it, and nothing else.
     *
     * <p>Concretely: a price token in the right-hand column (SPEC 8.3.1, 8.3.2), and where
     * that column holds more than one amount, the right-most one wins. A Walmart row can
     * print an original price struck through beside the charged one, and only the charged
     * one is of any interest.
     *
     * <p>An amount in the left-hand column is never eligible, whatever it says. That column
     * carries unit prices such as "$3.94/lb" or "$1.31/lb", which describe a rate rather
     * than a charge; a row whose only amount is over there yields no line price, so it opens
     * no block and is discarded.
     */
    private void detectLinePrices(List<TextBand> bands, int itemsEnd, int medianHeight,
                                  ParseTuning tuning) {
        int minHeight = medianHeight <= 0
                ? 0
                : (medianHeight * tuning.linePriceMinHeightPermilleOfMedian) / 1000;

        for (int i = 0; i < itemsEnd; i++) {
            TextBand band = bands.get(i);
            if (band.kind() != TextBand.Kind.CONTENT) {
                continue;
            }
            List<OcrElement> elements = band.elements();
            int chosen = -1;
            int amountsInZone = 0;
            for (int e = 0; e < elements.size(); e++) {
                OcrElement element = elements.get(e);
                if (element.centerXPermille() < tuning.linePriceZoneStartPermille) {
                    continue;
                }
                if (PriceTokens.isUnitPriceAt(elements, e)) {
                    continue;
                }
                if (!PriceTokens.isPriceToken(element.text())) {
                    continue;
                }
                amountsInZone++;
                if (element.heightPx() < minHeight) {
                    continue;
                }
                // SPEC 8.6.5: on a discounted row the right-most amount is the charged one.
                chosen = e;
            }
            if (chosen >= 0) {
                band.linePrice(elements.get(chosen));
                if (amountsInZone > 1) {
                    band.strikeThroughResolved(true);
                }
            }
        }
    }

    /** SPEC 8.6. */
    private void extractOrderFields(List<TextBand> bands, PageParse page, ParseTuning tuning) {
        long otherFees = 0L;
        boolean sawOtherFee = false;

        for (TextBand band : bands) {
            switch (band.kind()) {
                case ORDER_NUMBER: {
                    String number = vocabulary.orderNumberIn(band.text());
                    if (number != null) {
                        page.externalOrderNo = number;
                    }
                    break;
                }
                case APP_BAR: {
                    Long millis = vocabulary.orderDateMillis(band.text());
                    if (millis != null) {
                        page.orderDateMillis = millis;
                        page.label = vocabulary.defaultLabel(millis);
                    }
                    break;
                }
                case SUMMARY: {
                    OrderField field =
                            vocabulary.summaryLabelOf(band.leftText(tuning.nameZoneEndPermille));
                    Long amount = BandAmounts.amountOnBand(band);
                    if (field == null || amount == null) {
                        break;
                    }
                    // No warning for two amounts on one band. The right-most is the charged
                    // figure by definition, which is exactly what the free-delivery line
                    // needs when it prints "$9.95 $0".
                    if (field == OrderField.OTHER_FEE) {
                        // SPEC 8.6.3: service, bag and below-minimum fees are summed.
                        otherFees += amount;
                        sawOtherFee = true;
                    } else if (field == OrderField.DISCOUNT) {
                        page.amounts.put(field, Math.abs(amount));
                    } else {
                        page.amounts.put(field, amount);
                    }
                    break;
                }
                case SECTION: {
                    // SPEC 8.5.2, recorded in encounter order for stitching (SPEC 8.7.6).
                    if (page.sections.isEmpty()
                            || !page.sections.get(page.sections.size() - 1).equals(band.sectionName())) {
                        page.sections.add(band.sectionName());
                    }
                    break;
                }
                default:
                    break;
            }
        }
        if (sawOtherFee) {
            page.amounts.put(OrderField.OTHER_FEE, otherFees);
        }
    }

    /** Exposed for the app layer's manual-entry path (SPEC 7.5.4). */
    public static List<ParsedItem> noItems() {
        return Collections.emptyList();
    }
}
