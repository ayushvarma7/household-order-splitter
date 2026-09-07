package com.householdsplitter.ui.summary;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.householdsplitter.core.calc.UnassignedItemsException;
import com.householdsplitter.core.calc.result.SplitResult;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.OrderStatus;
import com.householdsplitter.data.relation.OrderBundle;
import com.householdsplitter.data.repo.OrderRepository;
import com.householdsplitter.prefs.SettingsStore;

import java.util.ArrayList;
import java.util.List;

/** S10 and S12. SPEC 7.10 and 7.12. */
public class SummaryViewModel extends ViewModel {

    private final OrderRepository repository;
    private final SettingsStore settings;
    private final long orderId;
    private final LiveData<OrderBundle> bundle;

    private final MutableLiveData<SplitResult> result = new MutableLiveData<>();
    private final MutableLiveData<List<LineItem>> blocking = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public SummaryViewModel(OrderRepository repository, SettingsStore settings, long orderId) {
        this.repository = repository;
        this.settings = settings;
        this.orderId = orderId;
        this.bundle = repository.observeBundle(orderId);
    }

    public long orderId() {
        return orderId;
    }

    public LiveData<OrderBundle> bundle() {
        return bundle;
    }

    public LiveData<SplitResult> result() {
        return result;
    }

    /** SPEC 7.10.6: non-empty means the summary must not compute. */
    public LiveData<List<LineItem>> blocking() {
        return blocking;
    }

    public LiveData<String> error() {
        return error;
    }

    /**
     * SPEC 7.10.6: the summary refuses to compute while anything is unanswered, and lists
     * the offenders instead. The calculator raises this itself, so the rule cannot be
     * bypassed by a caller who forgets to check.
     */
    public void recalculate() {
        repository.calculate(orderId, settings.allocationMode(), outcome -> {
            if (outcome.isOk()) {
                blocking.setValue(new ArrayList<>());
                result.setValue(outcome.value());
                return;
            }
            repository.unassignedItems(orderId, unassigned -> {
                if (unassigned != null && !unassigned.isEmpty()) {
                    blocking.setValue(unassigned);
                    result.setValue(null);
                } else {
                    error.setValue(outcome.error());
                }
            });
        });
    }

    /** SPEC 7.10.4. */
    public void setPayer(Long memberId) {
        repository.setPayer(orderId, memberId);
    }

    /** SPEC 7.10.7. */
    public void markSettled() {
        repository.setStatus(orderId, OrderStatus.SETTLED);
    }

    public void reopen() {
        repository.setStatus(orderId, OrderStatus.DRAFT);
    }

    /**
     * Promotes a draft to assigned once its totals compute.
     *
     * <p>Only ever from DRAFT. This runs on every recalculation, and the summary
     * recalculates whenever anything about the order changes, so promoting
     * unconditionally would quietly undo "Mark as settled" the moment the screen redrew:
     * the order would slide back to assigned and the workbook would record it as unsettled.
     */
    public void markAssignedIfStillDraft() {
        OrderBundle current = bundle.getValue();
        if (current != null && current.order.status == OrderStatus.DRAFT) {
            repository.setStatus(orderId, OrderStatus.ASSIGNED);
        }
    }

    /** Guards against UnassignedItemsException leaking as a raw message. */
    public static boolean isBlockingFailure(Throwable failure) {
        return failure instanceof UnassignedItemsException;
    }
}
