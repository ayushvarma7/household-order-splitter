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
import com.householdsplitter.core.parse.Reconciler;
import com.householdsplitter.core.parse.model.OrderField;
import com.householdsplitter.core.parse.model.ParsedAdjustments;
import com.householdsplitter.core.parse.model.Reconciliation;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.relation.LineItemWithAssignments;
import com.householdsplitter.data.relation.OrderBundle;
import com.householdsplitter.databinding.FragmentOrderDetailsBinding;
import com.householdsplitter.ui.common.BaseFragment;
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
    private long itemsSubtotalCents;
    private long orderDateMillis;

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
        money = new CurrencyFormat(locator().settings().currencySymbol(),
                locator().settings().locale());

        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        for (EditText field : moneyFields()) {
            CurrencyInput.attach(field);
            CurrencyInput.onChange(field, this::refreshReconciliation);
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

        binding.continueButton.setOnClickListener(v -> {
            persist();
            Bundle args = new Bundle();
            args.putLong(ParsingArgs.ARG_ORDER_ID, model.orderId());
            NavHostFragment.findNavController(this).navigate(R.id.participantsFragment, args);
        });
    }

    private void bind(OrderBundle bundle) {
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
        binding.subtotalLayout.setHelperText(marker(OrderField.SUBTOTAL));
        binding.taxLayout.setHelperText(marker(OrderField.TAX));
        binding.statedTotalLayout.setHelperText(marker(OrderField.TOTAL));
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

        if (reconciliation.totalMatches()) {
            binding.reconcileDelta.setText(R.string.reconcile_matches);
            binding.reconcileDelta.setTextColor(0xFF2E7D32);
        } else {
            binding.reconcileDelta.setText(getString(R.string.reconcile_delta,
                    money.formatSigned(reconciliation.totalDeltaCents())));
            binding.reconcileDelta.setTextColor(0xFFC62828);
        }
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
