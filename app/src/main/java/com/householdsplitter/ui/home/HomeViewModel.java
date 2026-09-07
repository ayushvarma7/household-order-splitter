package com.householdsplitter.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.householdsplitter.core.browse.MonthBucket;
import com.householdsplitter.core.browse.SearchMatch;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.OrderStatus;
import com.householdsplitter.data.relation.OrderBundle;
import com.householdsplitter.data.relation.OrderItemNames;
import com.householdsplitter.data.relation.OrderWithMembers;
import com.householdsplitter.data.repo.HouseholdRepository;
import com.householdsplitter.data.repo.OrderRepository;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** S3. SPEC 7.3. */
public class HomeViewModel extends ViewModel {

    /** SPEC 7.3.2 lists every order; these narrow it without hiding anything permanently. */
    public enum Filter {
        ALL,
        /** Anything not yet settled, which is the only work outstanding. */
        OPEN,
        SETTLED
    }

    private final OrderRepository orderRepository;
    private final LiveData<Household> household;
    private final LiveData<List<Member>> members;
    private final LiveData<List<OrderWithMembers>> orders;
    private final LiveData<List<OrderItemNames>> itemNames;

    private final MutableLiveData<String> query = new MutableLiveData<>("");
    private final MutableLiveData<Filter> filter = new MutableLiveData<>(Filter.ALL);
    private final MediatorLiveData<List<HomeRow>> rows = new MediatorLiveData<>();

    public HomeViewModel(HouseholdRepository householdRepository,
                         OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
        this.household = householdRepository.observeHousehold();
        this.members = Transformations.switchMap(household, value -> {
            if (value == null) {
                return new MutableLiveData<>(Collections.<Member>emptyList());
            }
            return householdRepository.observeMembers(value.id);
        });
        // SPEC 7.3.2: newest first, ordered by the DAO.
        this.orders = Transformations.switchMap(household, value -> {
            if (value == null) {
                return new MutableLiveData<>(Collections.<OrderWithMembers>emptyList());
            }
            return orderRepository.observeOrders(value.id);
        });
        this.itemNames = Transformations.switchMap(household, value -> {
            if (value == null) {
                return new MutableLiveData<>(Collections.<OrderItemNames>emptyList());
            }
            return orderRepository.observeItemNames(value.id);
        });

        rows.setValue(new ArrayList<>());
        rows.addSource(orders, value -> rebuild());
        rows.addSource(itemNames, value -> rebuild());
        rows.addSource(query, value -> rebuild());
        rows.addSource(filter, value -> rebuild());
    }

    public LiveData<Household> household() {
        return household;
    }

    public LiveData<List<Member>> members() {
        return members;
    }

    public LiveData<List<OrderWithMembers>> orders() {
        return orders;
    }

    /** The list as it should be shown: filtered, searched, and under month headings. */
    public LiveData<List<HomeRow>> rows() {
        return rows;
    }

    public LiveData<String> query() {
        return query;
    }

    public LiveData<Filter> filter() {
        return filter;
    }

    public void search(String text) {
        String next = text == null ? "" : text;
        if (!next.equals(query.getValue())) {
            query.setValue(next);
        }
    }

    public void filterBy(Filter value) {
        if (value != filter.getValue()) {
            filter.setValue(value);
        }
    }

    /** True when the list is empty only because of a search, which needs different wording. */
    public boolean isFilteredEmpty() {
        List<OrderWithMembers> all = orders.getValue();
        List<HomeRow> shown = rows.getValue();
        return all != null && !all.isEmpty() && (shown == null || shown.isEmpty());
    }

    public boolean hasSearchOrFilter() {
        String text = query.getValue();
        return (text != null && !text.trim().isEmpty()) || filter.getValue() != Filter.ALL;
    }

    /**
     * Filters, searches, and inserts a heading whenever the month changes.
     *
     * <p>The orders arrive newest first from the DAO, so a single pass is enough and the
     * headings come out in the right order without sorting anything again. A heading is
     * added when the month changes and rewritten once its month is complete, because the
     * count and total for a month are not known until the last of its orders is seen.
     */
    private void rebuild() {
        List<OrderWithMembers> all = orders.getValue();
        if (all == null) {
            rows.setValue(new ArrayList<>());
            return;
        }
        Map<Long, String> names = new HashMap<>();
        List<OrderItemNames> loaded = itemNames.getValue();
        if (loaded != null) {
            for (OrderItemNames entry : loaded) {
                names.put(entry.orderId, entry.names);
            }
        }
        String text = query.getValue();
        Filter active = filter.getValue() == null ? Filter.ALL : filter.getValue();
        ZoneId zone = ZoneId.systemDefault();
        long now = System.currentTimeMillis();

        List<HomeRow> next = new ArrayList<>();
        int openKey = Integer.MIN_VALUE;
        int headerAt = -1;
        int monthCount = 0;
        long monthTotal = 0L;

        for (OrderWithMembers order : all) {
            if (!passesFilter(order, active) || !passesSearch(order, names, text)) {
                continue;
            }
            int key = MonthBucket.keyOf(order.order.orderDate, zone);
            if (key != openKey) {
                closeMonth(next, headerAt, monthCount, monthTotal);
                openKey = key;
                headerAt = next.size();
                monthCount = 0;
                monthTotal = 0L;
                next.add(HomeRow.header(headingFor(order.order.orderDate, now, zone),
                        MonthBucket.of(order.order.orderDate, zone), 0, 0L));
            }
            monthCount++;
            monthTotal += order.order.statedTotalCents;
            next.add(HomeRow.of(order));
        }
        closeMonth(next, headerAt, monthCount, monthTotal);
        rows.setValue(next);
    }

    private void closeMonth(List<HomeRow> rowsSoFar, int headerAt, int count, long totalCents) {
        if (headerAt < 0) {
            return;
        }
        HomeRow header = rowsSoFar.get(headerAt);
        rowsSoFar.set(headerAt,
                HomeRow.header(header.heading, header.month, count, totalCents));
    }

    private boolean passesFilter(OrderWithMembers order, Filter active) {
        switch (active) {
            case OPEN:
                return order.order.status != OrderStatus.SETTLED;
            case SETTLED:
                return order.order.status == OrderStatus.SETTLED;
            default:
                return true;
        }
    }

    /**
     * The label, the participants' names, and the item names are all searchable. The item
     * names matter most: "which order had the coffee beans?" is the question a household
     * actually asks months later, and the label never answers it.
     */
    private boolean passesSearch(OrderWithMembers order, Map<Long, String> names, String text) {
        if (text == null || text.trim().isEmpty()) {
            return true;
        }
        StringBuilder people = new StringBuilder();
        for (Member member : order.participants) {
            people.append(member.name).append(' ');
        }
        return SearchMatch.matches(text, order.order.label, people.toString(),
                names.get(order.order.id));
    }

    /**
     * "This month" and "Last month" beat a month name for the two the user is most likely to
     * be looking at, because they answer the question without any arithmetic. The year is
     * dropped within the current year, where it adds nothing.
     */
    private HomeRow.Heading headingFor(long orderDate, long now, ZoneId zone) {
        if (MonthBucket.sameMonth(orderDate, now, zone)) {
            return HomeRow.Heading.THIS_MONTH;
        }
        if (MonthBucket.monthsBetween(orderDate, now, zone) == 1) {
            return HomeRow.Heading.LAST_MONTH;
        }
        return MonthBucket.of(orderDate, zone).getYear() == MonthBucket.of(now, zone).getYear()
                ? HomeRow.Heading.MONTH : HomeRow.Heading.MONTH_WITH_YEAR;
    }

    public LiveData<String> groupName() {
        return Transformations.map(household, value -> value == null ? "" : value.name);
    }

    /** SPEC 7.3.6. */
    public void rename(long orderId, String label) {
        orderRepository.rename(orderId, label);
    }

    public void delete(long orderId) {
        orderRepository.delete(orderId, result -> {
        });
    }

    /** SPEC 7.3.6: "Duplicate assignments". */
    public void duplicate(long orderId, com.householdsplitter.util.Callback<Long> onDone) {
        orderRepository.duplicate(orderId, result ->
                onDone.onResult(result.isOk() ? result.value() : null));
    }

    /** SPEC 7.3.6: "Export", which needs the order totalled first. */
    public void bundleFor(long orderId, com.householdsplitter.util.Callback<OrderBundle> onBundle) {
        orderRepository.bundle(orderId, onBundle);
    }
}
