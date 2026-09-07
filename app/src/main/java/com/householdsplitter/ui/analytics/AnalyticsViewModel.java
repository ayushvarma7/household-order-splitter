package com.householdsplitter.ui.analytics;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.householdsplitter.data.entity.SettlementPayment;
import com.householdsplitter.data.repo.SettlementRepository;
import com.householdsplitter.export.WorkbookService;

import java.util.List;

/** S15, spending analytics. */
public class AnalyticsViewModel extends ViewModel {

    private final WorkbookService workbookService;
    private final SettlementRepository settlements;
    private final long householdId;
    private final MutableLiveData<WorkbookService.Insight> report = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);

    private final MutableLiveData<List<SettlementPayment>> payments = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();

    public AnalyticsViewModel(WorkbookService workbookService, SettlementRepository settlements,
                              long householdId) {
        this.workbookService = workbookService;
        this.settlements = settlements;
        this.householdId = householdId;
    }

    public LiveData<List<SettlementPayment>> payments() {
        return payments;
    }

    public LiveData<String> message() {
        return message;
    }

    /**
     * Records that somebody handed the money over, then recomputes. Recomputing is the
     * point: the suggested payment should disappear from the list it came from.
     */
    public void recordPayment(long fromMemberId, long toMemberId, long amountCents,
                              String recordedMessage) {
        settlements.record(householdId, fromMemberId, toMemberId, amountCents, result -> {
            message.setValue(result.isOk() ? recordedMessage : result.error());
            load();
        });
    }

    /** Undoes a payment recorded by mistake. */
    public void deletePayment(long paymentId, String removedMessage) {
        settlements.delete(paymentId, result -> {
            message.setValue(result.isOk() ? removedMessage : result.error());
            load();
        });
    }

    public void messageShown() {
        message.setValue(null);
    }

    public LiveData<WorkbookService.Insight> report() {
        return report;
    }

    public LiveData<Boolean> loading() {
        return loading;
    }

    public void load() {
        loading.setValue(true);
        workbookService.analyse(householdId, value -> {
            report.setValue(value);
            loading.setValue(false);
        });
        settlements.paymentsSync(householdId, payments::setValue);
    }
}
