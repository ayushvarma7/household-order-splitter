package com.householdsplitter.ui.analytics;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.householdsplitter.core.analytics.SpendingAnalytics;
import com.householdsplitter.export.WorkbookService;

/** S15, spending analytics. */
public class AnalyticsViewModel extends ViewModel {

    private final WorkbookService workbookService;
    private final long householdId;
    private final MutableLiveData<SpendingAnalytics.Report> report = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);

    public AnalyticsViewModel(WorkbookService workbookService, long householdId) {
        this.workbookService = workbookService;
        this.householdId = householdId;
    }

    public LiveData<SpendingAnalytics.Report> report() {
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
    }
}
