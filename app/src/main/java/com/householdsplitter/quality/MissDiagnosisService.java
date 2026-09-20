package com.householdsplitter.quality;

import android.content.Context;
import android.net.Uri;

import com.householdsplitter.core.parse.StoreKind;
import com.householdsplitter.core.parse.layout.ParseTrace;
import com.householdsplitter.core.parse.layout.ReceiptLayoutParser;
import com.householdsplitter.core.parse.model.OcrElement;
import com.householdsplitter.core.quality.MissDiagnosis;
import com.householdsplitter.data.dao.LineItemDao;
import com.householdsplitter.data.dao.OrderDao;
import com.householdsplitter.data.dao.OrderImageDao;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderImage;
import com.householdsplitter.ocr.MlKitTextSource;
import com.householdsplitter.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

/**
 * Asks the screenshots why a hand-typed row was not found on them.
 *
 * <p>Runs the moment the row is added, not later. The screenshots are referenced by URI
 * rather than copied into the app, so a content URI stops resolving as soon as the user
 * deletes the picture from their gallery. The one moment it is certainly readable is while
 * the user is still looking at the order they just imported.
 *
 * <p>Runs the real reader over the real images with a {@link ParseTrace} attached, rather
 * than a separate search of its own. A second implementation that agreed with the parser
 * today would drift from it tomorrow, and a diagnosis that explains a parser other than
 * the one that ran is worse than no diagnosis, because it is confidently wrong about where
 * to look.
 *
 * <p>Silent on failure by design. This is a diagnostic: if the images have gone or OCR
 * cannot run, the honest outcome is no verdict, and no verdict must never interrupt
 * somebody who is in the middle of splitting a bill.
 */
public class MissDiagnosisService {

    private final Context context;
    private final OrderDao orderDao;
    private final OrderImageDao imageDao;
    private final LineItemDao lineItemDao;
    private final AppExecutors executors;

    public MissDiagnosisService(Context context, OrderDao orderDao, OrderImageDao imageDao,
                                LineItemDao lineItemDao, AppExecutors executors) {
        this.context = context.getApplicationContext();
        this.orderDao = orderDao;
        this.imageDao = imageDao;
        this.lineItemDao = lineItemDao;
        this.executors = executors;
    }

    /** Diagnoses one hand-typed row and stores the verdict on it. */
    public void diagnose(long lineItemId) {
        executors.parsing().execute(() -> {
            try {
                runDiagnosis(lineItemId);
            } catch (Exception diagnosticsAreNeverFatal) {
                // Deliberately swallowed. Nothing the user is doing depends on this.
            }
        });
    }

    private void runDiagnosis(long lineItemId) {
        LineItem item = lineItemDao.getByIdSync(lineItemId);
        if (item == null || item.name == null || item.name.trim().isEmpty()) {
            return;
        }
        Order order = orderDao.getByIdSync(item.orderId);
        if (order == null) {
            return;
        }
        List<OrderImage> images = imageDao.getForOrderSync(item.orderId);
        if (images == null || images.isEmpty()) {
            // Nothing was imported, so nothing was missed: this row was always going to be
            // typed. Recorded as such rather than left blank, so the report can tell the
            // difference between "not a parser problem" and "not looked at yet".
            store(item, MissDiagnosis.Verdict.NOT_ON_ANY_PAGE);
            return;
        }

        StoreKind store = order.store == null ? StoreKind.WALMART : order.store;
        MlKitTextSource source = new MlKitTextSource(context,
                store.vocabulary().tuning().maxImageDimensionPx);
        try {
            List<List<OcrElement>> pages = new ArrayList<>();
            for (OrderImage image : images) {
                pages.add(source.read(Uri.parse(image.uri), pages.size()));
            }
            if (pages.isEmpty()) {
                return;
            }
            ParseTrace trace = new ParseTrace();
            new ReceiptLayoutParser(store.vocabulary()).parse(pages, trace);
            store(item, verdictFor(trace, item));
        } catch (Exception unreadable) {
            // A screenshot the user has since deleted, most likely. No verdict is the
            // truthful outcome, and a guessed one would be worse than none.
        } finally {
            source.close();
        }
    }

    /**
     * The verdict, by name first and then by amount.
     *
     * <p>The name is asked first because it is what the user typed and so is what they are
     * asking about. When it finds nothing, the row's own amount is asked instead, and that
     * question is a much better one: the user has just typed in a row worth exactly this
     * much, so a band on the page carrying exactly that figure is almost certainly the row
     * the reader lost. A name match is a similarity score with a threshold; an amount match
     * is arithmetic.
     *
     * <p>Second rather than first, because an amount can coincide where a name cannot. Two
     * dishes at $12.00 are ordinary, two dishes called the same thing are not, so when the
     * name does find something it is the better evidence and it wins.
     *
     * <p>This matters most for a photographed bill, where a name is abbreviated to fit a
     * till roll and typed out in full by the user, so the name match has the least to work
     * with exactly where the amount match has the most.
     */
    private static MissDiagnosis.Verdict verdictFor(ParseTrace trace, LineItem item) {
        MissDiagnosis.Verdict byName = MissDiagnosis.diagnose(trace, item.name).verdict;
        if (byName != MissDiagnosis.Verdict.NOT_ON_ANY_PAGE) {
            return byName;
        }
        return MissDiagnosis.byAmount(trace, item.lineTotalCents).verdict;
    }

    private void store(LineItem item, MissDiagnosis.Verdict verdict) {
        item.missVerdict = verdict.name();
        lineItemDao.update(item);
    }
}
