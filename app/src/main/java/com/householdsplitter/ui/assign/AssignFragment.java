package com.householdsplitter.ui.assign;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.householdsplitter.R;
import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.core.money.QuantityBreakdown;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.relation.LineItemWithAssignments;
import com.householdsplitter.databinding.FragmentAssignBinding;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.common.Insets;
import com.householdsplitter.ui.common.MemberPalette;
import com.householdsplitter.ui.common.StateColors;
import com.householdsplitter.ui.parsing.ParsingArgs;

import java.util.List;
import java.util.Map;

/** S9, assignment. SPEC 7.9. */
public class AssignFragment extends BaseFragment {

    private FragmentAssignBinding binding;
    private AssignViewModel model;
    private CurrencyFormat money;
    private long orderId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAssignBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        orderId = getArguments() == null ? 0L : getArguments().getLong(ParsingArgs.ARG_ORDER_ID);
        model = viewModel(AssignViewModel.class);

        Insets.padTop(binding.toolbar);
        Insets.padBottom(binding.footer);
        money = new CurrencyFormat(locator().settings().currencySymbol(),
                locator().settings().locale());

        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        binding.toolbar.inflateMenu(R.menu.menu_assign);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_all_common) {
                confirmAssignRemaining();
                return true;
            }
            if (item.getItemId() == R.id.action_jump_to) {
                showJumpSheet();
                return true;
            }
            if (item.getItemId() == R.id.action_accept_all) {
                model.acceptAllSuggestions(applied -> {
                    if (binding == null) {
                        return;
                    }
                    if (applied == null || applied == 0) {
                        com.google.android.material.snackbar.Snackbar.make(binding.getRoot(),
                                R.string.assign_no_suggestions,
                                com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    com.google.android.material.snackbar.Snackbar.make(binding.getRoot(),
                            getResources().getQuantityString(
                                    R.plurals.assign_suggestions_applied, applied, applied),
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
                });
                return true;
            }
            if (item.getItemId() == R.id.action_remove_item) {
                removeCurrentItem();
                return true;
            }
            if (item.getItemId() == R.id.action_bulk) {
                Bundle args = new Bundle();
                args.putLong(ParsingArgs.ARG_ORDER_ID, orderId);
                NavHostFragment.findNavController(this).navigate(R.id.bulkAssignFragment, args);
                return true;
            }
            return false;
        });

        model.bundle().observe(getViewLifecycleOwner(), bundle -> {
            model.restorePosition(bundle);
            render();
        });
        model.index().observe(getViewLifecycleOwner(), at -> render());
        model.selection().observe(getViewLifecycleOwner(), selection -> render());
        model.suggested().observe(getViewLifecycleOwner(), value -> render());
        model.ruleNote().observe(getViewLifecycleOwner(), note -> render());

        // SPEC 7.9.3.1: one tap, advances immediately.
        binding.commonButton.setOnClickListener(v -> {
            model.selectEveryone();
            confirm();
        });
        binding.samePreviousButton.setOnClickListener(v -> model.copyPrevious());
        binding.justSomeoneButton.setOnClickListener(this::showJustMenu);
        binding.excludeButton.setOnClickListener(v ->
                model.excludeAndAdvance(finished -> finishIfDone(finished)));
        binding.nextButton.setOnClickListener(v -> confirm());
        binding.backButton.setOnClickListener(v -> model.back());
    }

    private void confirm() {
        model.confirmAndAdvance(this::finishIfDone);
    }

    private void finishIfDone(Boolean finished) {
        if (!Boolean.TRUE.equals(finished) || binding == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putLong(ParsingArgs.ARG_ORDER_ID, orderId);
        // The summary is where the order ends, so clear the wizard behind it and leave Home
        // underneath. Otherwise Back walks the user backwards through import, review,
        // totals and participants, which is a route nobody wants to take by accident.
        NavOptions options = new NavOptions.Builder()
                .setPopUpTo(R.id.homeFragment, false)
                .build();
        NavHostFragment.findNavController(this).navigate(R.id.summaryFragment, args, options);
    }

    private void render() {
        if (binding == null) {
            return;
        }
        LineItemWithAssignments item = model.currentItem();
        List<LineItemWithAssignments> all = model.items();
        Integer at = model.index().getValue();
        int position = at == null ? 0 : at;

        if (item == null) {
            binding.itemName.setText(R.string.assign_nothing_to_do);
            binding.chipGroup.removeAllViews();
            binding.nextButton.setEnabled(false);
            return;
        }

        // SPEC 7.9.2: a linear progress bar and "Item N of M".
        binding.progressBar.setMax(all.size());
        binding.progressBar.setProgress(position + 1);
        binding.progressLabel.setText(getString(R.string.assign_progress, position + 1, all.size()));

        binding.itemName.setText(item.item.name);
        binding.itemPrice.setText(money.format(item.item.lineTotalCents));
        binding.itemQuantity.setVisibility(item.item.quantity > 1 ? View.VISIBLE : View.GONE);
        String perUnit = QuantityBreakdown.describe(item.item.quantity,
                item.item.lineTotalCents, money, null);
        binding.itemQuantity.setText(perUnit == null
                ? getString(R.string.assign_quantity, item.item.quantity)
                : getString(R.string.assign_quantity_each, item.item.quantity, perUnit));

        // SPEC 7.9.3.2: hidden on item 1.
        binding.samePreviousButton.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
        // SPEC 7.9.9: available on every item after the first.
        binding.backButton.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);

        renderChips(item);
        renderReadout(item);

        binding.suggestionLabel.setVisibility(
                Boolean.TRUE.equals(model.suggested().getValue()) ? View.VISIBLE : View.GONE);

        boolean convertedToCommon = model.resolvedScope() == Scope.COMMON
                && !model.participants().isEmpty();
        binding.commonNote.setVisibility(convertedToCommon ? View.VISIBLE : View.GONE);
        binding.commonNote.setTextColor(
                StateColors.content(requireContext(), StateColors.State.SUCCESS));

        // A standing rule changed who is on this item. Saying so is the whole point: the
        // user can see it and put the person back with one tap.
        AssignViewModel.RuleNote note = model.ruleNote().getValue();
        binding.ruleNote.setVisibility(note == null ? View.GONE : View.VISIBLE);
        if (note != null) {
            binding.ruleNote.setText(ruleNoteText(note));
        }
        binding.ruleNote.setTextColor(
                StateColors.content(requireContext(), StateColors.State.WARNING));

        binding.nextButton.setEnabled(model.canAdvance());
    }

    private void renderChips(LineItemWithAssignments item) {
        binding.chipGroup.removeAllViews();
        Map<Long, Integer> selection = model.selection().getValue();
        for (Member member : model.participants()) {
            Chip chip = new Chip(requireContext());
            Integer shares = selection == null ? null : selection.get(member.id);
            boolean selected = shares != null;

            // SPEC 7.9.6: a doubled share reads "x2" on the chip.
            chip.setText(selected && shares > 1 ? member.name + "  x" + shares : member.name);
            chip.setCheckable(true);
            chip.setChecked(selected);
            chip.setChipIconVisible(false);
            chip.setMinHeight(dp(48));
            chip.setEnsureMinTouchTargetSize(true);
            // A selected chip is filled with that member's own colour, so who is on an item
            // is legible at a glance and matches their avatar everywhere else. The name is
            // always on the chip, so the colour is reinforcement rather than the signal.
            int memberColor = MemberPalette.resolve(requireContext(), member.colorHex);
            if (selected) {
                chip.setChipBackgroundColor(ColorStateList.valueOf(memberColor));
                chip.setTextColor(androidx.core.content.ContextCompat.getColor(
                        requireContext(), R.color.on_member_avatar));
                chip.setChipStrokeColor(ColorStateList.valueOf(memberColor));
            } else {
                chip.setChipStrokeColor(ColorStateList.valueOf(memberColor));
                chip.setChipStrokeWidth(getResources().getDimension(R.dimen.stroke_width));
            }
            // Not colour alone: the name is on the chip and in its description.
            chip.setContentDescription(member.name + (selected
                    ? ", selected" + (shares > 1 ? ", double share" : "") : ", not selected"));

            chip.setOnClickListener(v -> model.toggle(member.id));
            chip.setOnLongClickListener(v -> {
                model.cycleShares(member.id);
                return true;
            });
            binding.chipGroup.addView(chip);
        }
    }

    /** SPEC 7.9.5: the live per-head readout. */
    private void renderReadout(LineItemWithAssignments item) {
        long[] shares = model.previewShares();
        if (shares.length == 0) {
            binding.readout.setText(R.string.assign_pick_someone);
            StateColors.applyContainer(binding.readout, binding.readout,
                    StateColors.State.NEUTRAL);
            return;
        }
        // Once somebody is chosen the readout becomes the confirmed figure, so it takes the
        // success container: the consequence of the choice is visible without reading.
        StateColors.applyContainer(binding.readout, binding.readout, StateColors.State.SUCCESS);
        boolean allEqual = true;
        for (long share : shares) {
            if (share != shares[0]) {
                allEqual = false;
                break;
            }
        }
        if (allEqual) {
            binding.readout.setText(getString(R.string.assign_each, money.format(shares[0])));
        } else {
            StringBuilder out = new StringBuilder();
            for (long share : shares) {
                if (out.length() > 0) {
                    out.append(" / ");
                }
                out.append(money.format(share));
            }
            binding.readout.setText(out.toString());
        }
    }

    /** SPEC 7.9.3.3: a menu of participants for single-person assignment. */
    private void showJustMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        List<Member> participants = model.participants();
        for (int i = 0; i < participants.size(); i++) {
            menu.getMenu().add(0, i, i, participants.get(i).name);
        }
        menu.setOnMenuItemClickListener(entry -> {
            model.selectOnly(participants.get(entry.getItemId()).id);
            return true;
        });
        menu.show();
    }

    /** SPEC 7.9.10: confirmation naming the count. */
    /**
     * Removes the row on screen, with an Undo that puts back its answers too.
     *
     * <p>No confirmation dialog: Undo is the better answer for something reversible, and a
     * dialog on every removal would be in the way of the case this exists for, which is
     * clearing out rows the reader invented.
     */
    /** The rule note as a sentence, assembled from the string resources. */
    private String ruleNoteText(AssignViewModel.RuleNote note) {
        boolean off = !note.removed().isEmpty();
        boolean on = !note.added().isEmpty();
        if (off && on) {
            return getString(R.string.rules_note_both, note.removed(), note.added());
        }
        return off
                ? getString(R.string.rules_note_off, note.removed())
                : getString(R.string.rules_note_on, note.added());
    }

    private void removeCurrentItem() {
        if (model.items().size() <= 1) {
            // Removing the last row would leave an order with nothing in it, which the
            // summary cannot total. Deleting the order is the honest action there.
            Snackbar.make(binding.getRoot(), R.string.assign_remove_last,
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        LineItemWithAssignments current = model.currentItem();
        String name = current == null ? "" : current.item.name;
        model.removeCurrentItem(result -> {
            if (binding == null) {
                return;
            }
            if (!result.isOk()) {
                Snackbar.make(binding.getRoot(), R.string.assign_remove_failed,
                        Snackbar.LENGTH_LONG).show();
                return;
            }
            Snackbar.make(binding.getRoot(), getString(R.string.assign_removed, name),
                            Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_undo, v -> model.undoRemove(restored -> {
                    }))
                    .show();
        });
    }

    private void confirmAssignRemaining() {
        int count = model.remainingUnassignedFromHere();
        if (count == 0) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.assign_all_common_title)
                .setMessage(getResources().getQuantityString(
                        R.plurals.assign_all_common_message, count, count))
                .setPositiveButton(R.string.action_continue, (dialog, which) ->
                        model.assignRemainingAsCommon(changed -> {
                        }))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** SPEC 7.9.11: every item with its current assignment, tappable. */
    private void showJumpSheet() {
        List<LineItemWithAssignments> all = model.items();
        CharSequence[] labels = new CharSequence[all.size()];
        for (int i = 0; i < all.size(); i++) {
            LineItemWithAssignments row = all.get(i);
            labels[i] = (i + 1) + ". " + row.item.name + "  ("
                    + describe(row) + ")";
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.assign_jump_title)
                .setItems(labels, (dialog, which) -> model.jumpTo(which))
                .show();
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
                for (com.householdsplitter.data.entity.ItemAssignment assignment : row.assignments) {
                    for (Member member : model.participants()) {
                        if (member.id == assignment.memberId) {
                            if (names.length() > 0) {
                                names.append(", ");
                            }
                            names.append(member.name);
                        }
                    }
                }
                return names.toString();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
