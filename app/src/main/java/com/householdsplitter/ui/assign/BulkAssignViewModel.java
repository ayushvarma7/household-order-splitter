package com.householdsplitter.ui.assign;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.relation.LineItemWithAssignments;
import com.householdsplitter.data.relation.OrderBundle;
import com.householdsplitter.data.repo.OrderRepository;
import com.householdsplitter.util.Callback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Answering for several rows at once.
 *
 * <p>The loop in SPEC 7.9 is one item at a time, which is the right shape for deciding. It
 * is the wrong shape for a decision already made: "these six are all mine" should cost one
 * action rather than six. This screen exists alongside the loop rather than replacing it.
 */
public class BulkAssignViewModel extends ViewModel {

    private final OrderRepository repository;
    private final long orderId;
    private final LiveData<OrderBundle> bundle;
    private final MutableLiveData<Set<Long>> selected = new MutableLiveData<>(new LinkedHashSet<>());

    public BulkAssignViewModel(OrderRepository repository, long orderId) {
        this.repository = repository;
        this.orderId = orderId;
        this.bundle = repository.observeBundle(orderId);
    }

    public LiveData<OrderBundle> bundle() {
        return bundle;
    }

    public LiveData<Set<Long>> selected() {
        return selected;
    }

    public LiveData<Integer> selectedCount() {
        return Transformations.map(selected, value -> value == null ? 0 : value.size());
    }

    public List<LineItemWithAssignments> items() {
        OrderBundle value = bundle.getValue();
        return value == null ? new ArrayList<>() : value.items;
    }

    public List<Member> participants() {
        OrderBundle value = bundle.getValue();
        return value == null ? new ArrayList<>() : value.participants;
    }

    public boolean isSelected(long lineItemId) {
        Set<Long> value = selected.getValue();
        return value != null && value.contains(lineItemId);
    }

    public void toggle(long lineItemId) {
        Set<Long> next = new LinkedHashSet<>(current());
        if (!next.remove(lineItemId)) {
            next.add(lineItemId);
        }
        selected.setValue(next);
    }

    /** The common case on a repeat shop: everything nobody has answered for yet. */
    public void selectUnanswered() {
        Set<Long> next = new LinkedHashSet<>();
        for (LineItemWithAssignments row : items()) {
            if (row.item.scope.isUnanswered()) {
                next.add(row.item.id);
            }
        }
        selected.setValue(next);
    }

    public void selectAll() {
        Set<Long> next = new LinkedHashSet<>();
        for (LineItemWithAssignments row : items()) {
            next.add(row.item.id);
        }
        selected.setValue(next);
    }

    public void clearSelection() {
        selected.setValue(new LinkedHashSet<>());
    }

    /** Everyone in the order, which SPEC 7.9.4 makes COMMON. */
    public void assignSelectedToEveryone(Callback<Integer> onDone) {
        apply(Scope.COMMON, Collections.emptyList(), onDone);
    }

    public void assignSelectedTo(long memberId, Callback<Integer> onDone) {
        apply(Scope.PERSONAL, Collections.singletonList(memberId), onDone);
    }

    public void assignSelectedToSubset(List<Long> memberIds, Callback<Integer> onDone) {
        if (memberIds.size() == 1) {
            apply(Scope.PERSONAL, memberIds, onDone);
        } else if (memberIds.size() == participants().size()) {
            // SPEC 7.9.4: everybody selected is a common item, whichever route got there.
            apply(Scope.COMMON, Collections.emptyList(), onDone);
        } else {
            apply(Scope.SUBSET, memberIds, onDone);
        }
    }

    /** SPEC 7.9.7, in bulk. */
    public void excludeSelected(Callback<Integer> onDone) {
        apply(Scope.EXCLUDED, Collections.emptyList(), onDone);
    }

    private void apply(Scope scope, List<Long> memberIds, Callback<Integer> onDone) {
        List<Long> ids = new ArrayList<>(current());
        if (ids.isEmpty()) {
            onDone.onResult(0);
            return;
        }
        repository.assignMany(ids, scope, memberIds, null, result -> {
            clearSelection();
            onDone.onResult(result.isOk() ? result.value() : 0);
        });
    }

    private Set<Long> current() {
        Set<Long> value = selected.getValue();
        return value == null ? new LinkedHashSet<>() : value;
    }
}
