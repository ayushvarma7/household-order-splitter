package com.householdsplitter;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.householdsplitter.core.parse.Reconciler;
import com.householdsplitter.core.parse.StoreKind;
import com.householdsplitter.core.parse.layout.ColumnCalibration;
import com.householdsplitter.core.parse.layout.Deskew;
import com.householdsplitter.core.parse.layout.ReceiptLayoutParser;
import com.householdsplitter.core.parse.model.OcrElement;
import com.householdsplitter.core.parse.model.ParsedItem;
import com.householdsplitter.core.parse.model.ParsedOrder;
import com.householdsplitter.core.parse.model.Reconciliation;
import com.householdsplitter.ocr.MlKitTextSource;

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
 * The restaurant reader against real receipts, on a device, through the real recogniser.
 *
 * <p>Everything else that tests this reader builds its input by hand, which proves the
 * algorithm does what it was written to do and proves nothing whatever about whether it was
 * written to do the right thing. A fixture cannot surprise you. These can.
 *
 * <p>The fixtures are not in this repository: they are other people's photographs under
 * their own licences. {@code tools/receipts/fetch.sh} gets them. Absent, this skips rather
 * than fails, on the same reasoning as {@link OnDeviceParsingTest}: a red test for a file
 * the repository deliberately does not ship teaches everyone to ignore red tests.
 *
 * <p>It reports rather than asserts. A real receipt has no expected answer written down
 * anywhere, so the useful thing this can do is print what the reader made of each one, in
 * enough detail to tell a missed row from a misread one, and let a person read it.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class RealBillCalibrationTest {

    private static final String TAG = "BillCalibration";

    private static final List<String> BILLS = Arrays.asList(
            "bill-photo-1.jpg", "bill-photo-2.jpg",
            "bill-sroie-000.jpg", "bill-sroie-001.jpg", "bill-sroie-002.jpg",
            "bill-sroie-003.jpg", "bill-sroie-004.jpg", "bill-sroie-005.jpg",
            "bill-sroie-006.jpg", "bill-sroie-007.jpg",
            // A Burlington till receipt, photographed on a Pixel 8 and reported as reading
            // nothing at all. It is the first fixture here that came off a camera rather
            // than a scanner, which is exactly why it found what it found.
            "bill-burlington.jpg",
            // Three restaurant bills photographed by hand, and the hardest set here: a
            // hundred percent discount taking a total to zero, dish names wrapping over
            // two lines on curved paper, and a four column layout whose quantities are
            // printed as "1.00" and so look exactly like prices.
            "bill-real-1.jpg", "bill-real-2.jpg", "bill-real-3.jpg");

    private static boolean present(String name) {
        try {
            InstrumentationRegistry.getInstrumentation().getContext()
                    .getAssets().open(name).close();
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

    @Test
    public void readEveryBillAndSayWhatHappened() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ReceiptLayoutParser parser =
                (ReceiptLayoutParser) StoreKind.RESTAURANT.layoutParser();
        MlKitTextSource source = new MlKitTextSource(context,
                StoreKind.RESTAURANT.vocabulary().tuning().maxImageDimensionPx);
        try {
            for (String bill : BILLS) {
                if (!present(bill)) {
                    Log.i(TAG, "SKIP " + bill + " (not fetched)");
                    continue;
                }
                report(bill, source, parser);
            }
        } finally {
            source.close();
        }
    }

    private void report(String bill, MlKitTextSource source, ReceiptLayoutParser parser)
            throws Exception {
        List<OcrElement> elements = source.read(copyAsset(bill), 0);
        int slope = Deskew.slopePermille(elements);
        int column = ColumnCalibration.columnStartPermille(
                slope == 0 ? elements : Deskew.straighten(elements));

        List<List<OcrElement>> pages = new ArrayList<>();
        pages.add(elements);
        ParsedOrder order = parser.parse(pages);
        Reconciliation check = Reconciler.reconcile(order);

        Log.i(TAG, "================ " + bill);
        Log.i(TAG, "  ocr elements   " + elements.size());
        Log.i(TAG, "  measured tilt  " + slope + " permille");
        Log.i(TAG, "  amount column  " + (column < 0 ? "NOT FOUND" : column + " permille"));
        Log.i(TAG, "  items          " + order.items().size());
        for (ParsedItem item : order.items()) {
            Log.i(TAG, String.format("    %-40s x%-3d %8d  %s",
                    item.name(), item.quantity(), item.lineTotalCents(),
                    item.needsReview() ? item.reviewReasons().toString() : ""));
        }
        Log.i(TAG, "  items add to   " + order.itemsSubtotalCents());
        Log.i(TAG, "  printed subtot " + order.adjustments().statedSubtotalCents());
        Log.i(TAG, "  printed total  " + order.adjustments().statedTotalCents());
        Log.i(TAG, "  tax            " + order.adjustments().taxCents());
        Log.i(TAG, "  subtotal match " + check.subtotalMatches()
                + " (off by " + check.subtotalDeltaCents() + ")");
        Log.i(TAG, "  total match    " + check.totalMatches()
                + " (off by " + check.totalDeltaCents() + ")");
        for (String warning : order.warnings()) {
            Log.i(TAG, "  warning        " + warning);
        }

        // Whether straightening this page helped at all. A scan is already square, and a
        // tilt measured on one is noise being acted upon: rotating a straight page spreads
        // its rows into each other and makes the bands worse, not better.
        if (slope != 0) {
            List<List<OcrElement>> asIs = new ArrayList<>();
            asIs.add(elements);
            ParsedOrder without = new ReceiptLayoutParser(
                    new NoDeskew(StoreKind.RESTAURANT.vocabulary())).parse(asIs);
            Log.i(TAG, "  without deskew " + without.items().size() + " items, add to "
                    + without.itemsSubtotalCents());
        }
    }

    /** The restaurant vocabulary with the straightening turned off, for the comparison. */
    private static final class NoDeskew
            implements com.householdsplitter.core.parse.layout.StoreVocabulary {

        private final com.householdsplitter.core.parse.layout.StoreVocabulary delegate;

        NoDeskew(com.householdsplitter.core.parse.layout.StoreVocabulary delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean needsDeskew() {
            return false;
        }

        @Override
        public com.householdsplitter.core.parse.layout.ParseTuning calibrate(
                List<OcrElement> page,
                com.householdsplitter.core.parse.layout.ParseTuning store) {
            return delegate.calibrate(page, store);
        }

        @Override
        public boolean nameMayPrecedePrice() {
            return delegate.nameMayPrecedePrice();
        }

        @Override
        public String presentName(String name) {
            return delegate.presentName(name);
        }

        @Override
        public String nameAfterQuantity(String line) {
            return delegate.nameAfterQuantity(line);
        }

        @Override
        public String storeName() {
            return delegate.storeName();
        }

        @Override
        public com.householdsplitter.core.parse.layout.ParseTuning tuning() {
            return delegate.tuning();
        }

        @Override
        public boolean isChrome(String text) {
            return delegate.isChrome(text);
        }

        @Override
        public boolean endsItemRegion(String text) {
            return delegate.endsItemRegion(text);
        }

        @Override
        public boolean isCarouselMarker(String text) {
            return delegate.isCarouselMarker(text);
        }

        @Override
        public String orderNumberIn(String text) {
            return delegate.orderNumberIn(text);
        }

        @Override
        public Long orderDateMillis(String text) {
            return delegate.orderDateMillis(text);
        }

        @Override
        public String defaultLabel(long epochMillis) {
            return delegate.defaultLabel(epochMillis);
        }

        @Override
        public com.householdsplitter.core.parse.model.OrderField summaryLabelOf(String left) {
            return delegate.summaryLabelOf(left);
        }

        @Override
        public String sectionNameOf(String text) {
            return delegate.sectionNameOf(text);
        }

        @Override
        public int deliveredUnitCount(String text) {
            return delegate.deliveredUnitCount(text);
        }

        @Override
        public boolean isExcludedSection(String sectionName) {
            return delegate.isExcludedSection(sectionName);
        }

        @Override
        public int quantityIn(String line) {
            return delegate.quantityIn(line);
        }

        @Override
        public boolean isRowMetadata(String line) {
            return delegate.isRowMetadata(line);
        }

        @Override
        public long savingsCentsIn(String line) {
            return delegate.savingsCentsIn(line);
        }
    }

    /** Every amount the recogniser found, so a missed row can be told from a missed price. */
    @Test
    public void dumpTheTextOfOneBill() throws Exception {
        String bill = System.getProperty("dumpBill", "bill-real-2.jpg");
        if (!present(bill)) {
            Log.i(TAG, "SKIP text dump (not fetched)");
            return;
        }
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MlKitTextSource source = new MlKitTextSource(context,
                    StoreKind.RESTAURANT.vocabulary().tuning().maxImageDimensionPx);
        try {
            List<OcrElement> elements = source.read(copyAsset(bill), 0);
            Log.i(TAG, "================ RAW " + bill);
            for (OcrElement element : elements) {
                Log.i(TAG, String.format("  y=%4d x=%4d..%4d  %s",
                        element.topPermille(),
                        (int) (((long) element.left() * 1000L) / element.imageWidth()),
                        (int) (((long) element.right() * 1000L) / element.imageWidth()),
                        element.text()));
            }
        } finally {
            source.close();
        }
    }
}
