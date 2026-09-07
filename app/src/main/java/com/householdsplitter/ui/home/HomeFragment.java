package com.householdsplitter.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.householdsplitter.R;
import com.householdsplitter.databinding.FragmentHomeBinding;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.setup.SetupMembersFragment;

/** S3, Home. SPEC 7.3. */
public class HomeFragment extends BaseFragment {

    private FragmentHomeBinding binding;
    private HomeViewModel model;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        model = viewModel(HomeViewModel.class);

        binding.orderList.setLayoutManager(new LinearLayoutManager(requireContext()));

        model.groupName().observe(getViewLifecycleOwner(), name ->
                binding.toolbar.setTitle(name == null || name.isEmpty()
                        ? getString(R.string.app_name) : name));

        model.members().observe(getViewLifecycleOwner(), members ->
                binding.memberSummary.setText(members == null || members.isEmpty()
                        ? "" : getResources().getQuantityString(
                        R.plurals.member_count, members.size(), members.size())));

        // SPEC 7.3.5: the empty state, with a button that does the same thing as the FAB.
        binding.orderList.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.VISIBLE);

        binding.newOrderFab.setOnClickListener(v -> startNewOrder());
        binding.emptyStateButton.setOnClickListener(v -> startNewOrder());

        // SPEC 7.3.2: the overflow menu.
        binding.toolbar.inflateMenu(R.menu.menu_home);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_members) {
                Bundle args = new Bundle();
                args.putBoolean(SetupMembersFragment.ARG_MANAGE_MODE, true);
                NavHostFragment.findNavController(this)
                        .navigate(R.id.setupMembersFragment, args);
                return true;
            }
            if (id == R.id.action_settings || id == R.id.action_export_all) {
                notYetWired();
                return true;
            }
            return false;
        });
    }

    private void startNewOrder() {
        notYetWired();
    }

    /**
     * Temporary. The import flow of SPEC 7.4 onwards is the next phase; this keeps the
     * button honest instead of silently doing nothing.
     */
    private void notYetWired() {
        Snackbar.make(binding.getRoot(), R.string.not_yet_built, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.orderList.setAdapter(null);
        binding = null;
    }
}
