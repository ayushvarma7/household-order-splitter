package com.householdsplitter.core.parse.walmart;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.parse.model.OcrElement;
import com.householdsplitter.core.parse.model.ParsedItem;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Row grouping, the key algorithm. SPEC 8.3.
 *
 * <p>The thing that breaks naive parsers is stated in SPEC 8.3 and again in PROMPT 4: the
 * price belongs to the whole block, not to the line it sits beside. A Walmart name wraps
 * over two to four lines with the price right-aligned against the <em>first</em> of them,
 * so pairing a price with the text at its own y-coordinate captures a fragment such as
 * "Great Value Triple Cheddar" and silently drops the rest of the name.
 *
 * <p>So a block opens at a line price and runs downward until the next line price, a
 * section header or a chrome element (SPEC 8.3.4, 8.3.5), and every left-column line in
 * between is joined into one name (SPEC 8.3.6, 8.3.10).
 */
final class BlockAssembler {

    private static final Pattern QTY = Pattern.compile("(?i)^qty:? (\\d+)$");
    private static final Pattern MULTIPACK = Pattern.compile("(?i)^multipack quantity:? ?(\\d+)$");
    private static final Pattern SIZE_FRAGMENT =
            Pattern.compile("(?i)^[\\d.]+ ?(fl ?[o0]z|[o0]z|lb|lbs|ct|pk|gal|qt|g|kg|ml|l)\\.?$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private BlockAssembler() {
    }

    /**
     * @param bands          classified bands for one screenshot, ordered top to bottom
     * @param itemsEndIndex  the first band that belongs to the payment or summary region
     */
    static List<ParsedItem> assemble(List<TextBand> bands, int itemsEndIndex, ParseTuning tuning) {
        List<ParsedItem> items = new ArrayList<>();

        for (int i = 0; i < itemsEndIndex; i++) {
            TextBand start = bands.get(i);
            if (!start.hasLinePrice() || start.kind() != TextBand.Kind.CONTENT) {
                continue;
            }

            // SPEC 8.3.5: the block ends at the next line price, section header or chrome.
            int end = itemsEndIndex;
            for (int j = i + 1; j < itemsEndIndex; j++) {
                TextBand next = bands.get(j);
                if (next.hasLinePrice()
                        || next.kind() == TextBand.Kind.SECTION
                        || next.kind() == TextBand.Kind.CHROME
                        || next.kind() == TextBand.Kind.SUMMARY) {
                    end = j;
                    break;
                }
            }

            ParsedItem item = buildItem(bands, i, end, tuning);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private static ParsedItem buildItem(List<TextBand> bands, int startIndex, int endIndex,
                                        ParseTuning tuning) {
        TextBand priceBand = bands.get(startIndex);
        long lineTotalCents;
        try {
            lineTotalCents = PriceTokens.toCents(priceBand.linePrice().text());
        } catch (NumberFormatException notAnAmount) {
            return null;
        }

        // SPEC 8.3.6 puts name text in "the left 65%", but that fraction is a description
        // of where the name column sits, not a place to cut words off. A long name line
        // legitimately runs past 65% of the width, and slicing at the fraction silently
        // drops its tail: "Cheese Snack, 9 oz Bag, 12" loses the "12", and
        // "Finely Shredded Cheese, 8 oz" loses the "8 oz". The real boundary is the price
        // column, and this block's own line price marks exactly where that starts.
        final int nameBoundaryPx = nameBoundary(priceBand, tuning);

        List<String> nameLines = new ArrayList<>();
        List<String> rawLines = new ArrayList<>();
        String unitPriceText = null;
        int quantity = 1;
        int lowestConfidence = -1;

        for (int index = startIndex; index < endIndex; index++) {
            TextBand band = bands.get(index);
            rawLines.add(band.text());

            List<OcrElement> elements = band.elements();
            StringBuilder line = new StringBuilder();
            for (int e = 0; e < elements.size(); e++) {
                OcrElement element = elements.get(e);
                if (element == band.linePrice()) {
                    continue;
                }
                // SPEC 8.3.3: a unit price is captured for display and never charged.
                if (PriceTokens.isUnitPriceAt(elements, e)) {
                    if (unitPriceText == null) {
                        unitPriceText = PriceTokens.unitPriceTextAt(elements, e);
                    }
                    continue;
                }
                if (e > 0 && PriceTokens.startsWithUnitSuffix(element.text())
                        && PriceTokens.isUnitPriceAt(elements, e - 1)) {
                    continue;
                }
                // Names come from the left of the price column (SPEC 8.3.6).
                if (centerX(element) >= nameBoundaryPx) {
                    continue;
                }
                if (line.length() > 0) {
                    line.append(' ');
                }
                line.append(element.text());
            }

            String text = line.toString().trim();
            if (!text.isEmpty()) {
                nameLines.add(text);
            }
            int confidence = band.minConfidencePercent(tuning.nameZoneEndPermille);
            if (confidence >= 0) {
                lowestConfidence = lowestConfidence < 0
                        ? confidence : Math.min(lowestConfidence, confidence);
            }
        }

        // SPEC 8.3.7 and 8.3.8: strip the metadata lines, keeping the quantity.
        List<String> kept = new ArrayList<>();
        for (String candidate : nameLines) {
            Matcher qty = QTY.matcher(candidate);
            if (qty.matches()) {
                quantity = Math.max(1, Integer.parseInt(qty.group(1)));
                continue;
            }
            if (MULTIPACK.matcher(candidate).matches()) {
                continue;
            }
            if (PriceTokens.isBareUnitPriceLine(candidate)) {
                if (unitPriceText == null) {
                    unitPriceText = candidate.trim();
                }
                continue;
            }
            kept.add(candidate);
        }
        kept = dropRedundantSizeFragments(kept);

        String name = normaliseWhitespace(String.join(" ", kept));
        if (name.isEmpty()) {
            // SPEC 8.7.5: an edge fragment must never become a nameless line item.
            return null;
        }

        ParsedItem.Builder builder = ParsedItem.builder()
                .name(name)
                .rawOcrText(String.join("\n", rawLines))
                .quantity(quantity)
                .lineTotalCents(lineTotalCents)
                .unitPriceText(unitPriceText)
                .sourceSection(priceBand.sectionName())
                .imageIndex(priceBand.imageIndex())
                .scope(SectionHeaders.isExcludedSection(priceBand.sectionName())
                        ? Scope.EXCLUDED : Scope.UNASSIGNED);

        ConfidenceRules.apply(builder, name, quantity, lineTotalCents, lowestConfidence,
                priceBand.strikeThroughResolved(), priceBand.sectionName(), tuning);
        return builder.build();
    }

    private static int centerX(OcrElement element) {
        return (element.left() + element.right()) / 2;
    }

    /**
     * Where the name column ends: just left of this block's line price, with a small gutter.
     * Falls back to the tuning fraction when the price sits somewhere unexpected.
     */
    private static int nameBoundary(TextBand priceBand, ParseTuning tuning) {
        OcrElement price = priceBand.linePrice();
        int imageWidth = price.imageWidth();
        int fractionBoundary = (imageWidth * tuning.nameZoneEndPermille) / 1000;
        int gutter = Math.max(4, imageWidth / 50);
        int priceBoundary = price.left() - gutter;
        return priceBoundary > fractionBoundary ? priceBoundary : fractionBoundary;
    }

    /**
     * SPEC 8.3.7: "a bare weight/size fragment that already appears within the name".
     * Walmart repeats the pack size on its own metadata line under a name that already
     * ends in it.
     */
    private static List<String> dropRedundantSizeFragments(List<String> lines) {
        List<String> result = new ArrayList<>(lines);
        for (int i = result.size() - 1; i >= 0; i--) {
            String candidate = result.get(i).trim();
            if (!SIZE_FRAGMENT.matcher(candidate).matches()) {
                continue;
            }
            StringBuilder others = new StringBuilder();
            for (int j = 0; j < result.size(); j++) {
                if (j != i) {
                    others.append(result.get(j)).append(' ');
                }
            }
            if (others.toString().toLowerCase().contains(candidate.toLowerCase())) {
                result.remove(i);
            }
        }
        return result;
    }

    /** SPEC 8.3.9. */
    static String normaliseWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return WHITESPACE.matcher(text.replace('\n', ' ')).replaceAll(" ").trim();
    }
}
