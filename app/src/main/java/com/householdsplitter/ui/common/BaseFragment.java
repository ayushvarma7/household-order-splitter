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

    /**
     * Motion, set once here rather than screen by screen.
     *
     * <p>A transition is a sentence about where you just went, and it is only useful if
     * every screen says it the same way. Set per screen it becomes decoration: some
     * fragments slide, some appear, and the movement stops carrying any information at all.
     *
     * <p>Two kinds, because there are two kinds of navigation in this app.
     *
     * <p>Going deeper slides along the X axis, forward on the way in and backward on the
     * way out, so the direction of travel matches the direction of the gesture that caused
     * it. Home to an order to its split is a hierarchy and the motion says so.
     *
     * <p>Moving between the four tabs fades through instead. Those are siblings: nothing is
     * deeper than anything else, and sliding between them would invent a left-to-right
     * order that the bar does not have. {@link #isTopLevel()} is what tells them apart.
     *
     * <p>Done in onCreate because a transition set after the fragment's view exists is a
     * transition the first navigation does not get.
     */
    @Override
    public void onCreate(@androidx.annotation.Nullable android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isTopLevel()) {
            setEnterTransition(new com.google.android.material.transition.MaterialFadeThrough());
            setExitTransition(new com.google.android.material.transition.MaterialFadeThrough());
            return;
        }
        setEnterTransition(forward(true));
        setReturnTransition(forward(false));
        setExitTransition(forward(true));
        setReenterTransition(forward(false));
    }

    private static com.google.android.material.transition.MaterialSharedAxis forward(
            boolean entering) {
        return new com.google.android.material.transition.MaterialSharedAxis(
                com.google.android.material.transition.MaterialSharedAxis.X, entering);
    }

    /**
     * True for the four destinations the bar offers, which are siblings rather than a
     * hierarchy. False by default, since most screens are somewhere below one of them.
     */
    protected boolean isTopLevel() {
        return false;
    }

    protected ServiceLocator locator() {
        return ((SplitterApp) requireActivity().getApplication()).serviceLocator();
    }

    protected <T extends ViewModel> T viewModel(@NonNull Class<T> type) {
        return new ViewModelProvider(this,
                new ViewModelFactory(this, getArguments(), locator())).get(type);
    }
}
