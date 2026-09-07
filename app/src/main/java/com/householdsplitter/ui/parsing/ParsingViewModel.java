package com.householdsplitter.ui.parsing;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.householdsplitter.core.parse.model.ParsedOrder;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.repo.HouseholdRepository;
import com.householdsplitter.data.repo.OrderRepository;
import com.householdsplitter.parse.ReceiptParser;
import com.householdsplitter.util.AppExecutors;
import com.householdsplitter.util.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** S5. SPEC 7.5. */
public class ParsingViewModel extends ViewModel {

    /** How far through the images we are, for the determinate bar of SPEC 7.5.1. */
    public static final class Progress {

        public final int current;
        public final int total;

        Progress(int current, int total) {
            this.current = current;
            this.total = total;
        }
    }

    private final OrderRepository orderRepository;
    private final ReceiptParser parser;
    private final AppExecutors executors;
    private final long householdId;

    private final MutableLiveData<Progress> progress = new MutableLiveData<>();
    private final MutableLiveData<Long> orderCreated = new MutableLiveData<>();
    private final MutableLiveData<String> failure = new MutableLiveData<>();
    private final MutableLiveData<Order> duplicateWarning = new MutableLiveData<>();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private final long existingOrderId;
    private boolean started;
    private ParsedOrder pending;
    private List<String> pendingUris;

    /**
     * @param existingOrderId the order to append to, or 0 to create a new one. Appending is
     *                        what makes "you may have missed a screenshot" actionable
     *                        (SPEC 8.1.3).
     */
    public ParsingViewModel(OrderRepository orderRepository, HouseholdRepository householdRepository,
                            ReceiptParser parser, AppExecutors executors, long householdId,
                            long existingOrderId) {
        this.orderRepository = orderRepository;
        this.parser = parser;
        this.executors = executors;
        this.householdId = householdId;
        this.existingOrderId = existingOrderId;
    }

    public LiveData<Progress> progress() {
        return progress;
    }

    public LiveData<Long> orderCreated() {
        return orderCreated;
    }

    public LiveData<String> failure() {
        return failure;
    }

    /** SPEC 8.6.7. */
    public LiveData<Order> duplicateWarning() {
        return duplicateWarning;
    }

    /** SPEC 7.5.2: the parse runs on the parsing executor, never the main thread. */
    public void start(List<String> uriStrings) {
        if (started) {
            return;
        }
        started = true;
        cancelled.set(false);
        progress.setValue(new Progress(0, uriStrings.size()));

        executors.parsing().execute(() -> {
            List<Uri> uris = new ArrayList<>();
            for (String value : uriStrings) {
                uris.add(Uri.parse(value));
            }
            try {
                ParsedOrder parsed = parser.parse(uris, new ReceiptParser.ProgressListener() {
                    @Override
                    public void onProgress(int imageIndex, int imageCount) {
                        progress.postValue(new Progress(imageIndex, imageCount));
                    }

                    @Override
                    public boolean isCancelled() {
                        return cancelled.get();
                    }
                });
                if (cancelled.get()) {
                    return;
                }
                pending = parsed;
                pendingUris = uriStrings;
                checkDuplicateThenSave(parsed, uriStrings);
            } catch (ReceiptParser.ParseException failed) {
                if (!cancelled.get()) {
                    failure.postValue(failed.getMessage());
                }
            }
        });
    }

    private void checkDuplicateThenSave(ParsedOrder parsed, List<String> uris) {
        // Appending to a known order is not a duplicate import: the user asked for these
        // screenshots to join that order, so the same order number is expected.
        if (existingOrderId != 0L) {
            save(parsed, uris);
            return;
        }
        orderRepository.findExistingByOrderNo(parsed.externalOrderNo(), existing -> {
            if (existing != null) {
                duplicateWarning.setValue(existing);
            } else {
                save(parsed, uris);
            }
        });
    }

    /** The user chose to import anyway after the duplicate warning of SPEC 8.6.7. */
    public void saveAnyway() {
        if (pending != null) {
            save(pending, pendingUris);
        }
    }

    private void save(ParsedOrder parsed, List<String> uris) {
        if (existingOrderId != 0L) {
            orderRepository.appendParse(existingOrderId, parsed, uris,
                    (Result<Integer> result) -> {
                        if (result.isOk()) {
                            orderCreated.setValue(existingOrderId);
                        } else {
                            failure.setValue(result.error());
                        }
                    });
            return;
        }
        orderRepository.createFromParse(householdId, parsed, uris, (Result<Long> result) -> {
            if (result.isOk()) {
                orderCreated.setValue(result.value());
            } else {
                failure.setValue(result.error());
            }
        });
    }

    /** SPEC 7.5.4: an empty order that goes straight to the review screen. */
    public void enterManually() {
        orderRepository.createEmpty(householdId, (Result<Long> result) -> {
            if (result.isOk()) {
                orderCreated.setValue(result.value());
            } else {
                failure.setValue(result.error());
            }
        });
    }

    /** SPEC 7.5.2: Cancel aborts cleanly and creates no order. */
    public void cancel() {
        cancelled.set(true);
    }

    public void retry(List<String> uris) {
        started = false;
        failure.setValue(null);
        start(uris);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelled.set(true);
    }
}
