package com.householdsplitter.ui.parsing;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.householdsplitter.R;
import com.householdsplitter.databinding.FragmentParsingBinding;
import com.householdsplitter.ui.common.BaseFragment;

import java.util.ArrayList;

/** S5, parsing. SPEC 7.5. */
public class ParsingFragment extends BaseFragment {

    private FragmentParsingBinding binding;
    private ParsingViewModel model;
    private ArrayList<String> uris;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentParsingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        model = viewModel(ParsingViewModel.class);
        uris = getArguments() == null
                ? new ArrayList<>() : getArguments().getStringArrayList(ParsingArgs.ARG_URIS);
        if (uris == null) {
            uris = new ArrayList<>();
        }

        // SPEC 7.5.1: a determinate bar reading "Reading screenshot N of M".
        model.progress().observe(getViewLifecycleOwner(), progress -> {
            if (progress == null) {
                return;
            }
            binding.progressBar.setMax(Math.max(1, progress.total));
            binding.progressBar.setProgress(progress.current);
            binding.progressLabel.setText(getString(R.string.parsing_progress,
                    Math.min(progress.current + 1, progress.total), progress.total));
        });

        binding.cancelButton.setOnClickListener(v -> {
            model.cancel();
            NavHostFragment.findNavController(this).popBackStack();
        });

        model.orderCreated().observe(getViewLifecycleOwner(), orderId -> {
            if (orderId == null) {
                return;
            }
            Bundle args = new Bundle();
            args.putLong(ParsingArgs.ARG_ORDER_ID, orderId);
            NavOptions options = new NavOptions.Builder()
                    .setPopUpTo(R.id.parsingFragment, true)
                    .build();
            NavHostFragment.findNavController(this)
                    .navigate(R.id.reviewItemsFragment, args, options);
        });

        // SPEC 7.5.3: on failure, offer Retry, Choose different images, or Enter manually.
        model.failure().observe(getViewLifecycleOwner(), message -> {
            if (message == null || binding == null) {
                return;
            }
            binding.progressGroup.setVisibility(View.GONE);
            binding.errorGroup.setVisibility(View.VISIBLE);
            binding.errorMessage.setText(message);
        });

        binding.retryButton.setOnClickListener(v -> {
            binding.errorGroup.setVisibility(View.GONE);
            binding.progressGroup.setVisibility(View.VISIBLE);
            model.retry(uris);
        });
        binding.chooseOthersButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        binding.manualButton.setOnClickListener(v -> model.enterManually());

        // SPEC 8.6.7: warn before importing the same order number twice.
        model.duplicateWarning().observe(getViewLifecycleOwner(), existing -> {
            if (existing == null) {
                return;
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.duplicate_order_title)
                    .setMessage(getString(R.string.duplicate_order_message, existing.label))
                    .setPositiveButton(R.string.action_open_existing, (dialog, which) -> {
                        Bundle args = new Bundle();
                        args.putLong(ParsingArgs.ARG_ORDER_ID, existing.id);
                        NavOptions options = new NavOptions.Builder()
                                .setPopUpTo(R.id.parsingFragment, true).build();
                        NavHostFragment.findNavController(this)
                                .navigate(R.id.reviewItemsFragment, args, options);
                    })
                    .setNegativeButton(R.string.action_import_anyway,
                            (dialog, which) -> model.saveAnyway())
                    .setCancelable(false)
                    .show();
        });

        if (savedInstanceState == null) {
            model.start(uris);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
