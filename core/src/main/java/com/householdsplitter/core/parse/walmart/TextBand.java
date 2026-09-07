package com.householdsplitter.core.parse.walmart;

import com.householdsplitter.core.parse.model.OcrElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A horizontal band of the screenshot: every element whose vertical span overlaps, sorted
 * left to right.
 *
 * <p>The band, not the text line, is the unit this parser reasons in. SPEC 8.3 makes the
 * point that a Walmart row's price is right-aligned against the first line of a name that
 * wraps over several lines, and SPEC 8.6.3 asks for the summary amount "on the same
 * horizontal band" as its label. Both are band statements, not line statements.
 */
final class TextBand {

    enum Kind {
        /** Ordinary content: name fragments, metadata, prices. */
        CONTENT,
        /** Interface furniture to discard (SPEC 8.4). */
        CHROME,
        /** A section header (SPEC 8.5.1). */
        SECTION,
        /** A row of the summary block (SPEC 8.6.3). */
        SUMMARY,
        /** The blue app bar carrying the order date (SPEC 8.6.1). */
        APP_BAR,
        /** The order number line (SPEC 8.4.5, SPEC 8.6.6). */
        ORDER_NUMBER
    }

    private final List<OcrElement> elements = new ArrayList<>();
    private final int imageIndex;
    private int top;
    private int bottom;

    private Kind kind = Kind.CONTENT;
    private String sectionName;
    private OcrElement linePrice;
    private boolean strikeThroughResolved;

    TextBand(OcrElement first) {
        this.imageIndex = first.imageIndex();
        this.top = first.top();
        this.bottom = first.bottom();
        this.elements.add(first);
    }

    void add(OcrElement element) {
        elements.add(element);
        top = Math.min(top, element.top());
        bottom = Math.max(bottom, element.bottom());
    }

    void sort() {
        elements.sort(Comparator.comparingInt(OcrElement::left).thenComparingInt(OcrElement::top));
    }

    List<OcrElement> elements() {
        return Collections.unmodifiableList(elements);
    }

    int imageIndex() {
        return imageIndex;
    }

    int top() {
        return top;
    }

    int bottom() {
        return bottom;
    }

    int topPermille() {
        return (int) (((long) top * 1000L) / elements.get(0).imageHeight());
    }

    int heightPx() {
        return Math.max(0, bottom - top);
    }

    /** Everything on the band, left to right. */
    String text() {
        StringBuilder out = new StringBuilder();
        for (OcrElement element : elements) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(element.text());
        }
        return out.toString().trim();
    }

    /** Only the left column (SPEC 8.3.6), which is where names and labels live. */
    String leftText(int zoneEndPermille) {
        StringBuilder out = new StringBuilder();
        for (OcrElement element : elements) {
            if (element.centerXPermille() < zoneEndPermille) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(element.text());
            }
        }
        return out.toString().trim();
    }

    List<OcrElement> elementsInLeftZone(int zoneEndPermille) {
        List<OcrElement> result = new ArrayList<>();
        for (OcrElement element : elements) {
            if (element.centerXPermille() < zoneEndPermille) {
                result.add(element);
            }
        }
        return result;
    }

    Kind kind() {
        return kind;
    }

    void kind(Kind value) {
        this.kind = value;
    }

    String sectionName() {
        return sectionName;
    }

    void sectionName(String value) {
        this.sectionName = value;
    }

    OcrElement linePrice() {
        return linePrice;
    }

    void linePrice(OcrElement value) {
        this.linePrice = value;
    }

    boolean hasLinePrice() {
        return linePrice != null;
    }

    /** SPEC 8.6.4 and 8.6.5: two amounts were printed and the last one won. */
    boolean strikeThroughResolved() {
        return strikeThroughResolved;
    }

    void strikeThroughResolved(boolean value) {
        this.strikeThroughResolved = value;
    }

    /** Lowest reported confidence on the band, or -1 when nothing reported one. */
    int minConfidencePercent(int zoneEndPermille) {
        int lowest = -1;
        for (OcrElement element : elements) {
            if (element.centerXPermille() >= zoneEndPermille) {
                continue;
            }
            int confidence = element.confidencePercent();
            if (confidence < 0) {
                continue;
            }
            lowest = lowest < 0 ? confidence : Math.min(lowest, confidence);
        }
        return lowest;
    }

    @Override
    public String toString() {
        return "[" + kind + " y=" + top + ".." + bottom + "] " + text();
    }
}
