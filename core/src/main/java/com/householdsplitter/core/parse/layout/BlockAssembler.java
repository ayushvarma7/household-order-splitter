package com.householdsplitter.core.parse.layout;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.parse.model.ItemBounds;
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

    private static final Pattern SIZE_FRAGMENT =
            Pattern.compile("(?i)^[\\d.]+ ?(fl ?[o0]z|[o0]z|lb|lbs|ct|pk|gal|qt|g|kg|ml|l)\\.?$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final StoreVocabulary vocabulary;
    private final ParseTuning tuning;

    BlockAssembler(StoreVocabulary vocabulary) {
        this.vocabulary = vocabulary;
        this.tuning = vocabulary.tuning();
    }

    /**
     * The same assembler against a page's own measured geometry rather than the store's.
     * Used by the restaurant reader, whose columns are calibrated per page.
     */
    BlockAssembler(StoreVocabulary vocabulary, ParseTuning tuning) {
        this.vocabulary = vocabulary;
        this.tuning = tuning == null ? vocabulary.tuning() : tuning;
    }

    /**
     * @param bands          classified bands for one screenshot, ordered top to bottom
     * @param itemsEndIndex  the first band that belongs to the payment or summary region
     */
    List<ParsedItem> assemble(List<TextBand> bands, int itemsEndIndex) {
        List<ParsedItem> items = new ArrayList<>();

        int lastOpened = -1;
        for (int i = 0; i < itemsEndIndex; i++) {
            TextBand start = bands.get(i);
            if (!start.hasLinePrice() || start.kind() != TextBand.Kind.CONTENT) {
                continue;
            }
            // A discounted row prints its original price struck through on the band below
            // the charged one, right-aligned in the same column. That is a second price
            // band, and treating it as a second item invents a charge nobody paid.
            if (isStruckOriginal(bands, i, lastOpened, itemsEndIndex)) {
                continue;
            }

            // SPEC 8.3.5: the block ends at the next line price, section header or chrome.
            int end = itemsEndIndex;
            for (int j = i + 1; j < itemsEndIndex; j++) {
                TextBand next = bands.get(j);
                if (next.kind() == TextBand.Kind.SECTION
                        || next.kind() == TextBand.Kind.CHROME
                        || next.kind() == TextBand.Kind.SUMMARY) {
                    end = j;
                    break;
                }
                // A struck-through original does not end the block either: the lines below
                // it, the quantity and the savings note, still belong to this item.
                if (next.hasLinePrice()
                        && !isStruckOriginal(bands, j, i, itemsEndIndex)) {
                    end = j;
                    break;
                }
            }

            // A till that prints the description above the figures leaves this band naming
            // nothing at all. Looking up for it is what stops every row being labelled with
            // the name of the row after it.
            int nameStart = i;
            if (vocabulary.nameMayPrecedePrice() && i > 0 && namesNothing(start)) {
                TextBand above = bands.get(i - 1);
                if (above.kind() == TextBand.Kind.CONTENT && !above.hasLinePrice()
                        && !namesNothing(above)) {
                    nameStart = i - 1;
                    // And the block stops at the price band. Growing downward from here
                    // would take the band above the next price, which is that row's
                    // description and is about to be claimed by it.
                    end = i + 1;
                }
            }

            ParsedItem item = buildItem(bands, i, nameStart, end);
            if (item != null) {
                items.add(item);
                lastOpened = i;
            }
        }
        return items;
    }

    /**
     * True when a price band carries the struck-through original of the item that opened
     * just above it, rather than a charge of its own.
     *
     * <p>The observed layout, from a real order:
     * <pre>
     *   Fresh Gala Apples, 3 lb Bag        $3.24     &lt;- charged, opens the block
     *   $1.08/lb                           $4.44     &lt;- struck through
     *   Qty 1
     *   $1.20 from savings
     * </pre>
     *
     * <p>Three things have to hold, and the third is what makes this arithmetic rather than
     * a guess:
     * <ol>
     *   <li>the band sits inside the block opened above, with no product name of its own,
     *       only a unit price, a quantity or a savings note;
     *   <li>its amount is larger than the charged one, since an original exceeds the price
     *       it was discounted to;
     *   <li>the difference equals the savings the row itself prints. 4.44 - 3.24 = 1.20, and
     *       the row says "$1.20 from savings".
     * </ol>
     *
     * <p>When the row prints no savings amount to check against, the band is left alone and
     * becomes an item, flagged for review. Dropping a charge on a hunch is the one outcome
     * worth avoiding: an invented row is visible in a list the user reads, a missing one is
     * a total that is quietly short.
     */
    private boolean isStruckOriginal(List<TextBand> bands, int candidateIndex,
                                            int openIndex, int itemsEndIndex) {
        if (openIndex < 0 || candidateIndex <= openIndex) {
            return false;
        }
        TextBand open = bands.get(openIndex);
        TextBand candidate = bands.get(candidateIndex);
        if (!open.hasLinePrice() || !candidate.hasLinePrice()) {
            return false;
        }

        long chargedCents;
        long candidateCents;
        try {
            chargedCents = PriceTokens.toCents(open.linePrice().text());
            candidateCents = PriceTokens.toCents(candidate.linePrice().text());
        } catch (NumberFormatException notAnAmount) {
            return false;
        }
        // An original price is higher than what it was discounted to.
        if (candidateCents <= chargedCents) {
            return false;
        }

        // Nothing between the two bands, nor on the candidate itself, may be a product name.
        for (int index = openIndex + 1; index <= candidateIndex; index++) {
            if (hasOwnName(bands.get(index))) {
                return false;
            }
        }

        // The row has to say how much came off, and the figure has to match.
        long savings = savingsCentsInCard(bands, openIndex, itemsEndIndex);
        return savings >= 0 && candidateCents - chargedCents == savings;
    }

    /**
     * True when a band contributes text to a product name: anything in the name column that
     * is not a unit price, a quantity, a multipack count or price commentary.
     */
    private boolean hasOwnName(TextBand band) {
        if (band.kind() != TextBand.Kind.CONTENT) {
            return false;
        }
        for (String line : nameZoneLines(band)) {
            if (vocabulary.quantityIn(line) > 0
                    || PriceTokens.isBareUnitPriceLine(line)
                    || vocabulary.isRowMetadata(line)
                    || vocabulary.isChrome(line)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * The savings amount printed anywhere in the item card that opened at
     * {@code openIndex}, in cents, or -1 when the card prints none.
     */
    private long savingsCentsInCard(List<TextBand> bands, int openIndex,
                                           int itemsEndIndex) {
        for (int index = openIndex; index < itemsEndIndex; index++) {
            TextBand band = bands.get(index);
            if (band.kind() == TextBand.Kind.SECTION || band.kind() == TextBand.Kind.SUMMARY) {
                return -1L;
            }
            // The card ends at its own buttons: "+ Add" and "Review item".
            if (index > openIndex && band.kind() == TextBand.Kind.CHROME) {
                return -1L;
            }
            for (String line : nameZoneLines(band)) {
                long savings = vocabulary.savingsCentsIn(line);
                if (savings >= 0) {
                    return savings;
                }
            }
            // A second product name means the card is over.
            if (index > openIndex && hasOwnNameIgnoringSavings(band)) {
                return -1L;
            }
        }
        return -1L;
    }

    private boolean hasOwnNameIgnoringSavings(TextBand band) {
        return hasOwnName(band);
    }

    /** The band's text, split per line, restricted to the name column. */
    private List<String> nameZoneLines(TextBand band) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        List<OcrElement> elements = band.elements();
        for (int e = 0; e < elements.size(); e++) {
            OcrElement element = elements.get(e);
            if (element == band.linePrice()) {
                continue;
            }
            if (!inNameZone(element)) {
                continue;
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(element.text());
        }
        String text = line.toString().trim();
        if (!text.isEmpty()) {
            lines.add(text);
        }
        return lines;
    }

    /**
     * The name column has a left edge as well as a right one. Left of it sits the product
     * thumbnail, and ML Kit reads the packaging inside it, so a bag of apples offers up
     * "GALA APPLES" as though it were part of the name.
     */
    private boolean inNameZone(OcrElement element) {
        int centre = element.centerXPermille();
        return centre >= tuning.nameZoneStartPermille
                && centre < tuning.nameZoneEndPermille;
    }

    /**
     * The box on the screenshot that this block occupies, in permille.
     *
     * <p>Every element of every band in the block counts, including the thumbnail, because
     * the point is to show the user the row as they saw it rather than just its text.
     */
    private ItemBounds measure(List<TextBand> bands, int startIndex, int endIndex) {
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        int imageIndex = -1;
        int width = 0, height = 0;

        for (int index = startIndex; index < endIndex; index++) {
            for (OcrElement element : bands.get(index).elements()) {
                // A block never spans two screenshots: it opens and closes on one page.
                if (imageIndex < 0) {
                    imageIndex = element.imageIndex();
                    width = element.imageWidth();
                    height = element.imageHeight();
                } else if (element.imageIndex() != imageIndex) {
                    continue;
                }
                left = Math.min(left, element.left());
                top = Math.min(top, element.top());
                right = Math.max(right, element.right());
                bottom = Math.max(bottom, element.bottom());
            }
        }
        if (imageIndex < 0 || width <= 0 || height <= 0 || right <= left || bottom <= top) {
            return ItemBounds.UNKNOWN;
        }
        return new ItemBounds(imageIndex,
                (left * 1000) / width, (top * 1000) / height,
                (right * 1000) / width, (bottom * 1000) / height);
    }

    /**
     * True when a band's own text, left of the price column, names nothing: it is a count,
     * a unit, a rate and an amount, with no word in it long enough to be a product.
     *
     * <p>Three letters, because a two letter token on a figures line is a unit (PC, EA, KG)
     * and a three letter one is usually a word (TEA, PIE, RUM). Getting this wrong in the
     * cautious direction costs a name that could have been improved; getting it wrong in
     * the other direction relabels a row.
     */
    private boolean namesNothing(TextBand band) {
        String text = band.leftText(tuning.nameZoneEndPermille);
        int run = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) {
                if (++run >= 3) {
                    return false;
                }
            } else {
                run = 0;
            }
        }
        return true;
    }

    /**
     * @param priceIndex the band carrying the amount, which fixes the price column
     * @param startIndex the first band contributing name text, normally the same one
     */
    private ParsedItem buildItem(List<TextBand> bands, int priceIndex, int startIndex,
                                 int endIndex) {
        TextBand priceBand = bands.get(priceIndex);
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
        final int nameBoundaryPx = nameBoundary(priceBand);

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
                // And from the right of the thumbnail. ML Kit reads the packaging in the
                // product image, which is how "GALA APPLES" ends up inside an item name.
                if (element.centerXPermille() < tuning.nameZoneStartPermille) {
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
            int printedQuantity = vocabulary.quantityIn(candidate);
            if (printedQuantity > 0) {
                quantity = printedQuantity;
                // A count printed in front of the dish leaves the dish behind it.
                String remainder = vocabulary.nameAfterQuantity(candidate);
                if (remainder != null && !remainder.isEmpty()) {
                    kept.add(remainder);
                }
                continue;
            }
            if (PriceTokens.isBareUnitPriceLine(candidate)) {
                if (unitPriceText == null) {
                    unitPriceText = candidate.trim();
                }
                continue;
            }
            // "$1.20 from savings" and "Ordered price $13.97" describe the price, they are
            // not part of what the thing is called. Both stay in rawOcrText.
            if (vocabulary.isRowMetadata(candidate)) {
                continue;
            }
            kept.add(candidate);
        }
        kept = dropRedundantSizeFragments(kept);

        String name = vocabulary.presentName(normaliseWhitespace(String.join(" ", kept)));
        boolean nameMissing = name.isEmpty();
        if (nameMissing) {
            // SPEC 8.7.5: at a screenshot edge, a row with no name is a fragment whose
            // counterpart is on the adjacent image, and it must not become a nameless item.
            //
            // Away from the edges there is no counterpart. A price with no name in the
            // middle of the page means the name failed to recognise, and dropping the row
            // would quietly lose a charge: the totals would come out short with nothing to
            // point at. So the row is kept, named as unread, and flagged. SPEC 7.6.9 then
            // refuses to leave the review screen until the user has typed what it was.
            if (isNearEdge(priceBand)) {
                return null;
            }
            name = com.householdsplitter.core.parse.model.ParsedItem.NAME_NOT_READ;
        }

        ParsedItem.Builder builder = ParsedItem.builder()
                .name(name)
                .rawOcrText(String.join("\n", rawLines))
                .quantity(quantity)
                .lineTotalCents(lineTotalCents)
                .unitPriceText(unitPriceText)
                .sourceSection(priceBand.sectionName())
                .imageIndex(priceBand.imageIndex())
                .bounds(measure(bands, startIndex, endIndex))
                .scope(vocabulary.isExcludedSection(priceBand.sectionName())
                        ? Scope.EXCLUDED : Scope.UNASSIGNED);

        ConfidenceRules.apply(builder, name, quantity, lineTotalCents, lowestConfidence,
                priceBand.strikeThroughResolved(), priceBand.sectionName(), vocabulary);
        if (nameMissing) {
            builder.flag(com.householdsplitter.core.parse.model.ReviewReason.NAME_NOT_READ);
        }
        return builder.build();
    }

    private boolean isNearEdge(TextBand band) {
        int top = band.topPermille();
        int cropTop = tuning.headerZonePermille;
        int cropBottom = 1000 - tuning.bottomCropPermille;
        return top <= cropTop + tuning.edgeZonePermille
                || top >= cropBottom - tuning.edgeZonePermille;
    }

    private int centerX(OcrElement element) {
        return (element.left() + element.right()) / 2;
    }

    /**
     * Where the name column ends: just left of this block's line price, with a small gutter.
     * Falls back to the tuning fraction when the price sits somewhere unexpected.
     */
    private int nameBoundary(TextBand priceBand) {
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
    private List<String> dropRedundantSizeFragments(List<String> lines) {
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
