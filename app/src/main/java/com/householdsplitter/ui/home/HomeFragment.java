package com.householdsplitter.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.householdsplitter.R;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.data.entity.OrderStatus;
import com.householdsplitter.data.relation.OrderWithMembers;
import com.householdsplitter.databinding.FragmentHomeBinding;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.parsing.ParsingArgs;
import com.householdsplitter.ui.setup.SetupMembersFragment;
import com.householdsplitter.ui.summary.SummaryFragment;

/** S3, Home. SPEC 7.3. */
public class HomeFragment extends BaseFragment {

    private FragmentHomeBinding binding;
    private HomeViewModel model;
    private OrderAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        model = viewModel(HomeViewModel.class);
        CurrencyFormat money = new CurrencyFormat(locator().settings().currencySymbol(),
                locator().settings().locale());

        adapter = new OrderAdapter(new OrderAdapter.Listener() {
            @Override
            public void onOpen(OrderWithMembers order) {
                openOrder(order);
            }

            @Override
            public void onLongPress(OrderWithMembers order, View anchor) {
                showContextMenu(order, anchor);
            }
        }, money);
        binding.orderList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.orderList.setAdapter(adapter);

        model.groupName().observe(getViewLifecycleOwner(), name ->
                binding.toolbar.setTitle(name == null || name.isEmpty()
                        ? getString(R.string.app_name) : name));

        model.members().observe(getViewLifecycleOwner(), members ->
                binding.memberSummary.setText(members == null || members.isEmpty()
                        ? "" : getResources().getQuantityString(
                        R.plurals.member_count, members.size(), members.size())));

        model.orders().observe(getViewLifecycleOwner(), orders -> {
            adapter.submitList(orders);
            boolean empty = orders == null || orders.isEmpty();
            binding.orderList.setVisibility(empty ? View.GONE : View.VISIBLE);
            binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        });

        binding.newOrderFab.setOnClickListener(v -> startNewOrder());
        binding.emptyStateButton.setOnClickListener(v -> startNewOrder());

        binding.toolbar.inflateMenu(R.menu.menu_home);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_members) {
                Bundle args = new Bundle();
                args.putBoolean(SetupMembersFragment.ARG_MANAGE_MODE, true);
                NavHostFragment.findNavController(this).navigate(R.id.setupMembersFragment, args);
                return true;
            }
            if (id == R.id.action_settings) {
                NavHostFragment.findNavController(this).navigate(R.id.settingsFragment);
                return true;
            }
            if (id == R.id.action_export_all) {
                NavHostFragment.findNavController(this).navigate(R.id.settingsFragment);
                return true;
            }
            return false;
        });
    }

    private void startNewOrder() {
        NavHostFragment.findNavController(this).navigate(R.id.importFragment);
    }

    /**
     * SPEC 7.3.4: a draft resumes where it stopped, and anything further along opens the
     * read-only view.
     */
    private void openOrder(OrderWithMembers row) {
        Bundle args = new Bundle();
        args.putLong(ParsingArgs.ARG_ORDER_ID, row.order.id);
        if (row.order.status != OrderStatus.DRAFT) {
            args.putBoolean(SummaryFragment.ARG_READ_ONLY, true);
            NavHostFragment.findNavController(this).navigate(R.id.summaryFragment, args);
            return;
        }
        int destination;
        switch (row.order.draftStep) {
            case REVIEW:
                destination = R.id.reviewItemsFragment;
                break;
            case DETAILS:
                destination = R.id.orderDetailsFragment;
                break;
            case PARTICIPANTS:
                destination = R.id.participantsFragment;
                break;
            case ASSIGN:
                destination = R.id.assignFragment;
                break;
            case SUMMARY:
                destination = R.id.summaryFragment;
                break;
            default:
                destination = R.id.reviewItemsFragment;
                break;
        }
        NavHostFragment.findNavController(this).navigate(destination, args);
    }

    /** SPEC 7.3.6. */
    private void showContextMenu(OrderWithMembers row, View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 0, 0, R.string.action_rename);
        menu.getMenu().add(0, 1, 1, R.string.action_delete);
        menu.setOnMenuItemClickListener(entry -> {
            if (entry.getItemId() == 0) {
                showRenameDialog(row);
            } else {
                confirmDelete(row);
            }
            return true;
        });
        menu.show();
    }

    private void showRenameDialog(OrderWithMembers row) {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_single_input, null, false);
        com.google.android.material.textfield.TextInputEditText field =
                content.findViewById(R.id.dialogInput);
        com.google.android.material.textfield.TextInputLayout layout =
                content.findViewById(R.id.dialogInputLayout);
        layout.setHint(getString(R.string.hint_order_label));
        field.setText(row.order.label);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.action_rename)
                .setView(content)
                .setPositiveButton(R.string.action_save, (dialog, which) -> model.rename(
                        row.order.id,
                        field.getText() == null ? row.order.label : field.getText().toString()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmDelete(OrderWithMembers row) {
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.delete_order_title, row.order.label))
                .setMessage(R.string.delete_order_message)
                .setPositiveButton(R.string.action_delete,
                        (dialog, which) -> model.delete(row.order.id))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.orderList.setAdapter(null);
        binding = null;
    }
}
