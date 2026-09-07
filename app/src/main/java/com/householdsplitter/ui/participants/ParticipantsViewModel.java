package com.householdsplitter.ui.participants;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.relation.OrderBundle;
import com.householdsplitter.data.repo.HouseholdRepository;
import com.householdsplitter.data.repo.OrderRepository;
import com.householdsplitter.util.Callback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** S8. SPEC 7.8. */
public class ParticipantsViewModel extends ViewModel {

    private final OrderRepository orderRepository;
    private final long orderId;
    private final LiveData<OrderBundle> bundle;
    private final LiveData<List<Member>> allMembers;
    private final MutableLiveData<Set<Long>> selected = new MutableLiveData<>(new LinkedHashSet<>());

    private boolean initialised;

    public ParticipantsViewModel(OrderRepository orderRepository,
                                 HouseholdRepository householdRepository,
                                 long orderId, long householdId) {
        this.orderRepository = orderRepository;
        this.orderId = orderId;
        this.bundle = orderRepository.observeBundle(orderId);
        this.allMembers = householdRepository.observeMembers(householdId);
    }

    /** SPEC 7.8.2: every non-archived member is listed. */
    public LiveData<List<Member>> members() {
        return allMembers;
    }

    public LiveData<Set<Long>> selected() {
        return selected;
    }

    public LiveData<OrderBundle> bundle() {
        return bundle;
    }

    /** SPEC 7.8.3. */
    public LiveData<Integer> selectedCount() {
        return Transformations.map(selected, value -> value == null ? 0 : value.size());
    }

    /**
     * SPEC 7.8.2: all checked by default. When revisited from the summary the existing
     * participant list wins, so an earlier choice is not silently reset.
     */
    public void initialiseFrom(OrderBundle current, List<Member> members) {
        if (initialised || members == null) {
            return;
        }
        Set<Long> next = new LinkedHashSet<>();
        if (current != null && !current.participants.isEmpty()) {
            for (Member member : current.participants) {
                next.add(member.id);
            }
        } else {
            for (Member member : members) {
                next.add(member.id);
            }
        }
        selected.setValue(next);
        initialised = true;
    }

    public void toggle(long memberId, boolean checked) {
        Set<Long> next = new LinkedHashSet<>(
                selected.getValue() == null ? Collections.<Long>emptySet() : selected.getValue());
        if (checked) {
            next.add(memberId);
        } else {
            next.remove(memberId);
        }
        selected.setValue(next);
    }

    public boolean isSelected(long memberId) {
        Set<Long> value = selected.getValue();
        return value != null && value.contains(memberId);
    }

    /** SPEC 7.8.5: the repository preserves assignments and drops only the removed ones. */
    public void save(Callback<Boolean> done) {
        Set<Long> value = selected.getValue();
        List<Long> ids = new ArrayList<>(value == null ? Collections.<Long>emptySet() : value);
        orderRepository.setParticipants(orderId, ids, result -> done.onResult(result.isOk()));
    }
}
