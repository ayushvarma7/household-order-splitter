package com.householdsplitter.ui.setup;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.householdsplitter.R;
import com.householdsplitter.databinding.FragmentSetupGroupBinding;
import com.householdsplitter.ui.common.BaseFragment;

/**
 * S1, group setup. SPEC 7.1.
 *
 * <p>The field is empty on load and there is no hint that could be mistaken for a value
 * (SPEC 7.1.3), no example name anywhere in the layout, and nothing seeded behind it
 * (SPEC 1.6, PROMPT hard rule 2).
 */
public class SetupGroupFragment extends BaseFragment {

    private FragmentSetupGroupBinding binding;
    private SetupGroupViewModel model;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSetupGroupBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        model = viewModel(SetupGroupViewModel.class);

        String current = model.currentName();
        if (!current.contentEquals(binding.groupNameInput.getText() == null
                ? "" : binding.groupNameInput.getText())) {
            binding.groupNameInput.setText(current);
        }

        binding.groupNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                model.onNameChanged(s == null ? "" : s.toString());
                binding.groupNameLayout.setError(null);
            }
        });

        // SPEC 7.1.4
        model.canContinue().observe(getViewLifecycleOwner(), enabled ->
                binding.continueButton.setEnabled(Boolean.TRUE.equals(enabled)));

        binding.continueButton.setOnClickListener(v -> model.onContinue());

        model.error().observe(getViewLifecycleOwner(), message -> {
            if (message != null) {
                binding.groupNameLayout.setError(message);
                model.errorShown();
            }
        });

        // SPEC 7.1.5: once a household exists, S1 must not be reachable by going back.
        model.created().observe(getViewLifecycleOwner(), householdId -> {
            if (householdId == null) {
                return;
            }
            NavOptions options = new NavOptions.Builder()
                    .setPopUpTo(R.id.setupGroupFragment, true)
                    .build();
            NavHostFragment.findNavController(this)
                    .navigate(R.id.setupMembersFragment, null, options);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
