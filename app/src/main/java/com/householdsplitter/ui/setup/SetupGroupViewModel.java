package com.householdsplitter.ui.setup;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.repo.HouseholdRepository;
import com.householdsplitter.util.Result;

/** S1. SPEC 7.1. */
public class SetupGroupViewModel extends ViewModel {

    private static final String KEY_NAME = "group_name";

    private final HouseholdRepository repository;
    private final SavedStateHandle handle;
    private final MutableLiveData<Long> created = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public SetupGroupViewModel(HouseholdRepository repository, SavedStateHandle handle) {
        this.repository = repository;
        this.handle = handle;
        // SPEC 7.1.3: empty on load. Nothing seeds this value (SPEC 1.6).
        if (!handle.contains(KEY_NAME)) {
            handle.set(KEY_NAME, "");
        }
    }

    /** SPEC 7.1.6: survives rotation, and process death with it. */
    public LiveData<String> name() {
        return handle.getLiveData(KEY_NAME);
    }

    public String currentName() {
        String value = handle.get(KEY_NAME);
        return value == null ? "" : value;
    }

    public void onNameChanged(String value) {
        handle.set(KEY_NAME, value == null ? "" : value);
    }

    /** SPEC 7.1.4: Continue stays disabled until the trimmed input is 1 to 60 characters. */
    public LiveData<Boolean> canContinue() {
        return Transformations.map(handle.<String>getLiveData(KEY_NAME), Household::isValidName);
    }

    public LiveData<Long> created() {
        return created;
    }

    public LiveData<String> error() {
        return error;
    }

    public void onContinue() {
        repository.createHousehold(currentName(), (Result<Long> result) -> {
            if (result.isOk()) {
                created.setValue(result.value());
            } else {
                error.setValue(result.error());
            }
        });
    }

    public void errorShown() {
        error.setValue(null);
    }
}
