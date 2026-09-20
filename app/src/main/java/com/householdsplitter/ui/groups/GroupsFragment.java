package com.householdsplitter.ui.groups;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;

import com.householdsplitter.R;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.databinding.FragmentGroupsBinding;
import com.householdsplitter.databinding.ItemGroupCardBinding;
import com.householdsplitter.ui.MainActivity;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.common.Insets;
import com.householdsplitter.ui.setup.SetupGroupFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Every group, and which one you are in.
 *
 * <p>This is where the drawer's group list went, and a screen suits it better than a menu
 * did. A menu row had space for a name and a tick. A card has space to say that this group
 * has four people and eleven orders behind it, which is what tells "Flat" and "Flat 12"
 * apart when you have not opened either for a month.
 *
 * <p>Two different actions on one row, kept visibly apart: the body of the card switches
 * you into that group, and the pencil opens it for editing. Switching is the common one, so
 * it gets the large target; editing is the deliberate one, so it gets an explicit button
 * rather than a long press nobody discovers.
 */
public class GroupsFragment extends BaseFragment {

    /** A tab, not a step down: siblings fade through rather than sliding. */
    @Override
    protected boolean isTopLevel() {
        return true;
    }

    private FragmentGroupsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentGroupsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Insets.padTop(binding.toolbar);

        binding.newGroupFab.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(
                        R.id.setupGroupFragment, SetupGroupFragment.argsForAnotherGroup()));
    }

    /**
     * Rebuilt on every return rather than observed.
     *
     * <p>A group's name, its people and its orders are all changed on other screens, and
     * the counts here are derived from three tables. Watching all of that would be a lot of
     * machinery for a screen somebody looks at for two seconds; reading it fresh each time
     * it appears is both simpler and incapable of being stale.
     */
    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        long current = locator().currentHouseholdId();
        locator().executors().diskIO().execute(() -> {
            List<Household> groups = locator().database().householdDao().getAllSync();
            List<Summary> summaries = new ArrayList<>(groups.size());
            for (Household group : groups) {
                summaries.add(Summary.of(locator(), group));
            }
            locator().executors().mainThread().execute(() -> render(summaries, current));
        });
    }

    private void render(List<Summary> summaries, long current) {
        if (binding == null) {
            return;
        }
        binding.groupList.removeAllViews();
        for (Summary summary : summaries) {
            binding.groupList.addView(card(summary, summary.group.id == current));
        }
    }

    private View card(Summary summary, boolean isCurrent) {
        ItemGroupCardBinding row = ItemGroupCardBinding.inflate(
                getLayoutInflater(), binding.groupList, false);
        row.groupName.setText(summary.group.name);
        row.groupInitial.setText(summary.group.name.isEmpty()
                ? "?" : summary.group.name.substring(0, 1).toUpperCase());
        // A disc behind it. Without one the letter floats in the corner of the card with
        // nothing to belong to, which is the single clearest tell that a screen was laid
        // out and never looked at.
        android.graphics.drawable.GradientDrawable disc =
                new android.graphics.drawable.GradientDrawable();
        disc.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        disc.setColor(com.google.android.material.color.MaterialColors.getColor(
                row.groupInitial, com.google.android.material.R.attr.colorSecondaryContainer));
        row.groupInitial.setBackground(disc);
        row.groupInitial.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                row.groupInitial,
                com.google.android.material.R.attr.colorOnSecondaryContainer));
        row.groupMeta.setText(summary.describe(requireContext(),
                locator().settings().currencySymbol(), locator().settings().locale()));

        // A badge as well as a card outline, so which group you are in never depends on
        // noticing a one pixel border.
        row.currentBadge.setVisibility(isCurrent ? View.VISIBLE : View.GONE);
        row.getRoot().setStrokeWidth(isCurrent
                ? getResources().getDimensionPixelSize(R.dimen.stroke_width) * 2
                : getResources().getDimensionPixelSize(R.dimen.stroke_width));

        row.cardBody.setOnClickListener(v -> {
            if (isCurrent) {
                // Already here. Opening it for editing is the only thing left to mean.
                openEditor(summary.group.id);
                return;
            }
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchToGroup(summary.group);
            }
        });
        row.editButton.setOnClickListener(v -> openEditor(summary.group.id));
        return row.getRoot();
    }

    private void openEditor(long householdId) {
        NavHostFragment.findNavController(this).navigate(
                R.id.editGroupFragment, EditGroupFragment.argsFor(householdId));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
