package com.householdsplitter.ui.details;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.householdsplitter.core.parse.model.OrderField;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.relation.OrderBundle;
import com.householdsplitter.data.repo.OrderRepository;

import java.util.HashSet;
import java.util.Set;

/** S7. SPEC 7.7. */
public class OrderDetailsViewModel extends ViewModel {

    private final OrderRepository repository;
    private final long orderId;
    private final LiveData<OrderBundle> bundle;
    private final Set<OrderField> editedByUser = new HashSet<>();

    public OrderDetailsViewModel(OrderRepository repository, long orderId) {
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

    /**
     * SPEC 7.7.2: a field parsed with confidence shows a "from screenshot" marker, and
     * loses it once the user edits it.
     */
    public boolean isFromScreenshot(Order order, OrderField field) {
        if (editedByUser.contains(field)) {
            return false;
        }
        if (order == null || order.parsedFieldsCsv == null) {
            return false;
        }
        for (String name : order.parsedFieldsCsv.split(",")) {
            if (name.trim().equals(field.name())) {
                return true;
            }
        }
        return false;
    }

    public void markEdited(OrderField field) {
        editedByUser.add(field);
    }

    public void save(Order order) {
        repository.saveOrder(order, result -> {
        });
    }
}
