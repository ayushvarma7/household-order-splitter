package com.householdsplitter.core.insight;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.calc.input.CalcAssignment;
import com.householdsplitter.core.calc.input.CalcLineItem;
import com.householdsplitter.core.calc.input.CalcMember;
import com.householdsplitter.core.calc.input.CalcOrder;
import com.householdsplitter.core.money.MoneySplitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One thing somebody bought on one day, with who it was for.
 *
 * <p>This is the unit the "look inside" picture is built from. Each share is worked out with
 * the same largest-remainder split the calculator uses, so a row's shares always add up to
 * the row's price to the cent, however many ways it was divided.
 *
 * <p>Tax, fees and discounts are not spread over rows here. They belong to the whole order
 * and have no category, so this picture is of what the items cost, and the Spending screen
 * stays the place that shows what people paid in the end.
 */
public final class Purchase {

    private final long dateMillis;
    private final String name;
    private final long cents;
    private final Map<Long, Long> centsByMember;
    private final ItemCategorizer.Placement placement;
    private final String store;

    public Purchase(long dateMillis, String name, long cents, Map<Long, Long> centsByMember) {
        this(dateMillis, name, cents, centsByMember, null, "");
    }

    private Purchase(long dateMillis, String name, long cents, Map<Long, Long> centsByMember,
                     ItemCategorizer.Placement placement, String store) {
        this.placement = placement;
        this.store = store == null ? "" : store;
        this.dateMillis = dateMillis;
        this.name = name == null ? "" : name;
        this.cents = cents;
        this.centsByMember = Collections.unmodifiableMap(new LinkedHashMap<>(centsByMember));
    }

    public long dateMillis() {
        return dateMillis;
    }

    public String name() {
        return name;
    }

    public long cents() {
        return cents;
    }

    /** Where it was bought, as the store names itself, or empty when not known. */
    public String store() {
        return store;
    }

    /** The same purchase, recorded as bought at {@code store}. */
    public Purchase atStore(String store) {
        return new Purchase(dateMillis, name, cents, centsByMember, placement, store);
    }

    /** Where this row was put by hand rather than by its name, or null to read the name. */
    ItemCategorizer.Placement placement() {
        return placement;
    }

    /** True for the money the reader missed, which is not a thing anybody bought. */
    public boolean isMissing() {
        return placement != null && ItemCategorizer.MISSING.equals(placement.category());
    }

    /** What each person's part of this row came to, keyed by member id. */
    public Map<Long, Long> centsByMember() {
        return centsByMember;
    }

    /**
     * The money an order cost beyond what its rows add up to, as one row of its own.
     *
     * <p>Nobody can say who an unread item was for, so it is shared equally among the people
     * on the order, the same way a shared row is. Returns none when nothing is missing or
     * nobody is on the order.
     */
    public static List<Purchase> missing(long dateMillis, long missingCents, CalcOrder order) {
        List<CalcMember> members = order.participants();
        if (missingCents <= 0L || members.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Long> shares = sharesOf(
                CalcLineItem.common(0L, ItemCategorizer.MISSING_ITEM, missingCents), members);
        return Collections.singletonList(new Purchase(dateMillis, ItemCategorizer.MISSING_ITEM,
                missingCents, shares, new ItemCategorizer.Placement(
                        ItemCategorizer.MISSING, ItemCategorizer.MISSING_SUB), ""));
    }

    /**
     * The rows of one order, or none if the order is not finished.
     *
     * <p>An order with a row nobody has answered for, or with nobody on it, says nothing
     * reliable about who bought what, so it contributes nothing rather than a guess.
     * Excluded rows and rows with no positive price are skipped: nobody paid for the first
     * and the second is a credit that has no place in a "what did we buy" picture.
     */
    public static List<Purchase> fromOrder(long dateMillis, CalcOrder order) {
        List<CalcMember> members = order.participants();
        if (members.isEmpty()) {
            return Collections.emptyList();
        }
        List<Purchase> rows = new ArrayList<>();
        for (CalcLineItem item : order.items()) {
            if (item.scope().isUnanswered()) {
                return Collections.emptyList();
            }
            if (item.scope() == Scope.EXCLUDED || item.lineTotalCents() <= 0L) {
                continue;
            }
            Map<Long, Long> shares = sharesOf(item, members);
            if (shares.isEmpty()) {
                continue;
            }
            rows.add(new Purchase(dateMillis, item.name(), item.lineTotalCents(), shares));
        }
        return rows;
    }

    private static Map<Long, Long> sharesOf(CalcLineItem item, List<CalcMember> members) {
        List<Long> ids = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        if (item.scope() == Scope.COMMON) {
            for (CalcMember member : members) {
                ids.add(member.id());
                weights.add(1);
            }
        } else {
            for (CalcAssignment assignment : item.assignments()) {
                ids.add(assignment.memberId());
                weights.add(assignment.shares());
            }
        }
        Map<Long, Long> result = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        int[] weightArray = new int[weights.size()];
        for (int i = 0; i < weightArray.length; i++) {
            weightArray[i] = weights.get(i);
        }
        long[] parts = MoneySplitter.split(item.lineTotalCents(), weightArray);
        for (int i = 0; i < parts.length; i++) {
            result.merge(ids.get(i), parts[i], Long::sum);
        }
        return result;
    }
}
