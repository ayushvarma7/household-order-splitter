package com.householdsplitter.ui.review;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.householdsplitter.core.parse.model.ParsedAdjustments;
import com.householdsplitter.core.parse.Reconciler;
import com.householdsplitter.core.parse.model.Reconciliation;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.relation.LineItemWithAssignments;
import com.householdsplitter.data.relation.OrderBundle;
import com.householdsplitter.data.repo.OrderRepository;

import java.util.ArrayList;
import java.util.List;

/** S6. SPEC 7.6. */
public class ReviewItemsViewModel extends ViewModel {

    private final OrderRepository repository;
    private final long orderId;
    private final LiveData<OrderBundle> bundle;
    private final MutableLiveData<LineItem> lastDeleted = new MutableLiveData<>();

    public ReviewItemsViewModel(OrderRepository repository, long orderId) {
        this.repository = repository;
        this.orderId = orderId;
        this.bundle = repository.observeBundle(orderId);
    }

    public long orderId() {
        return orderId;
    }

    public LiveData<OrderBundle> bundle() {
        return bundle;
    }

    public LiveData<List<LineItem>> items() {
        return Transformations.map(bundle, value -> {
            List<LineItem> items = new ArrayList<>();
            if (value != null) {
                for (LineItemWithAssignments row : value.items) {
                    items.add(row.item);
                }
            }
            return items;
        });
    }

    /** SPEC 7.6.2 and 8.9: the header banner reports the reconciliation state. */
    public LiveData<Reconciliation> reconciliation() {
        return Transformations.map(bundle, value -> {
            if (value == null) {
                return null;
            }
            long itemsSubtotal = 0L;
            for (LineItemWithAssignments row : value.items) {
                if (row.item.scope.isChargeable() || row.item.scope.isUnanswered()) {
                    itemsSubtotal += row.item.lineTotalCents;
                }
            }
            ParsedAdjustments adjustments = new ParsedAdjustments(
                    value.order.statedSubtotalCents,
                    value.order.taxCents,
                    value.order.deliveryFeeCents,
                    value.order.tipCents,
                    value.order.otherFeeCents,
                    value.order.discountCents,
                    value.order.statedTotalCents);
            return Reconciler.reconcile(itemsSubtotal, adjustments);
        });
    }

    /** SPEC 7.6.9: the offending rows, so they can be scrolled into view. */
    public List<Integer> blockingPositions() {
        List<Integer> positions = new ArrayList<>();
        OrderBundle value = bundle.getValue();
        if (value == null) {
            return positions;
        }
        for (int i = 0; i < value.items.size(); i++) {
            if (!value.items.get(i).item.isReadyForSplitting()) {
                positions.add(i);
            }
        }
        return positions;
    }

    public void save(LineItem item) {
        repository.saveItem(item, result -> {
        });
    }

    /** SPEC 7.6.8: delete with an Undo that really restores the row. */
    public void delete(LineItem item) {
        lastDeleted.setValue(item);
        repository.deleteItem(item, result -> {
        });
    }

    public void undoDelete() {
        LineItem item = lastDeleted.getValue();
        if (item != null) {
            repository.restoreItem(item, result -> {
            });
            lastDeleted.setValue(null);
        }
    }

    /**
     * How many units the bill mentioned, or -1.
     *
     * <p>A hint only (SPEC 8.5.4). Useful for saying "the order mentions 33 units and you
     * have 5 rows", which points at a missing screenshot.
     */
    public int deliveredUnitCount() {
        OrderBundle value = bundle.getValue();
        return value == null ? -1 : value.order.deliveredUnitCount;
    }

    public int itemCount() {
        OrderBundle value = bundle.getValue();
        return value == null ? 0 : value.items.size();
    }

    /** The money the rows do not account for, as a positive shortfall or negative excess. */
    public long unaccountedCents() {
        OrderBundle value = bundle.getValue();
        if (value == null) {
            return 0L;
        }
        long items = 0L;
        for (LineItemWithAssignments row : value.items) {
            if (row.item.scope.isChargeable() || row.item.scope.isUnanswered()) {
                items += row.item.lineTotalCents;
            }
        }
        long stated = value.order.statedSubtotalCents;
        return stated == 0L ? 0L : stated - items;
    }

    /** Books the shortfall as one row rather than leaving the user to hunt for it. */
    public void addDifferenceAsItem(String name, com.householdsplitter.util.Callback<Long> onDone) {
        repository.addDifferenceItem(orderId, unaccountedCents(), name,
                result -> onDone.onResult(result.isOk() ? result.value() : null));
    }

    /** SPEC 7.6.7: a blank row, opened straight into the edit sheet. */
    public LineItem newBlankItem() {
        LineItem item = new LineItem();
        item.orderId = orderId;
        return item;
    }
}
