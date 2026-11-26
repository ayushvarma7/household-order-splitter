package com.householdsplitter.insight;

import com.householdsplitter.core.calc.AllocationMode;
import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.calc.input.CalcOrder;
import com.householdsplitter.core.insight.Purchase;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.mapper.CalcMapper;
import com.householdsplitter.data.mapper.OrderTotals;
import com.householdsplitter.data.relation.LineItemWithAssignments;
import com.householdsplitter.data.relation.OrderBundle;
import com.householdsplitter.data.repo.OrderRepository;
import com.householdsplitter.util.AppExecutors;
import com.householdsplitter.util.Callback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the orders in the database into what the "look inside" screen is drawn from: one
 * {@link Purchase} per row, plus a row for the money each order's bill charged beyond what
 * its rows add up to.
 *
 * <p>It reads exactly what the other screens read, the stored orders and their assignments,
 * and adds nothing of its own to the database. An order that is not finished (a row nobody
 * has answered for, or nobody on it) contributes nothing, the same rule the Spending screen
 * uses, so the two never describe different sets of orders.
 */
public class InsightService {

    /** One order whose rows add up to less than its bill. */
    public static final class MissingOrder {

        public final long orderId;
        public final String label;
        public final long dateMillis;
        public final long cents;

        MissingOrder(long orderId, String label, long dateMillis, long cents) {
            this.orderId = orderId;
            this.label = label;
            this.dateMillis = dateMillis;
            this.cents = cents;
        }
    }

    /** Everything read from the database, before a month is chosen. */
    public static final class Loaded {

        public final List<Purchase> purchases = new ArrayList<>();
        public final Map<Long, Member> members = new LinkedHashMap<>();
        public final List<MissingOrder> missingOrders = new ArrayList<>();
    }

    private final OrderRepository repository;
    private final AppExecutors executors;

    public InsightService(OrderRepository repository, AppExecutors executors) {
        this.repository = repository;
        this.executors = executors;
    }

    public void load(long householdId, Callback<Loaded> callback) {
        repository.allBundles(householdId, bundles -> executors.diskIO().execute(() -> {
            Loaded loaded = read(bundles);
            executors.mainThread().execute(() -> callback.onResult(loaded));
        }));
    }

    static Loaded read(List<OrderBundle> bundles) {
        Loaded loaded = new Loaded();
        for (OrderBundle bundle : bundles) {
            if (!isFinished(bundle)) {
                continue;
            }
            CalcOrder order = CalcMapper.toCalcOrder(bundle, AllocationMode.PROPORTIONAL);
            long date = bundle.order.orderDate;
            loaded.purchases.addAll(Purchase.fromOrder(date, order));
            long missing = OrderTotals.missingCents(bundle);
            if (missing > 0L) {
                loaded.purchases.addAll(Purchase.missing(date, missing, order));
                loaded.missingOrders.add(new MissingOrder(
                        bundle.order.id, bundle.order.label, date, missing));
            }
            for (Member member : bundle.participants) {
                loaded.members.put(member.id, member);
            }
        }
        return loaded;
    }

    /** Somebody is on it and no row is still waiting for an answer. */
    private static boolean isFinished(OrderBundle bundle) {
        if (bundle.participants.isEmpty()) {
            return false;
        }
        for (LineItemWithAssignments row : bundle.items) {
            if (row.item.scope == Scope.UNASSIGNED) {
                return false;
            }
        }
        return true;
    }
}
