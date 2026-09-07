package com.householdsplitter.export;

import android.content.ContentResolver;
import android.net.Uri;

import com.householdsplitter.core.calc.AllocationMode;
import com.householdsplitter.core.calc.SplitCalculator;
import com.householdsplitter.core.calc.result.SplitResult;
import com.householdsplitter.core.export.CsvExporter;
import com.householdsplitter.core.export.ExportOrder;
import com.householdsplitter.core.export.ShareTextBuilder;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.data.mapper.CalcMapper;
import com.householdsplitter.data.relation.OrderBundle;
import com.householdsplitter.data.repo.OrderRepository;
import com.householdsplitter.prefs.SettingsStore;
import com.householdsplitter.util.AppExecutors;
import com.householdsplitter.util.Callback;
import com.householdsplitter.util.Result;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** SPEC 7.11 and 10. The formatting itself is pure Java in :core. */
public class ExportService {

    private final OrderRepository repository;
    private final SettingsStore settings;
    private final AppExecutors executors;
    private final ContentResolver contentResolver;

    public ExportService(OrderRepository repository, SettingsStore settings,
                         AppExecutors executors, ContentResolver contentResolver) {
        this.repository = repository;
        this.settings = settings;
        this.executors = executors;
        this.contentResolver = contentResolver;
    }

    /** SPEC 10.2, built from a bundle the caller already has. */
    public String buildShareText(OrderBundle bundle, String groupName, SplitResult result) {
        ExportOrder order = toExportOrder(bundle, groupName, result);
        return ShareTextBuilder.build(order,
                new CurrencyFormat(settings.currencySymbol(), settings.locale()));
    }

    /** SPEC 10.1. */
    public String buildCsv(OrderBundle bundle, String groupName, SplitResult result) {
        return CsvExporter.export(toExportOrder(bundle, groupName, result));
    }

    /** SPEC 7.11.3: every order in one file. */
    public void buildCsvForAll(long householdId, String groupName,
                               Callback<Result<String>> callback) {
        executors.diskIO().execute(() -> repository.allBundles(householdId, bundles -> {
            executors.diskIO().execute(() -> {
                List<ExportOrder> orders = new ArrayList<>();
                AllocationMode mode = settings.allocationMode();
                for (OrderBundle bundle : bundles) {
                    try {
                        SplitResult result = SplitCalculator.calculate(
                                CalcMapper.toCalcOrder(bundle, mode));
                        orders.add(toExportOrder(bundle, groupName, result));
                    } catch (RuntimeException skipUncomputable) {
                        // An order still being assigned cannot be totalled. It is left out
                        // rather than exported with invented numbers.
                    }
                }
                String csv = CsvExporter.exportAll(orders);
                executors.mainThread().execute(() -> callback.onResult(Result.ok(csv)));
            });
        }));
    }

    /** SPEC 7.11.2: written through ACTION_CREATE_DOCUMENT. */
    public void writeToUri(Uri target, String content, Callback<Result<Void>> callback) {
        executors.diskIO().execute(() -> {
            try (OutputStream stream = contentResolver.openOutputStream(target)) {
                if (stream == null) {
                    throw new IOException("Could not open that file for writing");
                }
                stream.write(content.getBytes(StandardCharsets.UTF_8));
                executors.mainThread().execute(() -> callback.onResult(Result.ok(null)));
            } catch (IOException failed) {
                executors.mainThread().execute(() ->
                        callback.onResult(Result.failure(failed.getMessage())));
            }
        });
    }

    private ExportOrder toExportOrder(OrderBundle bundle, String groupName, SplitResult result) {
        return new ExportOrder(
                groupName,
                bundle.order.label,
                CalcMapper.commonItems(bundle),
                result,
                CalcMapper.memberName(bundle, bundle.order.payerMemberId));
    }
}
