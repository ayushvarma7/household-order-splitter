package com.householdsplitter.ui.assign;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.snackbar.Snackbar;
import com.householdsplitter.R;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.data.entity.ItemAssignment;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.relation.LineItemWithAssignments;
import com.householdsplitter.databinding.FragmentBulkAssignBinding;
import com.householdsplitter.databinding.ItemBulkRowBinding;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.common.Insets;
import com.householdsplitter.ui.common.MemberPalette;
import com.householdsplitter.ui.parsing.ParsingArgs;

import java.util.ArrayList;
import java.util.List;

/** Answering for several rows at once, alongside the one-at-a-time loop of SPEC 7.9. */
public class BulkAssignFragment extends BaseFragment {

    private FragmentBulkAssignBinding binding;
    private BulkAssignViewModel model;
    private CurrencyFormat money;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBulkAssignBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        model = viewModel(BulkAssignViewModel.class);
        money = new CurrencyFormat(locator().settings().currencySymbol(),
                locator().settings().locale());

        Insets.padTop(binding.toolbar);
        Insets.padBottom(binding.footer);
        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        binding.selectUnansweredButton.setOnClickListener(v -> model.selectUnanswered());
        binding.selectAllButton.setOnClickListener(v -> model.selectAll());

        binding.everyoneButton.setOnClickListener(v ->
                model.assignSelectedToEveryone(this::report));
        binding.justOneButton.setOnClickListener(this::showMemberMenu);
        binding.excludeButton.setOnClickListener(v -> model.excludeSelected(this::report));

        model.bundle().observe(getViewLifecycleOwner(), bundle -> renderRows());
        model.selected().observe(getViewLifecycleOwner(), selection -> renderRows());
        model.selectedCount().observe(getViewLifecycleOwner(), count -> {
            int value = count == null ? 0 : count;
            binding.selectionLabel.setText(getResources().getQuantityString(
                    R.plurals.bulk_selected, value, value));
            boolean any = value > 0;
            binding.everyoneButton.setEnabled(any);
            binding.justOneButton.setEnabled(any);
            binding.excludeButton.setEnabled(any);
        });
    }

    private void renderRows() {
        if (binding == null) {
            return;
        }
        binding.itemContainer.removeAllViews();
        List<LineItemWithAssignments> items = model.items();
        binding.emptyMessage.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = getLayoutInflater();
        for (LineItemWithAssignments row : items) {
            ItemBulkRowBinding view = ItemBulkRowBinding.inflate(
                    inflater, binding.itemContainer, false);
            view.itemName.setText(row.item.name);
            view.itemPrice.setText(money.format(row.item.lineTotalCents));
            view.currentAssignment.setText(describe(row));
            view.checkbox.setOnCheckedChangeListener(null);
            view.checkbox.setChecked(model.isSelected(row.item.id));
            view.checkbox.setOnCheckedChangeListener((button, checked) ->
                    model.toggle(row.item.id));
            view.getRoot().setOnClickListener(v -> model.toggle(row.item.id));
            view.getRoot().setContentDescription(row.item.name + ", "
                    + money.format(row.item.lineTotalCents) + ", " + describe(row));
            binding.itemContainer.addView(view.getRoot());
        }
    }

    /** SPEC 7.9.3.3, applied to a selection rather than one row. */
    private void showMemberMenu(View anchor) {
        List<Member> members = model.participants();
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        for (int i = 0; i < members.size(); i++) {
            menu.getMenu().add(0, i, i, members.get(i).name);
        }
        menu.setOnMenuItemClickListener(entry -> {
            model.assignSelectedTo(members.get(entry.getItemId()).id, this::report);
            return true;
        });
        menu.show();
    }

    private void report(Integer changed) {
        if (binding == null || changed == null || changed == 0) {
            return;
        }
        Snackbar.make(binding.getRoot(), getResources().getQuantityString(
                R.plurals.bulk_applied, changed, changed), Snackbar.LENGTH_SHORT).show();
    }

    private String describe(LineItemWithAssignments row) {
        switch (row.item.scope) {
            case COMMON:
                return getString(R.string.scope_common);
            case EXCLUDED:
                return getString(R.string.scope_excluded);
            case UNASSIGNED:
                return getString(R.string.scope_unassigned);
            default:
                StringBuilder names = new StringBuilder();
                for (ItemAssignment assignment : row.assignments) {
                    for (Member member : model.participants()) {
                        if (member.id == assignment.memberId) {
                            if (names.length() > 0) {
                                names.append(", ");
                            }
                            names.append(member.name);
                        }
                    }
                }
                return names.length() == 0
                        ? getString(R.string.scope_unassigned) : names.toString();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
