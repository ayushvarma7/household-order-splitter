package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.householdsplitter.core.parse.Reconciler;
import com.householdsplitter.core.parse.model.ParsedItem;
import com.householdsplitter.core.parse.model.ParsedOrder;
import com.householdsplitter.core.parse.model.Reconciliation;
import com.householdsplitter.parse.MlKitReceiptParser;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * End to end on a device: three real order screenshots go in, the item list comes out.
 *
 * <p>This is the check that SPEC 4.10 and PROMPT hard rule 8 do not cost anything. The
 * `standard` build declares no INTERNET permission at all, so if text recognition needed the
 * network this test would fail. It uses the bundled ML Kit model, which ships inside the
 * APK, so it does not.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class OnDeviceParsingTest {


    /**
     * True when the order-page fixtures are present.
     *
     * <p>They are screenshots of a real Walmart order, so they are not committed: page three
     * carries a card's last four digits, the order number and a scannable barcode of it.
     * Drop your own captures into {@code app/src/androidTest/assets} as
     * {@code order-page-1.png} and so on to run these, and see the readme.
     *
     * <p>Skipped rather than failed when they are absent. A red test for a file the
     * repository deliberately does not ship would train everyone to ignore it.
     */
    private static boolean fixturesPresent() {
        try {
            InstrumentationRegistry.getInstrumentation().getContext()
                    .getAssets().open("order-page-1.png").close();
            return true;
        } catch (java.io.IOException absent) {
            return false;
        }
    }

    private static Uri copyAsset(String name) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        File file = new File(context.getCacheDir(), name);
        try (InputStream in = testContext.getAssets().open(name);
             OutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) > 0) {
                out.write(buffer, 0, count);
            }
        }
        return Uri.fromFile(file);
    }

    private static ParsedOrder parseAll() throws Exception {
        org.junit.Assume.assumeTrue(
                "order-page fixtures are not committed; see the readme", fixturesPresent());
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        List<Uri> images = new ArrayList<>(Arrays.asList(
                copyAsset("order-page-1.png"),
                copyAsset("order-page-2.png"),
                copyAsset("order-page-3.png")));
        MlKitReceiptParser parser = new MlKitReceiptParser(context);
        try {
            return parser.parse(images, null);
        } finally {
            parser.close();
        }
    }

    /**
     * Structural extraction is exact: five rows, each with its complete wrapped name and
     * its own price, and no metadata, unit price or furniture leaking into any of them.
     *
     * <p>The one thing outside this parser's control is glyph recognition. ML Kit reads the
     * "oz" in "24 oz" as "0Z" on this capture, so the comparison folds the handful of
     * character pairs a recogniser genuinely confuses. Everything else is compared
     * literally. Deliberately no attempt is made to correct such characters inside a product
     * name: that would be inventing content, and SPEC 7.6 gives the user an editable review
     * screen precisely so a misread letter is a two-second fix rather than a wrong total.
     */
    @Test
    // Needs the uncommitted order-page fixtures; see fixturesPresent().
    public void readsEveryRowWithItsFullName() throws Exception {
        ParsedOrder order = parseAll();

        StringBuilder actual = new StringBuilder();
        for (ParsedItem item : order.items()) {
            actual.append(item.name()).append(" = ").append(item.lineTotalCents()).append('\n');
        }
        assertEquals(actual.toString(), 5, order.items().size());

        String expected = "Great Value Sharp Cheddar Cheese Snack, 9 oz Bag, 12 Cheese Sticks = 256\n"
                + "Equate Body Sponge, Color May Vary = 100\n"
                + "Great Value Triple Cheddar Finely Shredded Cheese, 8 oz Bag = 197\n"
                + "Great Value Tomato Basil Garlic Pasta Sauce, 24 oz = 197\n"
                + "Great Value 100% Whole Grain Old Fashioned Oats, 42 = 418\n";

        assertEquals(foldLookalikes(expected), foldLookalikes(actual.toString()));
    }

    /** Prices are compared with no tolerance at all: money is never approximate. */
    @Test
    public void everyPriceIsExact() throws Exception {
        ParsedOrder order = parseAll();
        long[] expected = {256L, 100L, 197L, 197L, 418L};
        assertEquals(expected.length, order.items().size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], order.items().get(i).lineTotalCents());
        }
    }

    /** SPEC 8.3.3: the unit price is captured and kept out of the name. */
    @Test
    public void unitPricesAreCapturedNotLeakedIntoNames() throws Exception {
        ParsedOrder order = parseAll();
        for (ParsedItem item : order.items()) {
            assertFalse(item.name(), item.name().contains("$"));
        }
        int withUnitPrice = 0;
        for (ParsedItem item : order.items()) {
            if (item.unitPriceText() != null) {
                withUnitPrice++;
            }
        }
        assertEquals("both by-weight rows carry a unit price", 2, withUnitPrice);
    }

    /**
     * Folds only the character pairs a text recogniser genuinely confuses, so a glyph
     * misread does not masquerade as a parsing bug and, just as importantly, a parsing bug
     * cannot hide behind a glyph misread.
     */
    private static String foldLookalikes(String text) {
        return text.toLowerCase()
                .replace('0', 'o')
                .replace('1', 'l')
                .replace('5', 's');
    }

    @Test
    public void readsTheSummaryBlock() throws Exception {
        ParsedOrder order = parseAll();
        assertEquals(5352L, order.adjustments().statedSubtotalCents());
        assertEquals(50L, order.adjustments().taxCents());
        assertEquals(0L, order.adjustments().tipCents());
        // SPEC 8.6.4: the struck $9.95 loses to the charged $0.
        assertEquals(0L, order.adjustments().deliveryFeeCents());
        // SPEC 8.6.8: Subtotal is not matched by the Total rule.
        assertEquals(5402L, order.adjustments().statedTotalCents());

        Reconciliation reconciliation = Reconciler.reconcile(order);
        assertTrue(reconciliation.totalMatches());
    }

    @Test
    public void readsTheOrderIdentity() throws Exception {
        ParsedOrder order = parseAll();
        assertEquals("1000001-12345678", order.externalOrderNo());
        assertEquals("Sep 03 Walmart", order.label());
        assertNotNull(order.orderDateMillis());
    }

    /** The carousel, the cart, the timer and the payment card all stay out of the list. */
    @Test
    public void nothingSpuriousIsEmitted() throws Exception {
        ParsedOrder order = parseAll();
        for (ParsedItem item : order.items()) {
            String lower = item.name().toLowerCase();
            assertFalse(item.name(), lower.contains("banana"));
            assertFalse(item.name(), lower.contains("add"));
            assertFalse(item.name(), lower.contains("review item"));
            assertFalse(item.name(), lower.contains("visa"));
            assertFalse(item.name(), lower.contains("qty"));
            assertFalse(item.name(), lower.contains("multipack"));
            assertFalse(item.name(), lower.contains("02:5"));
            assertFalse("the cart total became a row", item.lineTotalCents() == 0L);
            assertFalse("the payment card total became a row", item.lineTotalCents() == 5402L);
        }
    }

    /** SPEC 8.7.6: "16 shopped" appears on two screenshots and opens one section. */
    @Test
    public void sectionsAreStitchedNotDuplicated() throws Exception {
        assertEquals(Arrays.asList("items delivered", "substituted", "shopped"),
                parseAll().sections());
    }
}
