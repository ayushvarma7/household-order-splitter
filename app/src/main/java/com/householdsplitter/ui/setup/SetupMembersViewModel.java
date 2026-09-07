package com.householdsplitter.ui.setup;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.repo.HouseholdRepository;
import com.householdsplitter.util.Result;

import java.util.Collections;
import java.util.List;

/** S2 and S13. SPEC 7.2 and 7.13. */
public class SetupMembersViewModel extends ViewModel {

    /** SPEC 7.2.9: Done stays disabled below two members. No upper limit anywhere. */
    public static final int MINIMUM_MEMBERS = 2;

    private final HouseholdRepository repository;
    private final LiveData<Household> household;
    private final LiveData<List<Member>> members;
    private final MutableLiveData<String> inlineError = new MutableLiveData<>();
    private final MutableLiveData<String> toast = new MutableLiveData<>();

    public SetupMembersViewModel(HouseholdRepository repository, SavedStateHandle handle) {
        this.repository = repository;
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

    /** SPEC 7.2.2: the title reads "Who's in <group name>?". */
    public LiveData<String> groupName() {
        return Transformations.map(household, value -> value == null ? "" : value.name);
    }

    public LiveData<Boolean> canFinish() {
        MediatorLiveData<Boolean> result = new MediatorLiveData<>();
        result.addSource(members, list ->
                result.setValue(list != null && list.size() >= MINIMUM_MEMBERS));
        return result;
    }

    public LiveData<String> inlineError() {
        return inlineError;
    }

    public LiveData<String> toast() {
        return toast;
    }

    /** SPEC 7.2.4 and 7.2.5. */
    public void addMember(String name) {
        Household current = household.getValue();
        if (current == null) {
            inlineError.setValue("No group yet");
            return;
        }
        repository.addMember(current.id, name, (Result<Long> result) -> {
            if (!result.isOk()) {
                inlineError.setValue(result.error());
            }
        });
    }

    /** SPEC 7.2.7. */
    public void renameMember(long memberId, String name) {
        repository.renameMember(memberId, name, (Result<Void> result) -> {
            if (!result.isOk()) {
                toast.setValue(result.error());
            }
        });
    }

    /** SPEC 7.2.8 and 5.10. */
    public void removeMember(long memberId) {
        repository.removeMember(memberId, (Result<Boolean> result) -> {
            if (!result.isOk()) {
                toast.setValue(result.error());
            } else if (Boolean.TRUE.equals(result.value())) {
                toast.setValue("Archived, because they appear in past orders");
            }
        });
    }

    public void hasHistory(long memberId, com.householdsplitter.util.Callback<Boolean> callback) {
        repository.hasHistory(memberId, callback);
    }

    /** SPEC 7.13.1. */
    public void renameGroup(String name) {
        Household current = household.getValue();
        if (current == null) {
            return;
        }
        repository.renameHousehold(current.id, name, (Result<Void> result) -> {
            if (!result.isOk()) {
                toast.setValue(result.error());
            }
        });
    }

    public void errorShown() {
        inlineError.setValue(null);
    }

    public void toastShown() {
        toast.setValue(null);
    }
}
