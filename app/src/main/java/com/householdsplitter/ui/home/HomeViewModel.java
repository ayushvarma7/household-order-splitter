package com.householdsplitter.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.relation.OrderWithMembers;
import com.householdsplitter.data.repo.HouseholdRepository;
import com.householdsplitter.data.repo.OrderRepository;

import java.util.Collections;
import java.util.List;

/** S3. SPEC 7.3. */
public class HomeViewModel extends ViewModel {

    private final OrderRepository orderRepository;
    private final LiveData<Household> household;
    private final LiveData<List<Member>> members;
    private final LiveData<List<OrderWithMembers>> orders;

    public HomeViewModel(HouseholdRepository householdRepository, OrderRepository orderRepository) {
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
}
