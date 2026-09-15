package com.householdsplitter.ui.home;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.householdsplitter.core.parse.StoreKind;
import com.householdsplitter.databinding.ItemStoreOptionBinding;
import com.householdsplitter.databinding.SheetStorePickerBinding;

/**
 * Asks which store an order came from, before any screenshot is imported.
 *
 * <p>Why the user is asked rather than the app detecting it: the two layouts are near
 * identical in structure, both a thumbnail, a wrapping title, a quantity line and a
 * right-aligned price, and what distinguishes them is the summary block wording, which is
 * the most fragile part of either vocabulary. A detector would therefore rest the choice of
 * parser on the least reliable evidence on the page, and getting it wrong does not fail
 * loudly: it produces a plausible number that is wrong. Told the store, each vocabulary is
 * free to be strict and to flag what it cannot account for.
 *
 * <p>It is one tap, on the store used last time, at the point where the user already knows
 * the answer because they are about to open that store's app.
 */
public class StorePickerSheet extends BottomSheetDialogFragment {

    public interface Listener {
        void onStoreChosen(StoreKind store);
    }

    private SheetStorePickerBinding binding;
    private StoreKind lastUsed;
    private Listener listener;

    public static StorePickerSheet withLastUsed(StoreKind lastUsed, Listener listener) {
        StorePickerSheet sheet = new StorePickerSheet();
        sheet.lastUsed = lastUsed;
        sheet.listener = listener;
        return sheet;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        binding = SheetStorePickerBinding.inflate(getLayoutInflater());
        dialog.setContentView(binding.getRoot());

        for (StoreKind store : ordered()) {
            binding.storeOptions.addView(optionFor(store));
        }
        return dialog;
    }

    /** The store used last time comes first, so the common case is the nearest tap. */
    private StoreKind[] ordered() {
        StoreKind first = lastUsed == null ? StoreKind.WALMART : lastUsed;
        StoreKind[] all = StoreKind.values();
        StoreKind[] result = new StoreKind[all.length];
        result[0] = first;
        int index = 1;
        for (StoreKind store : all) {
            if (store != first) {
                result[index++] = store;
            }
        }
        return result;
    }

    private View optionFor(StoreKind store) {
        ItemStoreOptionBinding row =
                ItemStoreOptionBinding.inflate(LayoutInflater.from(requireContext()),
                        binding.storeOptions, false);
        row.storeName.setText(store.displayName());
        row.lastUsed.setVisibility(store == lastUsed ? View.VISIBLE : View.GONE);
        row.getRoot().setOnClickListener(v -> {
            if (listener != null) {
                listener.onStoreChosen(store);
            }
            dismiss();
        });
        return row.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
