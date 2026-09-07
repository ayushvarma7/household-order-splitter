package com.householdsplitter.ui.review;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.householdsplitter.R;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.databinding.SheetEditItemBinding;
import com.householdsplitter.ui.common.CurrencyInput;

/** SPEC 7.6.5: the edit sheet. */
public class ItemEditSheet extends BottomSheetDialogFragment {

    public interface Listener {
        void onSaved(LineItem item);

        void onDeleted(LineItem item);

        /** SPEC 11.1: break a row of quantity N into N rows of equal price. */
        void onSplitByQuantity(LineItem item);

        /** Show the screenshot this row was read from, with the row ringed. */
        void onViewOnScreenshot(LineItem item);
    }

    private SheetEditItemBinding binding;
    private LineItem item;
    private Listener listener;
    private String currencySymbol;

    public static ItemEditSheet forItem(LineItem item, String currencySymbol, Listener listener) {
        ItemEditSheet sheet = new ItemEditSheet();
        sheet.item = item;
        sheet.currencySymbol = currencySymbol;
        sheet.listener = listener;
        return sheet;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        binding = SheetEditItemBinding.inflate(getLayoutInflater());
        dialog.setContentView(binding.getRoot());

        if (item == null) {
            dismiss();
            return dialog;
        }

        binding.nameInput.setText(item.name);
        binding.quantityInput.setText(String.valueOf(item.quantity));
        CurrencyInput.attach(binding.priceInput);
        if (currencySymbol != null) {
            ((com.google.android.material.textfield.TextInputLayout)
                    binding.priceInput.getParent().getParent()).setPrefixText(currencySymbol);
        }
        CurrencyInput.writeCents(binding.priceInput, item.lineTotalCents);

        // SPEC 7.6.5: the original OCR text, read only, so a wrong parse can be diagnosed.
        boolean hasRaw = item.rawOcrText != null && !item.rawOcrText.trim().isEmpty();
        binding.sourceGroup.setVisibility(hasRaw ? View.VISIBLE : View.GONE);
        binding.sourceText.setText(item.rawOcrText);

        binding.saveButton.setOnClickListener(v -> {
            String name = binding.nameInput.getText() == null
                    ? "" : binding.nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                binding.nameLayout.setError(getString(R.string.error_name_empty));
                return;
            }
            item.name = name;
            item.quantity = readQuantity();
            // SPEC 7.6.6: converted to cents with no floating point anywhere in the path.
            item.lineTotalCents = CurrencyInput.readCents(binding.priceInput, item.lineTotalCents);
            listener.onSaved(item);
            dismiss();
        });

        binding.deleteButton.setOnClickListener(v -> {
            listener.onDeleted(item);
            dismiss();
        });

        // Only a row the reader found has somewhere to point at. A row typed by hand, or
        // added to close a reconciliation gap, has no region and no button.
        boolean hasRegion = item.hasSourceRegion();
        binding.viewOnScreenshotButton.setVisibility(hasRegion ? View.VISIBLE : View.GONE);
        if (hasRegion) {
            binding.viewOnScreenshotButton.setOnClickListener(v -> {
                listener.onViewOnScreenshot(item);
                dismiss();
            });
        }

        // SPEC 11.1. Worth offering only when there is something to split: a two-pack
        // charged as one row cannot be answered for when its halves are for two people.
        boolean splittable = item.quantity > 1;
        binding.splitButton.setVisibility(splittable ? View.VISIBLE : View.GONE);
        if (splittable) {
            binding.splitButton.setText(
                    getString(R.string.action_split_rows, item.quantity));
            binding.splitButton.setOnClickListener(v -> {
                listener.onSplitByQuantity(item);
                dismiss();
            });
        }

        return dialog;
    }

    private int readQuantity() {
        CharSequence text = binding.quantityInput.getText();
        if (text == null || text.toString().trim().isEmpty()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(text.toString().trim()));
        } catch (NumberFormatException notANumber) {
            return 1;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
