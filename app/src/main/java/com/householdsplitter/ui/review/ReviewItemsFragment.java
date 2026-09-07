package com.householdsplitter.ui.review;

import android.graphics.Canvas;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.householdsplitter.R;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.core.parse.model.Reconciliation;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.databinding.FragmentReviewItemsBinding;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.common.Insets;
import com.householdsplitter.ui.common.StateColors;
import com.householdsplitter.ui.parsing.ParsingArgs;

import java.util.List;

/**
 * S6, review items. SPEC 7.6.
 *
 * <p>PROMPT hard rule 7: every parse lands here, editable, and the reconciliation banner is
 * advisory. Nothing on this screen blocks on the parser's own confidence. The only thing
 * that does block is a row with no name or no price (SPEC 7.6.9), because that cannot be
 * split.
 */
public class ReviewItemsFragment extends BaseFragment {

    private FragmentReviewItemsBinding binding;
    private ReviewItemsViewModel model;
    private ReviewItemAdapter adapter;
    private long orderId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentReviewItemsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        orderId = getArguments() == null ? 0L : getArguments().getLong(ParsingArgs.ARG_ORDER_ID);
        model = viewModel(ReviewItemsViewModel.class);

        Insets.padTop(binding.toolbar);
        Insets.padBottom(binding.footer);

        CurrencyFormat money = new CurrencyFormat(
                locator().settings().currencySymbol(), locator().settings().locale());

        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        adapter = new ReviewItemAdapter(this::openEditSheet, money);
        binding.itemList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.itemList.setAdapter(adapter);

        attachSwipeToDelete();

        model.items().observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
            boolean empty = items == null || items.isEmpty();
            // SPEC 11.11: zero items is a clear message, not a crash.
            binding.emptyMessage.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.itemCount.setText(getResources().getQuantityString(
                    R.plurals.item_count, items == null ? 0 : items.size(),
                    items == null ? 0 : items.size()));
        });

        // SPEC 7.6.2 and 8.9.4: prominent, and advisory only.
        model.reconciliation().observe(getViewLifecycleOwner(), reconciliation ->
                renderBanner(reconciliation, money));

        binding.addItemButton.setOnClickListener(v -> openEditSheet(model.newBlankItem()));

        binding.continueButton.setOnClickListener(v -> {
            List<Integer> blocking = model.blockingPositions();
            if (!blocking.isEmpty()) {
                // SPEC 7.6.9: scroll the first offender into view and say why.
                binding.itemList.smoothScrollToPosition(blocking.get(0));
                Snackbar.make(binding.getRoot(),
                        getResources().getQuantityString(R.plurals.blocking_items,
                                blocking.size(), blocking.size()),
                        Snackbar.LENGTH_LONG).show();
                return;
            }
            Bundle args = new Bundle();
            args.putLong(ParsingArgs.ARG_ORDER_ID, orderId);
            NavHostFragment.findNavController(this).navigate(R.id.orderDetailsFragment, args);
        });
    }

    private void renderBanner(Reconciliation reconciliation, CurrencyFormat money) {
        if (binding == null) {
            return;
        }
        if (reconciliation == null) {
            binding.reconciliationPanel.setVisibility(View.GONE);
            return;
        }
        binding.reconciliationPanel.setVisibility(View.VISIBLE);

        final StateColors.State state;
        final int icon;
        boolean offerHelp = false;
        if (reconciliation.statedSubtotalCents() == 0L && reconciliation.statedTotalCents() == 0L) {
            binding.reconciliationBanner.setText(R.string.reconcile_no_totals);
            state = StateColors.State.NEUTRAL;
            icon = R.drawable.ic_alert_circle;
        } else if (reconciliation.subtotalMatches()) {
            binding.reconciliationBanner.setText(getString(R.string.reconcile_items_match,
                    money.format(reconciliation.itemsSubtotalCents())));
            state = StateColors.State.SUCCESS;
            icon = R.drawable.ic_check_circle;
        } else {
            binding.reconciliationBanner.setText(getString(R.string.reconcile_items_off,
                    money.format(reconciliation.itemsSubtotalCents()),
                    money.format(reconciliation.statedSubtotalCents()),
                    money.formatSigned(reconciliation.subtotalDeltaCents())));
            // Advisory, never a gate (SPEC 8.9.4), so it is a warning rather than an error.
            state = StateColors.State.WARNING;
            icon = R.drawable.ic_alert_circle;
            offerHelp = true;
        }

        int onContainer = StateColors.onContainer(requireContext(), state);
        binding.reconciliationPanel.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        StateColors.container(requireContext(), state)));
        binding.reconciliationBanner.setTextColor(onContainer);
        binding.unitHint.setTextColor(onContainer);
        binding.addDifferenceButton.setTextColor(onContainer);
        binding.addScreenshotsButton.setTextColor(onContainer);
        binding.reconciliationBanner.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(
                binding.reconciliationBanner,
                android.content.res.ColorStateList.valueOf(onContainer));

        // SPEC 8.5.4: a unit count, offered as a possible explanation and nothing else.
        int units = model.deliveredUnitCount();
        int rows = model.itemCount();
        boolean showHint = offerHelp && units > 0 && units > rows;
        binding.unitHint.setVisibility(showHint ? View.VISIBLE : View.GONE);
        if (showHint) {
            binding.unitHint.setText(getString(R.string.reconcile_unit_hint, units, rows));
        }

        binding.reconciliationActions.setVisibility(offerHelp ? View.VISIBLE : View.GONE);
        if (offerHelp) {
            long unaccounted = model.unaccountedCents();
            binding.addDifferenceButton.setEnabled(unaccounted != 0L);
            binding.addDifferenceButton.setOnClickListener(v -> model.addDifferenceAsItem(
                    getString(R.string.reconcile_difference_name), added -> {
                        if (added != null && binding != null) {
                            Snackbar.make(binding.getRoot(),
                                    getString(R.string.reconcile_difference_added,
                                            money.format(unaccounted)),
                                    Snackbar.LENGTH_LONG).show();
                        }
                    }));
            binding.addScreenshotsButton.setOnClickListener(v -> {
                Bundle args = new Bundle();
                args.putLong(ParsingArgs.ARG_ORDER_ID, orderId);
                NavHostFragment.findNavController(this).navigate(R.id.importFragment, args);
            });
        }
    }

    private void openEditSheet(LineItem item) {
        ItemEditSheet.forItem(item, locator().settings().currencySymbol(),
                new ItemEditSheet.Listener() {
            @Override
            public void onSaved(LineItem saved) {
                model.save(saved);
            }

            @Override
            public void onDeleted(LineItem deleted) {
                deleteWithUndo(deleted);
            }

            @Override
            public void onSplitByQuantity(LineItem toSplit) {
                model.splitByQuantity(toSplit.id, rows -> {
                    if (binding == null || rows == null || rows < 2) {
                        return;
                    }
                    Snackbar.make(binding.getRoot(), getResources().getQuantityString(
                            R.plurals.split_done, rows, rows), Snackbar.LENGTH_LONG).show();
                });
            }
        }).show(getChildFragmentManager(), "edit-item");
    }

    /** SPEC 7.6.8: swipe left to delete, Undo lasting at least five seconds. */
    private void attachSwipeToDelete() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.START) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder source,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                deleteWithUndo(adapter.itemAt(viewHolder.getBindingAdapterPosition()));
            }

            @Override
            public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent,
                                    @NonNull RecyclerView.ViewHolder holder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                holder.itemView.setAlpha(1f - Math.min(1f, Math.abs(dX) / holder.itemView.getWidth()));
                super.onChildDraw(canvas, parent, holder, dX, dY, actionState, isCurrentlyActive);
            }
        }).attachToRecyclerView(binding.itemList);
    }

    private void deleteWithUndo(LineItem item) {
        model.delete(item);
        Snackbar.make(binding.getRoot(), R.string.item_deleted, 6000)
                .setAction(R.string.action_undo, v -> model.undoDelete())
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.itemList.setAdapter(null);
        binding = null;
    }
}
