package com.householdsplitter.core.parse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import com.householdsplitter.core.parse.layout.StoreVocabulary;

import org.junit.Test;

public class StoreKindTest {

    @Test
    public void everyKindNamesItselfAndBuildsAReader() {
        for (StoreKind kind : StoreKind.values()) {
            assertNotNull(kind.displayName());
            assertNotNull(kind.vocabulary());
            assertEquals("the reader agrees with the enum about the store",
                    kind.displayName(), kind.layoutParser().storeName());
        }
    }

    @Test
    public void storedByNameSoReorderingTheEnumCannotReinterpretARow() {
        for (StoreKind kind : StoreKind.values()) {
            assertSame(kind, StoreKind.fromName(kind.name()));
        }
    }

    @Test
    public void anUnknownNameFallsBackToWalmart() {
        // Every order that existed before the store column did was a Walmart order, so an
        // unreadable value means an old row rather than a store the app has never heard of.
        assertSame(StoreKind.WALMART, StoreKind.fromName(null));
        assertSame(StoreKind.WALMART, StoreKind.fromName(""));
        assertSame(StoreKind.WALMART, StoreKind.fromName("COSTCO"));
    }

    @Test
    public void theTwoStoresAreCalibratedDifferently() {
        StoreVocabulary walmart = StoreKind.WALMART.vocabulary();
        StoreVocabulary amazon = StoreKind.AMAZON_FRESH.vocabulary();

        assertNotEquals("the name column does not start in the same place",
                walmart.tuning().nameZoneStartPermille, amazon.tuning().nameZoneStartPermille);
        assertNotEquals("nor end in the same place",
                walmart.tuning().nameZoneEndPermille, amazon.tuning().nameZoneEndPermille);
        assertNotEquals("nor does the price column",
                walmart.tuning().linePriceZoneStartPermille,
                amazon.tuning().linePriceZoneStartPermille);
        assertNotEquals("and Amazon pins a navigation bar over the foot of the page",
                walmart.tuning().bottomCropPermille, amazon.tuning().bottomCropPermille);
    }

    @Test
    public void neitherStoreAnswersForTheOthersSummaryWording() {
        StoreVocabulary walmart = StoreKind.WALMART.vocabulary();
        StoreVocabulary amazon = StoreKind.AMAZON_FRESH.vocabulary();

        // This asymmetry is the concrete reason the store is asked for rather than detected:
        // each vocabulary only recognises its own labels, so handing pages to the wrong one
        // does not fail loudly, it silently reads no summary at all.
        assertNotNull(amazon.summaryLabelOf("Grand Total:"));
        assertEquals(null, walmart.summaryLabelOf("Grand Total:"));

        assertNotNull(amazon.summaryLabelOf("Item(s) Subtotal:"));
        assertEquals(null, walmart.summaryLabelOf("Item(s) Subtotal:"));

        assertNotNull(walmart.summaryLabelOf("Subtotal"));
        assertNotNull(amazon.summaryLabelOf("Subtotal"));
    }
}
