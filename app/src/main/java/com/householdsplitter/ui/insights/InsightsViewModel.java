package com.householdsplitter.ui.insights;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.householdsplitter.core.insight.InsightTree;
import com.householdsplitter.core.insight.Trivia;
import com.householdsplitter.insight.InsightSample;
import com.householdsplitter.insight.InsightService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The look-inside screen: loads the orders once, then rebuilds the picture when the month
 * changes, which is cheap enough to do on the spot.
 *
 * <p>Where you had zoomed to and which facts you had already popped are kept here, not in the
 * view, so turning the phone does not put you back at the top with every bubble full again.
 */
public class InsightsViewModel extends ViewModel {

    /** About four months: enough to show a rhythm, few enough squares to read on a phone. */
    private static final int CALENDAR_WEEKS = 17;
    /** The items offered in the price tracker, most often bought first. */
    private static final int PRICE_ITEMS = 8;

    private final InsightService service;
    private final long householdId;
    private final MutableLiveData<InsightData> data = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final Set<String> popped = new HashSet<>();

    private InsightService.Loaded loaded;
    private boolean example;
    private YearMonth month;
    private String focusPath = "";

    public InsightsViewModel(InsightService service, long householdId) {
        this.service = service;
        this.householdId = householdId;
    }

    public LiveData<InsightData> data() {
        return data;
    }

    public LiveData<Boolean> loading() {
        return loading;
    }

    /**
     * Shows made-up orders so the screen can be tried before there is anything of one's own.
     * Nothing is written anywhere, and {@link #exitExample()} goes back to the real orders.
     */
    public void startExample() {
        example = true;
        loaded = InsightSample.create();
        month = InsightTree.latestMonth(loaded.purchases, ZoneId.systemDefault());
        focusPath = "";
        popped.clear();
        publish();
    }

    public void exitExample() {
        example = false;
        month = null;
        focusPath = "";
        popped.clear();
        load();
    }

    public void load() {
        if (example) {
            return;
        }
        loading.setValue(true);
        service.load(householdId, result -> {
            loaded = result;
            if (month == null || !hasMonth(month)) {
                month = InsightTree.latestMonth(loaded.purchases, ZoneId.systemDefault());
            }
            publish();
            loading.setValue(false);
        });
    }

    public void selectMonth(YearMonth chosen) {
        if (loaded == null || chosen.equals(month)) {
            return;
        }
        month = chosen;
        focusPath = "";
        publish();
    }

    public String focusPath() {
        return focusPath;
    }

    public void setFocusPath(String path) {
        focusPath = path == null ? "" : path;
    }

    public boolean isPopped(String insightId) {
        return popped.contains(insightId);
    }

    public void markPopped(String insightId) {
        popped.add(insightId);
    }

    public void bringAllBack() {
        popped.clear();
    }

    private boolean hasMonth(YearMonth wanted) {
        return months().contains(wanted);
    }

    private List<YearMonth> months() {
        ZoneId zone = ZoneId.systemDefault();
        Set<YearMonth> seen = new HashSet<>();
        for (com.householdsplitter.core.insight.Purchase purchase : loaded.purchases) {
            seen.add(InsightTree.monthOf(purchase.dateMillis(), zone));
        }
        List<YearMonth> sorted = new ArrayList<>(seen);
        sorted.sort(Collections.reverseOrder());
        return sorted;
    }

    private void publish() {
        ZoneId zone = ZoneId.systemDefault();
        if (month == null) {
            data.setValue(new InsightData(null, YearMonth.now(zone), new ArrayList<>(),
                    loaded.members, new ArrayList<>(), new ArrayList<>(), example));
            return;
        }
        InsightTree tree = InsightTree.build(loaded.purchases, zone, month);
        List<InsightService.MissingOrder> missing = new ArrayList<>();
        for (InsightService.MissingOrder order : loaded.missingOrders) {
            if (InsightTree.monthOf(order.dateMillis, zone).equals(month)) {
                missing.add(order);
            }
        }
        missing.sort((a, b) -> Long.compare(b.cents, a.cents));
        List<Trivia.Fact> trivia = Trivia.facts(loaded.purchases, zone,
                LocalDate.now(zone).toEpochDay());
        data.setValue(new InsightData(tree, month, months(), loaded.members, missing, trivia,
                example,
                com.householdsplitter.core.insight.Patterns.sharingWeb(loaded.purchases),
                com.householdsplitter.core.insight.Patterns.calendar(loaded.purchases, zone,
                        CALENDAR_WEEKS),
                com.householdsplitter.core.insight.Patterns.priceTracker(loaded.purchases, 2,
                        PRICE_ITEMS)));
    }
}
