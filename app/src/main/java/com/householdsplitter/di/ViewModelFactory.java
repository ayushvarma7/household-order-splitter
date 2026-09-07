package com.householdsplitter.di;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.savedstate.SavedStateRegistryOwner;

import com.householdsplitter.ui.home.HomeViewModel;
import com.householdsplitter.ui.importer.ImportViewModel;
import com.householdsplitter.ui.parsing.ParsingViewModel;
import com.householdsplitter.ui.setup.SetupGroupViewModel;
import com.householdsplitter.ui.setup.SetupMembersViewModel;

/**
 * Builds every ViewModel by hand, in keeping with the ServiceLocator choice of SPEC 4.8.
 *
 * <p>Extends the saved-state factory so each ViewModel gets a {@link SavedStateHandle}.
 * That is what keeps typed input alive across rotation (SPEC 7.1.6) and across process
 * death (SPEC 11.14) without any screen having to reimplement it.
 */
public class ViewModelFactory extends AbstractSavedStateViewModelFactory {

    private final ServiceLocator locator;

    public ViewModelFactory(SavedStateRegistryOwner owner, Bundle defaultArgs,
                            ServiceLocator locator) {
        super(owner, defaultArgs);
        this.locator = locator;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    protected <T extends ViewModel> T create(@NonNull String key, @NonNull Class<T> modelClass,
                                             @NonNull SavedStateHandle handle) {
        if (modelClass == SetupGroupViewModel.class) {
            return (T) new SetupGroupViewModel(locator.householdRepository(), handle);
        }
        if (modelClass == SetupMembersViewModel.class) {
            return (T) new SetupMembersViewModel(locator.householdRepository(), handle);
        }
        if (modelClass == HomeViewModel.class) {
            return (T) new HomeViewModel(locator.householdRepository());
        }
        if (modelClass == ImportViewModel.class) {
            return (T) new ImportViewModel(handle);
        }
        if (modelClass == ParsingViewModel.class) {
            return (T) new ParsingViewModel(
                    locator.orderRepository(),
                    locator.householdRepository(),
                    locator.receiptParser(),
                    locator.executors(),
                    locator.currentHouseholdId());
        }
        throw new IllegalArgumentException("No ViewModel wired for " + modelClass.getName());
    }
}
