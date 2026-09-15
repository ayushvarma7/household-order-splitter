package com.householdsplitter.ui.home;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.householdsplitter.core.parse.StoreKind;
import com.householdsplitter.prefs.SettingsStore;
import com.householdsplitter.R;
import com.google.android.material.snackbar.Snackbar;
import com.householdsplitter.core.calc.SplitCalculator;
import com.householdsplitter.core.calc.result.SplitResult;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.data.mapper.CalcMapper;
import com.householdsplitter.export.ExportService;
import com.householdsplitter.data.entity.OrderStatus;
import com.householdsplitter.data.relation.OrderWithMembers;
import com.householdsplitter.databinding.FragmentHomeBinding;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.common.Insets;
import com.householdsplitter.ui.parsing.ParsingArgs;
import com.householdsplitter.ui.setup.SetupMembersFragment;
import com.householdsplitter.ui.summary.SummaryFragment;

/** S3, Home. SPEC 7.3. */
public class HomeFragment extends BaseFragment {

    private FragmentHomeBinding binding;
    private HomeViewModel model;
    private OrderAdapter adapter;
    private ExportService exportService;
    private ActivityResultLauncher<String> createCsv;
    private String pendingCsv;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createCsv = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
                    if (uri != null && pendingCsv != null) {
                        exportService.writeToUri(uri, pendingCsv, result -> {
                            if (binding != null) {
                                Snackbar.make(binding.getRoot(), result.isOk()
                                                ? R.string.export_saved : R.string.export_failed,
                                        Snackbar.LENGTH_LONG).show();
                            }
                        });
                    }
                    pendingCsv = null;
                });
    }

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

        Insets.padTop(binding.toolbar);
        Insets.marginBottom(binding.newOrderFab);
        exportService = new ExportService(locator().orderRepository(), locator().settings(),
                locator().executors(), requireContext().getContentResolver());
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

        model.rows().observe(getViewLifecycleOwner(), rows -> {
            adapter.submitList(rows);
            renderEmptyStates(rows);
        });
        // The controls only appear once there is something to search, so a new household is
        // not shown a filter for a list it does not have yet.
        model.orders().observe(getViewLifecycleOwner(), orders -> {
            boolean any = orders != null && !orders.isEmpty();
            binding.browseControls.setVisibility(any ? View.VISIBLE : View.GONE);
            renderEmptyStates(model.rows().getValue());
        });

        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                model.search(text == null ? "" : text.toString());
            }
        });

        binding.filterChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            int checked = checkedIds.get(0);
            if (checked == R.id.filterOpen) {
                model.filterBy(HomeViewModel.Filter.OPEN);
            } else if (checked == R.id.filterSettled) {
                model.filterBy(HomeViewModel.Filter.SETTLED);
            } else {
                model.filterBy(HomeViewModel.Filter.ALL);
            }
        });

        binding.clearSearchButton.setOnClickListener(v -> {
            binding.searchInput.setText("");
            binding.filterChips.check(R.id.filterAll);
        });

        binding.newOrderFab.setOnClickListener(v -> startNewOrder());
        binding.emptyStateButton.setOnClickListener(v -> startNewOrder());

        binding.toolbar.inflateMenu(R.menu.menu_home);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_analytics) {
                NavHostFragment.findNavController(this).navigate(R.id.analyticsFragment);
                return true;
            }
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

    /**
     * Three states, and telling them apart matters. An empty list because the household is
     * new wants an invitation to start; an empty list because a search matched nothing wants
     * a way back, and offering to create an order would be the wrong answer to a search.
     */
    private void renderEmptyStates(java.util.List<HomeRow> rows) {
        if (binding == null) {
            return;
        }
        boolean nothingShown = rows == null || rows.isEmpty();
        boolean filtered = nothingShown && model.isFilteredEmpty();
        binding.orderList.setVisibility(nothingShown ? View.GONE : View.VISIBLE);
        binding.emptyState.setVisibility(nothingShown && !filtered ? View.VISIBLE : View.GONE);
        binding.noMatchesState.setVisibility(filtered ? View.VISIBLE : View.GONE);
        // The FAB would sit over "Nothing matches" saying the opposite of what is needed.
        if (filtered) {
            binding.newOrderFab.hide();
        } else {
            binding.newOrderFab.show();
        }
    }

    /**
     * The store is chosen before the screenshots are taken, not after.
     *
     * <p>It selects the column geometry the parser measures with, so it has to be known at
     * parse time. It is also the moment the user already knows the answer, because they are
     * about to open that store's app to screenshot the order.
     */
    private void startNewOrder() {
        SettingsStore settings = locator().settings();
        StorePickerSheet.withLastUsed(settings.lastStore(), store -> {
            settings.lastStore(store);
            Bundle args = new Bundle();
            args.putString(ParsingArgs.ARG_STORE, store.name());
            NavHostFragment.findNavController(this).navigate(R.id.importFragment, args);
        }).show(getParentFragmentManager(), "store_picker");
    }

    /**
     * SPEC 7.3.4: a draft resumes where it stopped, and anything further along opens the
     * summary.
     *
     * <p>Only a SETTLED order opens locked. SPEC 7.3.4 routes both assigned and settled
     * orders to the read-only view, but SPEC 7.10.7 is the rule that actually says what
     * being locked means, and it ties that to settling: "Mark as settled sets status
     * SETTLED and makes the order read-only". Locking an assigned order too would make the
     * payer selector of SPEC 7.10.4 reachable only during the first pass through an order,
     * and in practice nobody knows who is paying until afterwards. An assigned order
     * therefore stays editable until it is settled.
     */
    private void openOrder(OrderWithMembers row) {
        Bundle args = new Bundle();
        args.putLong(ParsingArgs.ARG_ORDER_ID, row.order.id);
        if (row.order.status == OrderStatus.SETTLED) {
            args.putBoolean(SummaryFragment.ARG_READ_ONLY, true);
            NavHostFragment.findNavController(this).navigate(R.id.summaryFragment, args);
            return;
        }
        if (row.order.status == OrderStatus.ASSIGNED) {
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

    /** SPEC 7.3.6: Rename, Duplicate assignments, Export, Delete. */
    private void showContextMenu(OrderWithMembers row, View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 0, 0, R.string.action_rename);
        menu.getMenu().add(0, 1, 1, R.string.action_duplicate_assignments);
        menu.getMenu().add(0, 2, 2, R.string.action_export_csv);
        menu.getMenu().add(0, 3, 3, R.string.action_delete);
        menu.setOnMenuItemClickListener(entry -> {
            switch (entry.getItemId()) {
                case 0:
                    showRenameDialog(row);
                    return true;
                case 1:
                    duplicate(row);
                    return true;
                case 2:
                    exportOne(row);
                    return true;
                default:
                    confirmDelete(row);
                    return true;
            }
        });
        menu.show();
    }

    /**
     * SPEC 7.3.6. The copy is a fresh draft carrying the same rows and the same answers, so
     * a repeat shop starts from last week rather than from nothing.
     */
    private void duplicate(OrderWithMembers row) {
        model.duplicate(row.order.id, newOrderId -> {
            if (newOrderId == null || binding == null) {
                return;
            }
            Snackbar.make(binding.getRoot(), R.string.order_duplicated, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_open, v -> {
                        Bundle args = new Bundle();
                        args.putLong(ParsingArgs.ARG_ORDER_ID, newOrderId);
                        NavHostFragment.findNavController(this)
                                .navigate(R.id.reviewItemsFragment, args);
                    })
                    .show();
        });
    }

    /** SPEC 7.3.6 and 7.11.2. An order still being assigned cannot be totalled, and says so. */
    private void exportOne(OrderWithMembers row) {
        model.bundleFor(row.order.id, bundle -> {
            if (bundle == null || binding == null) {
                return;
            }
            try {
                SplitResult result = SplitCalculator.calculate(CalcMapper.toCalcOrder(
                        bundle, locator().settings().allocationMode()));
                String groupName = model.household().getValue() == null
                        ? "" : model.household().getValue().name;
                pendingCsv = exportService.buildCsv(bundle, groupName, result);
                createCsv.launch(row.order.label.replaceAll("[^A-Za-z0-9 _-]", "") + "-split.csv");
            } catch (RuntimeException notReady) {
                Snackbar.make(binding.getRoot(), R.string.export_needs_assignments,
                        Snackbar.LENGTH_LONG).show();
            }
        });
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
