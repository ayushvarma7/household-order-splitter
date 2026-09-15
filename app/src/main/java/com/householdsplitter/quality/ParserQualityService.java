package com.householdsplitter.quality;

import com.householdsplitter.core.parse.StoreKind;
import com.householdsplitter.core.quality.ItemOrigin;
import com.householdsplitter.core.quality.ParserScorecard;
import com.householdsplitter.data.dao.DiscardedRowDao;
import com.householdsplitter.data.dao.LineItemDao;
import com.householdsplitter.data.dao.OrderDao;
import com.householdsplitter.data.entity.DiscardedRow;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.util.AppExecutors;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out how the reader has been doing, on demand.
 *
 * <p>Nothing is stored. Every figure here is computed from the rows that are already in the
 * database at the moment someone asks, which is the whole reason there is no metrics table:
 * a percentage written down at import time stops being true the first time anyone edits an
 * item, and then the app is showing a number that disagrees with its own data. The only
 * thing that has to be recorded is the one fact the live rows cannot express, which is that
 * a row was deleted, and that is what {@code discarded_rows} is for.
 *
 * <p>Reading a few hundred rows to add them up is not worth optimising for a household.
 */
public class ParserQualityService {

    /** Everything the report shows. */
    public static final class Report {

        public final ParserScorecard overall;
        public final Map<StoreKind, ParserScorecard> byStore;
        /** Rows the user typed: candidates for charges the reader missed. */
        public final List<LineItem> addedByHand;
        /** Rows the user deleted: charges the reader invented. */
        public final List<DiscardedRow> discarded;
        /** Rows the reader found but got wrong. */
        public final List<LineItem> corrected;

        Report(ParserScorecard overall, Map<StoreKind, ParserScorecard> byStore,
               List<LineItem> addedByHand, List<DiscardedRow> discarded,
               List<LineItem> corrected) {
            this.overall = overall;
            this.byStore = byStore;
            this.addedByHand = addedByHand;
            this.discarded = discarded;
            this.corrected = corrected;
        }
    }

    public interface Callback {
        void onReport(Report report);
    }

    private final OrderDao orderDao;
    private final LineItemDao lineItemDao;
    private final DiscardedRowDao discardedRowDao;
    private final AppExecutors executors;

    public ParserQualityService(OrderDao orderDao, LineItemDao lineItemDao,
                                DiscardedRowDao discardedRowDao, AppExecutors executors) {
        this.orderDao = orderDao;
        this.lineItemDao = lineItemDao;
        this.discardedRowDao = discardedRowDao;
        this.executors = executors;
    }

    public void report(long householdId, Callback callback) {
        executors.diskIO().execute(() -> {
            Report report = buildReport(householdId);
            executors.mainThread().execute(() -> callback.onReport(report));
        });
    }

    /** Separated from the threading so it can be exercised directly in an instrumented test. */
    public Report buildReport(long householdId) {
        Map<Long, StoreKind> storeOfOrder = new HashMap<>();
        for (Order order : orderDao.getAllSync(householdId)) {
            storeOfOrder.put(order.id, order.store);
        }

        Map<StoreKind, ParserScorecard.Builder> builders = new EnumMap<>(StoreKind.class);
        List<LineItem> addedByHand = new ArrayList<>();
        List<LineItem> corrected = new ArrayList<>();

        for (LineItem item : lineItemDao.allForHouseholdSync(householdId)) {
            StoreKind store = storeOfOrder.get(item.orderId);
            if (store == null) {
                continue;
            }
            ParserScorecard.Builder builder = builderFor(builders, store);
            switch (item.origin) {
                case MANUAL:
                    builder.addedByHand(item.lineTotalCents);
                    addedByHand.add(item);
                    break;
                case PARSED:
                    if (item.matchesWhatWasRead()) {
                        builder.keptAsRead(item.lineTotalCents);
                    } else {
                        builder.corrected(item.lineTotalCents);
                        corrected.add(item);
                    }
                    break;
                case SPLIT:
                default:
                    // Judges nothing: the reader read the parent row correctly.
                    break;
            }
        }

        List<DiscardedRow> discarded = discardedRowDao.allForHouseholdSync(householdId);
        for (DiscardedRow row : discarded) {
            builderFor(builders, row.store).removed(row.lineTotalCents);
        }

        Map<StoreKind, ParserScorecard> byStore = new EnumMap<>(StoreKind.class);
        ParserScorecard overall = ParserScorecard.empty();
        for (Map.Entry<StoreKind, ParserScorecard.Builder> entry : builders.entrySet()) {
            ParserScorecard card = entry.getValue().build();
            byStore.put(entry.getKey(), card);
            overall = overall.plus(card);
        }
        return new Report(overall, byStore, addedByHand, discarded, corrected);
    }

    private static ParserScorecard.Builder builderFor(
            Map<StoreKind, ParserScorecard.Builder> builders, StoreKind store) {
        ParserScorecard.Builder builder = builders.get(store);
        if (builder == null) {
            builder = ParserScorecard.builder();
            builders.put(store, builder);
        }
        return builder;
    }
}
