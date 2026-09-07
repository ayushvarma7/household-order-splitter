package com.householdsplitter.core.parse.walmart;

import com.householdsplitter.core.parse.model.OcrElement;
import com.householdsplitter.core.parse.model.OrderField;
import com.householdsplitter.core.parse.model.ParsedItem;
import com.householdsplitter.core.parse.model.ParsedOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Walmart order page reader. SPEC 8.2 to 8.9.
 *
 * <p>Pure Java over {@link OcrElement}s, which is what lets the whole of SPEC 12.4 run as
 * plain JUnit against captured fixture element lists, with no images and no network. The
 * Android and ML Kit half of the job lives in the app module.
 *
 * <p>SPEC 3.5: nothing above this class knows the layout is Walmart's, so a second store
 * is a second implementation of this pipeline and no change to the domain layer.
 */
public final class WalmartLayoutParser {

    private final ParseTuning tuning;

    public WalmartLayoutParser() {
        this(ParseTuning.defaults());
    }

    public WalmartLayoutParser(ParseTuning tuning) {
        this.tuning = tuning == null ? ParseTuning.defaults() : tuning;
    }

    public ParseTuning tuning() {
        return tuning;
    }

    /**
     * @param pages one element list per screenshot, in the user's chosen order (SPEC 7.4.4,
     *              SPEC 8.7.1)
     */
    public ParsedOrder parse(List<List<OcrElement>> pages) {
        if (pages == null || pages.isEmpty()) {
            return ParsedOrder.empty();
        }
        List<PageParse> parsed = new ArrayList<>(pages.size());
        for (int index = 0; index < pages.size(); index++) {
            parsed.add(parsePage(pages.get(index), index));
        }
        return Stitcher.merge(parsed, tuning);
    }

    /** Convenience for the single screenshot case. */
    public ParsedOrder parseSinglePage(List<OcrElement> elements) {
        return parse(Collections.singletonList(elements));
    }

    private PageParse parsePage(List<OcrElement> raw, int imageIndex) {
        PageParse page = new PageParse(imageIndex);
        if (raw == null || raw.isEmpty()) {
            return page;
        }

        List<OcrElement> cropped = crop(raw);
        if (cropped.isEmpty()) {
            return page;
        }

        List<TextBand> bands = BandBuilder.build(cropped, tuning);
        int medianHeight = medianHeight(cropped);

        classify(bands, page);
        markAppBarFurniture(bands);
        markRatingCarousel(bands);
        propagateSections(bands);

        int itemsEnd = itemsEndIndex(bands);
        detectLinePrices(bands, itemsEnd, medianHeight);

        page.items.addAll(BlockAssembler.assemble(bands, itemsEnd, tuning));
        extractOrderFields(bands, page);
        return page;
    }

    /**
     * SPEC 8.2.3: discard the status bar and the gesture bar. Tunable constants, not magic
     * numbers in a loop, and expressed as fractions of image height so a tablet screenshot
     * behaves the same as a phone one (SPEC 11.9).
     */
    private List<OcrElement> crop(List<OcrElement> elements) {
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

    private void classify(List<TextBand> bands, PageParse page) {
        for (TextBand band : bands) {
            String full = band.text();
            String left = band.leftText(tuning.nameZoneEndPermille);

            if (full.isEmpty()) {
                band.kind(TextBand.Kind.CHROME);
                continue;
            }
            // SPEC 8.4.5 and 8.6.6: the order number is captured, then excluded.
            if (ChromeFilter.orderNumberIn(full) != null) {
                band.kind(TextBand.Kind.ORDER_NUMBER);
                continue;
            }
            // SPEC 8.6.1
            if (OrderFieldExtractor.orderDateMillis(full) != null) {
                band.kind(TextBand.Kind.APP_BAR);
                continue;
            }
            // SPEC 8.5.1
            String section = SectionHeaders.sectionNameOf(left.isEmpty() ? full : left);
            if (section != null) {
                band.kind(TextBand.Kind.SECTION);
                band.sectionName(section);
                continue;
            }
            // SPEC 8.6.3. Checked before chrome so a label such as
            // "Free delivery from store" is read as a summary row, not discarded.
            if (OrderFieldExtractor.isSummaryLabel(left)) {
                band.kind(TextBand.Kind.SUMMARY);
                continue;
            }
            // SPEC 8.4. A band holding nothing but a right-aligned price has empty left
            // text, and must not be mistaken for empty chrome: it is what opens a block.
            String probe = left.isEmpty() ? full : left;
            if (!probe.isEmpty() && ChromeFilter.isChrome(probe)) {
                band.kind(TextBand.Kind.CHROME);
                continue;
            }
            band.kind(TextBand.Kind.CONTENT);
        }
    }

    /**
     * The blue app bar is furniture, and so is everything sharing its rows.
     *
     * <p>On a real order page the bar carries the title, a back chevron and a cart that
     * prints its own total, commonly "$0.00". That total sits in the right-hand price
     * column (SPEC 8.3.2) and is a valid price token, so without this it opens an item
     * block. The block is currently discarded anyway, because it has no name and SPEC 8.7.5
     * forbids nameless rows, but relying on that is relying on an accident: one stray word
     * beneath the bar would turn the cart into a phantom purchase. The bar is identified by
     * its date title (SPEC 8.6.1), and its vertical extent then defines the region.
     */
    private void markAppBarFurniture(List<TextBand> bands) {
        int appBarBottom = -1;
        for (TextBand band : bands) {
            if (band.kind() == TextBand.Kind.APP_BAR) {
                appBarBottom = Math.max(appBarBottom, band.bottom());
            }
        }
        if (appBarBottom < 0) {
            return;
        }
        for (TextBand band : bands) {
            if (band.kind() == TextBand.Kind.APP_BAR) {
                continue;
            }
            // Anything whose vertical midpoint is level with the bar or above it.
            if ((band.top() + band.bottom()) / 2 <= appBarBottom) {
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
    private void markRatingCarousel(List<TextBand> bands) {
        for (int i = 0; i < bands.size(); i++) {
            if (!ChromeFilter.hasStarGlyph(bands.get(i).text())) {
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
    private int itemsEndIndex(List<TextBand> bands) {
        for (int i = 0; i < bands.size(); i++) {
            TextBand band = bands.get(i);
            if (band.kind() == TextBand.Kind.SUMMARY) {
                return i;
            }
            if (ChromeFilter.endsItemRegion(band.leftText(tuning.nameZoneEndPermille))
                    || ChromeFilter.endsItemRegion(band.text())) {
                return i;
            }
        }
        return bands.size();
    }

    /**
     * SPEC 8.3.1 and 8.3.2. A line price is a price token in the right-most zone, rendered
     * at least as large as body text. Unit prices are excluded here and captured during
     * block assembly instead (SPEC 8.3.3).
     */
    private void detectLinePrices(List<TextBand> bands, int itemsEnd, int medianHeight) {
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
    private void extractOrderFields(List<TextBand> bands, PageParse page) {
        long otherFees = 0L;
        boolean sawOtherFee = false;

        for (TextBand band : bands) {
            switch (band.kind()) {
                case ORDER_NUMBER: {
                    String number = ChromeFilter.orderNumberIn(band.text());
                    if (number != null) {
                        page.externalOrderNo = number;
                    }
                    break;
                }
                case APP_BAR: {
                    Long millis = OrderFieldExtractor.orderDateMillis(band.text());
                    if (millis != null) {
                        page.orderDateMillis = millis;
                        page.label = OrderFieldExtractor.defaultLabel(millis);
                    }
                    break;
                }
                case SUMMARY: {
                    OrderField field =
                            OrderFieldExtractor.labelOf(band.leftText(tuning.nameZoneEndPermille));
                    Long amount = OrderFieldExtractor.amountOnBand(band);
                    if (field == null || amount == null) {
                        break;
                    }
                    if (band.strikeThroughResolved()) {
                        page.warnings.add("A struck-through price was found beside \""
                                + band.leftText(tuning.nameZoneEndPermille)
                                + "\"; the last amount on the line was used.");
                    }
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
