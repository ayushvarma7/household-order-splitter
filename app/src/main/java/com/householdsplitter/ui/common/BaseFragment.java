package com.householdsplitter.ui.common;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.householdsplitter.SplitterApp;
import com.householdsplitter.di.ServiceLocator;
import com.householdsplitter.di.ViewModelFactory;

/**
 * Shared plumbing so no screen repeats it.
 *
 * <p>SPEC 4.5: fragments hold no business logic and no money math. They observe a
 * ViewModel and render what it hands them.
 */
public abstract class BaseFragment extends Fragment {

    protected ServiceLocator locator() {
        return ((SplitterApp) requireActivity().getApplication()).serviceLocator();
    }

    protected <T extends ViewModel> T viewModel(@NonNull Class<T> type) {
        return new ViewModelProvider(this,
                new ViewModelFactory(this, getArguments(), locator())).get(type);
    }
}
