package com.householdsplitter.export;

import android.content.ContentResolver;
import android.net.Uri;

import com.householdsplitter.core.analytics.SpendingAnalytics;
import com.householdsplitter.core.calc.AllocationMode;
import com.householdsplitter.core.calc.SplitCalculator;
import com.householdsplitter.core.calc.result.SplitResult;
import com.householdsplitter.core.export.ExportOrder;
import com.householdsplitter.core.export.OrderWorkbook;
import com.householdsplitter.core.export.xlsx.Sheet;
import com.householdsplitter.core.export.xlsx.XlsxWriter;
import com.householdsplitter.data.mapper.CalcMapper;
import com.householdsplitter.data.relation.OrderBundle;
import com.householdsplitter.data.repo.OrderRepository;
import com.householdsplitter.prefs.SettingsStore;
import com.householdsplitter.util.AppExecutors;
import com.householdsplitter.util.Callback;
import com.householdsplitter.util.Result;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps one Excel workbook up to date, with a sheet per order.
 *
 * <p>The user picks a destination file once. From then on the app rewrites that file
 * whenever an order changes, so a newly settled order simply appears as a new sheet with no
 * export step to remember. Rewriting the whole workbook rather than editing it in place is
 * deliberate: it needs no spreadsheet library to read the existing file, and it means the
 * file is always a faithful picture of the database rather than an append-only log that can
 * drift out of step when an order is edited or deleted.
 *
 * <p>An order that cannot be totalled yet, because something is still unassigned, is left
 * out rather than written with invented numbers.
 */
public class WorkbookService {

    /** What a write produced, so the UI can say something specific. */
    public static final class Written {

        public final int sheetCount;
        public final int orderCount;

        Written(int sheetCount, int orderCount) {
            this.sheetCount = sheetCount;
            this.orderCount = orderCount;
        }
    }

    private final OrderRepository repository;
    private final SettingsStore settings;
    private final AppExecutors executors;
    private final ContentResolver contentResolver;

    public WorkbookService(OrderRepository repository, SettingsStore settings,
                           AppExecutors executors, ContentResolver contentResolver) {
        this.repository = repository;
        this.settings = settings;
        this.executors = executors;
        this.contentResolver = contentResolver;
    }

    public boolean hasDestination() {
        return settings.workbookUri() != null;
    }

    /**
     * Rewrites the workbook if the user has chosen a destination and left auto-update on.
     * Silent: this runs after settling an order, and a household that never set a
     * destination should not be nagged about it.
     */
    public void refreshQuietly(long householdId, String groupName) {
        if (!settings.workbookAutoUpdate() || settings.workbookUri() == null) {
            return;
        }
        write(householdId, groupName, Uri.parse(settings.workbookUri()), result -> {
        });
    }

    /** Writes to the saved destination, reporting the outcome. */
    public void refresh(long householdId, String groupName, Callback<Result<Written>> callback) {
        String uri = settings.workbookUri();
        if (uri == null) {
            callback.onResult(Result.failure("Choose where to keep the workbook first"));
            return;
        }
        write(householdId, groupName, Uri.parse(uri), callback);
    }

    /** Writes to a newly chosen destination and remembers it. */
    public void writeTo(long householdId, String groupName, Uri target,
                        Callback<Result<Written>> callback) {
        settings.workbookUri(target.toString());
        write(householdId, groupName, target, callback);
    }

    private void write(long householdId, String groupName, Uri target,
                       Callback<Result<Written>> callback) {
        repository.allBundles(householdId, bundles -> executors.diskIO().execute(() -> {
            try {
                List<OrderWorkbook.Entry> entries = toEntries(bundles, groupName);
                if (entries.isEmpty()) {
                    post(callback, Result.failure("No finished orders to write yet"));
                    return;
                }
                List<Sheet> sheets = OrderWorkbook.build(groupName, entries);
                try (OutputStream stream = contentResolver.openOutputStream(target, "wt")) {
                    if (stream == null) {
                        throw new IOException("Could not open the workbook for writing");
                    }
                    XlsxWriter.write(sheets, stream);
                }
                post(callback, Result.ok(new Written(sheets.size(), entries.size())));
            } catch (Exception failed) {
                post(callback, Result.failure(String.valueOf(failed.getMessage())));
            }
        }));
    }

    /** The same orders the workbook covers, ready for the analytics screen. */
    public void analyse(long householdId, Callback<SpendingAnalytics.Report> callback) {
        repository.allBundles(householdId, bundles -> executors.diskIO().execute(() -> {
            List<SpendingAnalytics.OrderPoint> points = new ArrayList<>();
            AllocationMode mode = settings.allocationMode();
            for (OrderBundle bundle : bundles) {
                SplitResult result = tryCalculate(bundle, mode);
                if (result != null) {
                    points.add(new SpendingAnalytics.OrderPoint(
                            bundle.order.orderDate, bundle.order.label, result));
                }
            }
            SpendingAnalytics.Report report = SpendingAnalytics.analyse(points);
            executors.mainThread().execute(() -> callback.onResult(report));
        }));
    }

    private List<OrderWorkbook.Entry> toEntries(List<OrderBundle> bundles, String groupName) {
        List<OrderWorkbook.Entry> entries = new ArrayList<>();
        AllocationMode mode = settings.allocationMode();
        for (OrderBundle bundle : bundles) {
            SplitResult result = tryCalculate(bundle, mode);
            if (result == null) {
                continue;
            }
            ExportOrder order = new ExportOrder(groupName, bundle.order.label,
                    CalcMapper.commonItems(bundle), result,
                    CalcMapper.memberName(bundle, bundle.order.payerMemberId));
            entries.add(new OrderWorkbook.Entry(order, bundle.order.orderDate,
                    friendlyStatus(bundle)));
        }
        return entries;
    }

    private static SplitResult tryCalculate(OrderBundle bundle, AllocationMode mode) {
        try {
            return SplitCalculator.calculate(CalcMapper.toCalcOrder(bundle, mode));
        } catch (RuntimeException stillBeingAssigned) {
            // Nothing to write yet. Better an absent sheet than an invented one.
            return null;
        }
    }

    private static String friendlyStatus(OrderBundle bundle) {
        switch (bundle.order.status) {
            case SETTLED:
                return "Settled";
            case ASSIGNED:
                return "Assigned";
            default:
                return "Draft";
        }
    }

    private <T> void post(Callback<T> callback, T value) {
        executors.mainThread().execute(() -> callback.onResult(value));
    }
}
