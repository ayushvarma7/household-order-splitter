package com.householdsplitter.ui.details;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;

import com.householdsplitter.R;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.core.money.TipCalculator;
import com.householdsplitter.core.parse.Reconciler;
import com.householdsplitter.core.parse.model.OrderField;
import com.householdsplitter.core.parse.model.ParsedAdjustments;
import com.householdsplitter.core.parse.model.Reconciliation;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.relation.LineItemWithAssignments;
import com.householdsplitter.data.relation.OrderBundle;
import com.householdsplitter.databinding.FragmentOrderDetailsBinding;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.common.Insets;
import com.householdsplitter.ui.common.StateColors;
import com.householdsplitter.ui.common.CurrencyInput;
import com.householdsplitter.ui.parsing.ParsingArgs;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * S7, order details and adjustments. SPEC 7.7.
 *
 * <p>SPEC 7.7.4: a non-zero reconciliation delta is a warning and never blocks, because
 * Walmart occasionally prints figures the line items cannot reproduce. It is shown again on
 * the summary (SPEC 7.10.3).
 */
public class OrderDetailsFragment extends BaseFragment {

    private FragmentOrderDetailsBinding binding;
    private OrderDetailsViewModel model;
    private CurrencyFormat money;
    private Order current;
    /** "Round up" means to the next whole currency unit. */
    private static final long ROUND_UP_STEP_CENTS = 100L;

    private long itemsSubtotalCents;
    private long orderDateMillis;
    /**
     * True once the fields hold the parsed values. Writing them programmatically fires the
     * same watchers a user's typing does, and treating that as an edit would clear every
     * marker before the screen had even been looked at.
     */
    private boolean bound;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentOrderDetailsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        model = viewModel(OrderDetailsViewModel.class);

        Insets.padTop(binding.toolbar);
        Insets.padBottom(binding.footer);
        money = new CurrencyFormat(locator().settings().currencySymbol(),
                locator().settings().locale());

        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        // SPEC 7.7.2: a field parsed from a screenshot shows a marker, and loses it the
        // moment the user edits it. The marker is a claim about where the number came from,
        // so it has to stop being made as soon as that stops being true.
        EditText[] fields = moneyFields();
        OrderField[] markers = moneyFieldMarkers();
        for (int i = 0; i < fields.length; i++) {
            final OrderField field = markers[i];
            CurrencyInput.attach(fields[i]);
            CurrencyInput.onChange(fields[i], () -> {
                if (bound && field != null) {
                    model.markEdited(field);
                    refreshMarkers();
                }
                refreshReconciliation();
            });
        }
        // The prefix shows whichever symbol the user chose in Settings (SPEC 7.14.3),
        // rather than a symbol baked into the layout.
        for (com.google.android.material.textfield.TextInputLayout layout : moneyLayouts()) {
            layout.setPrefixText(money.symbol());
        }

        binding.dateButton.setOnClickListener(v -> pickDate());

        // SPEC 7.7.5: zero adjustments collapse behind a link rather than showing as
        // a row of empty fields.
        binding.addAdjustmentLink.setOnClickListener(v -> {
            binding.adjustmentGroup.setVisibility(View.VISIBLE);
            binding.addAdjustmentLink.setVisibility(View.GONE);
        });

        model.bundle().observe(getViewLifecycleOwner(), bundle -> {
            if (bundle == null || bundle.order == null) {
                return;
            }
            if (current == null) {
                bind(bundle);
            }
            itemsSubtotalCents = itemsSubtotal(bundle);
            refreshReconciliation();
        });

        wireTipShortcuts();

        binding.continueButton.setOnClickListener(v -> {
            persist();
            Bundle args = new Bundle();
            args.putLong(ParsingArgs.ARG_ORDER_ID, model.orderId());
            NavHostFragment.findNavController(this).navigate(R.id.participantsFragment, args);
        });
    }

    /**
     * The tip shortcuts.
     *
     * <p>Each one writes into the tip field rather than into a separate piece of state, so
     * the field remains the single answer to "what is the tip" and a tapped percentage can
     * be edited afterwards like any typed figure.
     *
     * <p>The percentage base is the subtotal before tax. Which subtotal is a real question,
     * because the screen holds two: the one printed on the bill and the one the rows add up
     * to. The printed one is used when there is one, since that is the figure the
     * restaurant charged for food, and the rows fall back in when the bill did not state it.
     */
    private void wireTipShortcuts() {
        binding.tip15.setOnClickListener(v -> applyPercentTip(15));
        binding.tip18.setOnClickListener(v -> applyPercentTip(18));
        binding.tip20.setOnClickListener(v -> applyPercentTip(20));
        binding.tipClear.setOnClickListener(v -> CurrencyInput.writeCents(binding.tipInput, 0L));
        binding.tipRoundUp.setOnClickListener(v -> {
            long topUp = TipCalculator.toRoundTotal(totalWithoutTip(), ROUND_UP_STEP_CENTS);
            CurrencyInput.writeCents(binding.tipInput, topUp);
            announceTip(topUp);
        });
    }

    private void applyPercentTip(int percent) {
        long tip = TipCalculator.ofSubtotal(tipBaseCents(), percent);
        CurrencyInput.writeCents(binding.tipInput, tip);
        announceTip(tip);
    }

    /** What a percentage is taken on: the printed subtotal, or the rows if none was read. */
    private long tipBaseCents() {
        long printed = CurrencyInput.readCents(binding.subtotalInput, 0L);
        return printed > 0L ? printed : itemsSubtotalCents;
    }

    /** Everything settled so far with the tip left out, which is what gets rounded up. */
    private long totalWithoutTip() {
        return tipBaseCents()
                + CurrencyInput.readCents(binding.taxInput, 0L)
                + CurrencyInput.readCents(binding.deliveryInput, 0L)
                + CurrencyInput.readCents(binding.otherFeeInput, 0L)
                - CurrencyInput.readCents(binding.discountInput, 0L);
    }

    private void announceTip(long tipCents) {
        int tenths = TipCalculator.percentTenthsOf(tipCents, tipBaseCents());
        String percentage = tenths < 0
                ? "" : " (" + (tenths / 10) + "." + (tenths % 10) + "%)";
        binding.tipLayout.announceForAccessibility(
                getString(R.string.tip_set, money.format(tipCents)) + percentage);
    }

    private void bind(OrderBundle bundle) {
        bound = false;
        current = bundle.order;
        orderDateMillis = current.orderDate;

        binding.labelInput.setText(current.label);
        renderDate();

        CurrencyInput.writeCents(binding.subtotalInput, current.statedSubtotalCents);
        CurrencyInput.writeCents(binding.taxInput, current.taxCents);
        CurrencyInput.writeCents(binding.deliveryInput, current.deliveryFeeCents);
        CurrencyInput.writeCents(binding.tipInput, current.tipCents);
        CurrencyInput.writeCents(binding.otherFeeInput, current.otherFeeCents);
        CurrencyInput.writeCents(binding.discountInput, current.discountCents);
        CurrencyInput.writeCents(binding.statedTotalInput, current.statedTotalCents);

        boolean anyAdjustment = current.deliveryFeeCents != 0 || current.tipCents != 0
                || current.otherFeeCents != 0 || current.discountCents != 0;
        binding.adjustmentGroup.setVisibility(anyAdjustment ? View.VISIBLE : View.GONE);
        binding.addAdjustmentLink.setVisibility(anyAdjustment ? View.GONE : View.VISIBLE);

        // SPEC 7.7.2
        refreshMarkers();
        bound = true;
        // Rendered here rather than left to the field watchers. They only fire when an
        // amount actually changes, so an order whose figures are all zero, which is every
        // hand-created order, would otherwise never draw the strip at all.
        refreshReconciliation();
    }

    private String marker(OrderField field) {
        return model.isFromScreenshot(current, field)
                ? getString(R.string.from_screenshot) : null;
    }

    private void renderDate() {
        binding.dateButton.setText(new SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault())
                .format(new java.util.Date(orderDateMillis)));
    }

    private void pickDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(orderDateMillis);
        new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(year, month, day, 0, 0, 0);
            chosen.set(Calendar.MILLISECOND, 0);
            orderDateMillis = chosen.getTimeInMillis();
            model.markEdited(OrderField.ORDER_DATE);
            renderDate();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    /** SPEC 7.7.3: the live strip, green with a tick at zero, red otherwise. */
    private void refreshReconciliation() {
        if (binding == null || current == null) {
            return;
        }
        ParsedAdjustments adjustments = new ParsedAdjustments(
                CurrencyInput.readCents(binding.subtotalInput, 0L),
                CurrencyInput.readCents(binding.taxInput, 0L),
                CurrencyInput.readCents(binding.deliveryInput, 0L),
                CurrencyInput.readCents(binding.tipInput, 0L),
                CurrencyInput.readCents(binding.otherFeeInput, 0L),
                CurrencyInput.readCents(binding.discountInput, 0L),
                CurrencyInput.readCents(binding.statedTotalInput, 0L));

        Reconciliation reconciliation = Reconciler.reconcile(itemsSubtotalCents, adjustments);

        binding.reconcileLine.setText(getString(R.string.reconcile_equation,
                money.format(reconciliation.computedTotalCents()),
                money.format(reconciliation.statedTotalCents())));

        boolean matches = reconciliation.totalMatches();
        // SPEC 7.7.4: a delta warns, it never blocks, so it is styled as a warning rather
        // than an error, and it carries an icon as well as a colour.
        StateColors.State state = matches
                ? StateColors.State.SUCCESS : StateColors.State.WARNING;
        binding.reconcileDelta.setText(matches
                ? getString(R.string.reconcile_matches)
                : getString(R.string.reconcile_delta,
                        money.formatSigned(reconciliation.totalDeltaCents())));
        binding.reconcileDelta.setCompoundDrawablesRelativeWithIntrinsicBounds(
                matches ? R.drawable.ic_check_circle : R.drawable.ic_alert_circle, 0, 0, 0);

        int onContainer = StateColors.onContainer(requireContext(), state);
        binding.reconcileBanner.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        StateColors.container(requireContext(), state)));
        binding.reconcileDelta.setTextColor(onContainer);
        binding.reconcileLine.setTextColor(onContainer);
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(binding.reconcileDelta,
                android.content.res.ColorStateList.valueOf(onContainer));
    }

    private void persist() {
        if (current == null) {
            return;
        }
        current.label = binding.labelInput.getText() == null
                ? current.label : binding.labelInput.getText().toString().trim();
        current.orderDate = orderDateMillis;
        current.statedSubtotalCents = CurrencyInput.readCents(binding.subtotalInput, 0L);
        current.taxCents = CurrencyInput.readCents(binding.taxInput, 0L);
        current.deliveryFeeCents = CurrencyInput.readCents(binding.deliveryInput, 0L);
        current.tipCents = CurrencyInput.readCents(binding.tipInput, 0L);
        current.otherFeeCents = CurrencyInput.readCents(binding.otherFeeInput, 0L);
        current.discountCents = Math.abs(CurrencyInput.readCents(binding.discountInput, 0L));
        current.statedTotalCents = CurrencyInput.readCents(binding.statedTotalInput, 0L);
        current.draftStep = com.householdsplitter.data.entity.DraftStep.PARTICIPANTS;
        model.save(current);
    }

    /** Parallel to {@link #moneyFields()}: which marker each field owns, or null. */
    private OrderField[] moneyFieldMarkers() {
        return new OrderField[]{
                OrderField.SUBTOTAL, OrderField.TAX, OrderField.TOTAL,
                OrderField.DELIVERY_FEE, OrderField.TIP, OrderField.OTHER_FEE,
                OrderField.DISCOUNT};
    }

    /** Re-reads every marker, so one that has just been invalidated disappears. */
    private void refreshMarkers() {
        binding.subtotalLayout.setHelperText(marker(OrderField.SUBTOTAL));
        binding.taxLayout.setHelperText(marker(OrderField.TAX));
        binding.statedTotalLayout.setHelperText(marker(OrderField.TOTAL));
    }

    private com.google.android.material.textfield.TextInputLayout[] moneyLayouts() {
        return new com.google.android.material.textfield.TextInputLayout[]{
                binding.subtotalLayout, binding.taxLayout, binding.statedTotalLayout,
                binding.deliveryLayout, binding.tipLayout, binding.otherFeeLayout,
                binding.discountLayout};
    }

    private EditText[] moneyFields() {
        return new EditText[]{
                binding.subtotalInput, binding.taxInput, binding.deliveryInput,
                binding.tipInput, binding.otherFeeInput, binding.discountInput,
                binding.statedTotalInput};
    }

    private static long itemsSubtotal(OrderBundle bundle) {
        long sum = 0L;
        for (LineItemWithAssignments row : bundle.items) {
            if (row.item.scope.isChargeable() || row.item.scope.isUnanswered()) {
                sum += row.item.lineTotalCents;
            }
        }
        return sum;
    }

    @Override
    public void onPause() {
        super.onPause();
        // SPEC 11.14: nothing typed is lost if the process dies here.
        persist();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
