package com.householdsplitter.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.repo.HouseholdRepository;

import java.util.Collections;
import java.util.List;

/** S3. SPEC 7.3. */
public class HomeViewModel extends ViewModel {

    private final LiveData<Household> household;
    private final LiveData<List<Member>> members;

    public HomeViewModel(HouseholdRepository repository) {
        this.household = repository.observeHousehold();
        this.members = Transformations.switchMap(household, value -> {
            if (value == null) {
                return new MutableLiveData<>(Collections.<Member>emptyList());
            }
            return repository.observeMembers(value.id);
        });
    }

    public LiveData<Household> household() {
        return household;
    }

    public LiveData<List<Member>> members() {
        return members;
    }

    /** SPEC 7.3.2: the toolbar carries the group name. */
    public LiveData<String> groupName() {
        return Transformations.map(household, value -> value == null ? "" : value.name);
    }
}
