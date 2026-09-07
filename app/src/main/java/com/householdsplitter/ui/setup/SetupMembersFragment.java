package com.householdsplitter.ui.setup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.householdsplitter.R;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.databinding.FragmentSetupMembersBinding;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.common.Insets;

/**
 * S2, member setup, and S13, the same list reached later from Home. SPEC 7.2 and 7.13.
 *
 * <p>There is deliberately no "add sample members" or "quick add 4" shortcut anywhere on
 * this screen (SPEC 7.2.10), and nothing assumes any particular member count
 * (PROMPT hard rule 6).
 */
public class SetupMembersFragment extends BaseFragment {

    /** True when reached from Home for later edits, which changes the bottom button. */
    public static final String ARG_MANAGE_MODE = "manage_mode";

    private FragmentSetupMembersBinding binding;
    private SetupMembersViewModel model;
    private MemberAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSetupMembersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        model = viewModel(SetupMembersViewModel.class);

        Insets.padTop(binding.toolbar);
        Insets.padTop(binding.header);
        Insets.padBottom(binding.footer);
        boolean manageMode = getArguments() != null
                && getArguments().getBoolean(ARG_MANAGE_MODE, false);

        adapter = new MemberAdapter(new MemberAdapter.Listener() {
            @Override
            public void onEdit(Member member) {
                showRenameDialog(member);
            }

            @Override
            public void onDelete(Member member) {
                confirmRemove(member);
            }
        });
        binding.memberList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.memberList.setAdapter(adapter);

        binding.toolbar.setVisibility(manageMode ? View.VISIBLE : View.GONE);
        binding.renameGroupButton.setVisibility(manageMode ? View.VISIBLE : View.GONE);
        if (manageMode) {
            binding.toolbar.setNavigationOnClickListener(v ->
                    NavHostFragment.findNavController(this).popBackStack());
            binding.renameGroupButton.setOnClickListener(v -> showRenameGroupDialog());
        }
        binding.doneButton.setText(manageMode ? R.string.action_save : R.string.action_done);

        // SPEC 7.2.2: "Who's in <group name>?"
        model.groupName().observe(getViewLifecycleOwner(), name ->
                binding.title.setText(getString(R.string.members_title, name == null ? "" : name)));

        model.members().observe(getViewLifecycleOwner(), members -> {
            adapter.submitList(members);
            binding.emptyHint.setVisibility(
                    members == null || members.isEmpty() ? View.VISIBLE : View.GONE);
            int count = members == null ? 0 : members.size();
            binding.countHint.setText(getResources().getQuantityString(
                    R.plurals.member_count, count, count));
        });

        // SPEC 7.2.9
        model.canFinish().observe(getViewLifecycleOwner(), enabled ->
                binding.doneButton.setEnabled(Boolean.TRUE.equals(enabled)));

        model.inlineError().observe(getViewLifecycleOwner(), message -> {
            if (message != null) {
                binding.nameLayout.setError(message);
                model.errorShown();
            }
        });

        model.toast().observe(getViewLifecycleOwner(), message -> {
            if (message != null) {
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                model.toastShown();
            }
        });

        binding.addButton.setOnClickListener(v -> submitName());

        // SPEC 7.2.4: IME Done adds and keeps focus, so several names can be typed in a row
        // without touching the screen.
        binding.nameInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitName();
                return true;
            }
            return false;
        });
        binding.nameInput.addTextChangedListener(new SimpleWatcher(() ->
                binding.nameLayout.setError(null)));

        binding.doneButton.setOnClickListener(v -> {
            if (manageMode) {
                NavHostFragment.findNavController(this).popBackStack();
            } else {
                NavOptions options = new NavOptions.Builder()
                        .setPopUpTo(R.id.setupMembersFragment, true)
                        .build();
                NavHostFragment.findNavController(this)
                        .navigate(R.id.homeFragment, null, options);
            }
        });
    }

    private void submitName() {
        TextInputEditText input = binding.nameInput;
        String value = input.getText() == null ? "" : input.getText().toString();
        if (value.trim().isEmpty()) {
            binding.nameLayout.setError(getString(R.string.error_name_empty));
            return;
        }
        model.addMember(value);
        input.setText("");
        input.requestFocus();
    }

    /** SPEC 7.2.7: an inline dialog with the current name pre-filled. */
    private void showRenameDialog(Member member) {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_single_input, null, false);
        com.google.android.material.textfield.TextInputEditText field = content.findViewById(R.id.dialogInput);
        com.google.android.material.textfield.TextInputLayout layout = content.findViewById(R.id.dialogInputLayout);
        layout.setHint(getString(R.string.hint_member_name));
        field.setText(member.name);
        field.setSelection(member.name.length());

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_rename_member)
                .setView(content)
                .setPositiveButton(R.string.action_save, (dialog, which) -> model.renameMember(
                        member.id, field.getText() == null ? "" : field.getText().toString()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showRenameGroupDialog() {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_single_input, null, false);
        com.google.android.material.textfield.TextInputEditText field = content.findViewById(R.id.dialogInput);
        com.google.android.material.textfield.TextInputLayout layout = content.findViewById(R.id.dialogInputLayout);
        layout.setHint(getString(R.string.hint_group_name));
        if (model.household().getValue() != null) {
            field.setText(model.household().getValue().name);
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.action_rename_group)
                .setView(content)
                .setPositiveButton(R.string.action_save, (dialog, which) -> model.renameGroup(
                        field.getText() == null ? "" : field.getText().toString()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** SPEC 7.2.8: immediate removal with no history, a confirmation and archive with it. */
    private void confirmRemove(Member member) {
        model.hasHistory(member.id, hasHistory -> {
            if (binding == null) {
                return;
            }
            if (Boolean.TRUE.equals(hasHistory)) {
                new AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.dialog_remove_member_title, member.name))
                        .setMessage(R.string.dialog_remove_member_message)
                        .setPositiveButton(R.string.action_archive,
                                (dialog, which) -> model.removeMember(member.id))
                        .setNegativeButton(R.string.action_cancel, null)
                        .show();
            } else {
                model.removeMember(member.id);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.memberList.setAdapter(null);
        binding = null;
    }

    /** Small helper so the fragment does not carry three anonymous TextWatchers. */
    static class SimpleWatcher implements android.text.TextWatcher {

        private final Runnable onChanged;

        SimpleWatcher(Runnable onChanged) {
            this.onChanged = onChanged;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {
            onChanged.run();
        }
    }
}
