package com.householdsplitter.core.parse.layout;

import com.householdsplitter.core.parse.model.OcrElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Groups elements into {@link TextBand}s by vertical overlap. */
final class BandBuilder {

    private BandBuilder() {
    }

    static List<TextBand> build(List<OcrElement> elements, ParseTuning tuning) {
        List<OcrElement> sorted = new ArrayList<>(elements);
        sorted.sort(Comparator.comparingInt(OcrElement::top).thenComparingInt(OcrElement::left));

        List<TextBand> bands = new ArrayList<>();
        for (OcrElement element : sorted) {
            TextBand target = null;
            for (int i = bands.size() - 1; i >= 0; i--) {
                TextBand candidate = bands.get(i);
                if (candidate.top() > element.bottom()) {
                    // Sorted by top, so nothing earlier can overlap either.
                    continue;
                }
                if (overlapsEnough(candidate, element, tuning)) {
                    target = candidate;
                    break;
                }
            }
            if (target == null) {
                bands.add(new TextBand(element));
            } else {
                target.add(element);
            }
        }

        for (TextBand band : bands) {
            band.sort();
        }
        bands.sort(Comparator.comparingInt(TextBand::top));
        return bands;
    }

    /**
     * Two boxes belong together when they share at least {@code bandOverlapPermille} of the
     * shorter box's height. Using the shorter box means a tall element does not swallow a
     * neighbouring row.
     */
    private static boolean overlapsEnough(TextBand band, OcrElement element, ParseTuning tuning) {
        int overlap = Math.min(band.bottom(), element.bottom()) - Math.max(band.top(), element.top());
        if (overlap <= 0) {
            return false;
        }
        int shorter = Math.min(Math.max(1, band.heightPx()), Math.max(1, element.heightPx()));
        return (overlap * 1000) / shorter >= tuning.bandOverlapPermille;
    }
}
