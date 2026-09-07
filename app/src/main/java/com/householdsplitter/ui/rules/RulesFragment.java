package com.householdsplitter.ui.rules;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.householdsplitter.R;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.MemberRule;
import com.householdsplitter.databinding.FragmentRulesBinding;
import com.householdsplitter.databinding.ItemRuleBinding;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.common.Insets;
import com.householdsplitter.ui.common.MemberPalette;
import com.householdsplitter.ui.common.StateColors;

import java.util.List;

/**
 * Standing rules: "Ben is never on beer".
 *
 * <p>The app is not allowed to guess who an item is for (SPEC 7.9.13), which leaves the
 * household correcting the same item every order. This screen lets them write the correction
 * down once. It is the honest version of a guess, because the household made it.
 */
public class RulesFragment extends BaseFragment {

    private FragmentRulesBinding binding;
    private RulesViewModel model;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRulesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        model = viewModel(RulesViewModel.class);

        Insets.padTop(binding.toolbar);
        Insets.padBottomScrollable(binding.scroll);

        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        binding.kindGroup.check(R.id.kindExclude);
        binding.kindGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            model.pickKind(checkedId == R.id.kindInclude
                    ? MemberRule.Kind.INCLUDE : MemberRule.Kind.EXCLUDE);
        });

        model.members().observe(getViewLifecycleOwner(), this::renderMemberChips);
        model.rules().observe(getViewLifecycleOwner(), this::renderRules);

        binding.addButton.setOnClickListener(v -> submit());
    }

    private void submit() {
        String keyword = binding.keywordInput.getText() == null
                ? "" : binding.keywordInput.getText().toString();
        model.add(keyword, result -> {
            if (binding == null || result == null) {
                return;
            }
            if (!result.isOk()) {
                binding.keywordField.setError(result.error());
                return;
            }
            binding.keywordField.setError(null);
            binding.keywordInput.setText("");
            Snackbar.make(binding.getRoot(), R.string.rules_added, Snackbar.LENGTH_SHORT).show();
        });
    }

    /**
     * A chip per member rather than a dropdown: the household is small, and seeing the names
     * at once is faster than opening a menu to find out who is even an option.
     */
    private void renderMemberChips(List<Member> members) {
        binding.memberChips.removeAllViews();
        if (members == null || members.isEmpty()) {
            return;
        }
        Long selected = model.pendingMemberId().getValue();
        boolean anySelected = false;
        for (Member member : members) {
            Chip chip = new Chip(requireContext());
            chip.setText(member.name);
            chip.setCheckable(true);
            chip.setId(View.generateViewId());
            long memberId = member.id;
            chip.setChecked(selected != null && selected == memberId);
            anySelected |= chip.isChecked();
            chip.setOnClickListener(v -> model.pickMember(memberId));
            binding.memberChips.addView(chip);
        }
        // Preselecting the first member means the common case is one field and a button.
        if (!anySelected) {
            View first = binding.memberChips.getChildAt(0);
            if (first instanceof Chip) {
                ((Chip) first).setChecked(true);
            }
            model.pickMember(members.get(0).id);
        }
    }

    private void renderRules(List<MemberRule> rules) {
        binding.ruleContainer.removeAllViews();
        boolean empty = rules == null || rules.isEmpty();
        binding.emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (MemberRule rule : rules) {
            ItemRuleBinding row = ItemRuleBinding.inflate(inflater, binding.ruleContainer, false);
            Member member = model.member(rule.memberId);
            String name = member == null ? getString(R.string.rules_unknown_member) : member.name;
            row.avatar.setText(member == null ? "?" : member.initials());
            row.avatar.setBackgroundTintList(ColorStateList.valueOf(MemberPalette.resolve(
                    requireContext(), member == null ? null : member.colorHex)));
            row.avatar.setContentDescription(name);

            boolean exclude = rule.kind == MemberRule.Kind.EXCLUDE;
            row.ruleText.setText(getString(
                    exclude ? R.string.rule_row_exclude : R.string.rule_row_include,
                    name, rule.keyword));
            row.ruleKind.setText(exclude
                    ? R.string.rules_kind_exclude : R.string.rules_kind_include);
            row.ruleKind.setTextColor(StateColors.content(requireContext(),
                    exclude ? StateColors.State.WARNING : StateColors.State.SUCCESS));

            long ruleId = rule.id;
            row.deleteButton.setOnClickListener(v -> model.delete(ruleId, result -> {
                if (binding == null) {
                    return;
                }
                Snackbar.make(binding.getRoot(), R.string.rules_deleted, Snackbar.LENGTH_SHORT)
                        .show();
            }));
            binding.ruleContainer.addView(row.getRoot());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
