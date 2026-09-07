package com.householdsplitter.ui.summary;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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

import com.google.android.material.snackbar.Snackbar;
import com.householdsplitter.R;
import com.householdsplitter.core.calc.AdjustmentType;
import com.householdsplitter.core.export.CsvExporter;
import com.householdsplitter.core.money.Cents;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.core.calc.result.SplitResult;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.OrderStatus;
import com.householdsplitter.data.relation.OrderBundle;
import com.householdsplitter.databinding.FragmentSummaryBinding;
import com.householdsplitter.export.ExportService;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.parsing.ParsingArgs;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * S10, the summary, and S12, the same content read-only. SPEC 7.10 and 7.12.
 *
 * <p>SPEC 7.12.1 describes S12 as "the same content as S10 with every control disabled
 * except Share, Export, Duplicate, and Reopen", so it is the same screen behind a flag
 * rather than a near-copy that can drift out of step.
 */
public class SummaryFragment extends BaseFragment {

    /** SPEC 7.12: opened read-only from Home for an assigned or settled order. */
    public static final String ARG_READ_ONLY = "read_only";

    private FragmentSummaryBinding binding;
    private SummaryViewModel model;
    private MemberSplitAdapter adapter;
    private ExportService exportService;
    private CurrencyFormat money;
    private long orderId;
    private boolean readOnly;

    private ActivityResultLauncher<String> createCsv;
    private String pendingCsv;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // SPEC 7.11.2
        createCsv = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
                    if (uri != null && pendingCsv != null) {
                        exportService.writeToUri(uri, pendingCsv, result -> {
                            if (binding == null) {
                                return;
                            }
                            Snackbar.make(binding.getRoot(), result.isOk()
                                            ? R.string.export_saved : R.string.export_failed,
                                    Snackbar.LENGTH_LONG).show();
                        });
                    }
                    pendingCsv = null;
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSummaryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        orderId = getArguments() == null ? 0L : getArguments().getLong(ParsingArgs.ARG_ORDER_ID);
        readOnly = getArguments() != null && getArguments().getBoolean(ARG_READ_ONLY, false);

        model = viewModel(SummaryViewModel.class);
        money = new CurrencyFormat(locator().settings().currencySymbol(),
                locator().settings().locale());
        exportService = new ExportService(locator().orderRepository(), locator().settings(),
                locator().executors(), requireContext().getContentResolver());

        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        adapter = new MemberSplitAdapter(money);
        binding.memberList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.memberList.setAdapter(adapter);
        binding.memberList.setNestedScrollingEnabled(false);

        model.bundle().observe(getViewLifecycleOwner(), bundle -> {
            if (bundle == null || bundle.order == null) {
                return;
            }
            renderHeader(bundle);
            Map<Long, String> colors = new HashMap<>();
            for (Member member : bundle.participants) {
                colors.put(member.id, member.colorHex);
            }
            adapter.setColors(colors);
            String payer = null;
            for (Member member : bundle.participants) {
                if (bundle.order.payerMemberId != null
                        && member.id == bundle.order.payerMemberId) {
                    payer = member.name;
                }
            }
            adapter.setPayer(bundle.order.payerMemberId, payer);
            applyReadOnly(bundle);
            model.recalculate();
        });

        model.result().observe(getViewLifecycleOwner(), this::renderResult);

        // SPEC 7.10.6
        model.blocking().observe(getViewLifecycleOwner(), this::renderBlockingPanel);

        model.error().observe(getViewLifecycleOwner(), message -> {
            if (message != null && binding != null) {
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });

        binding.payerButton.setOnClickListener(this::showPayerMenu);
        binding.shareButton.setOnClickListener(v -> shareAsText());
        binding.exportButton.setOnClickListener(v -> exportCsv());
        binding.editAssignmentsButton.setOnClickListener(v -> navigate(R.id.assignFragment));
        binding.editParticipantsButton.setOnClickListener(v -> navigate(R.id.participantsFragment));
        binding.settleButton.setOnClickListener(v -> toggleSettled());
    }

    private void renderHeader(OrderBundle bundle) {
        binding.toolbar.setTitle(bundle.order.label);
        binding.orderDate.setText(new SimpleDateFormat("EEEE d MMMM yyyy", Locale.getDefault())
                .format(new java.util.Date(bundle.order.orderDate)));
    }

    /** SPEC 7.12.1: everything disabled except Share, Export and Reopen. */
    private void applyReadOnly(OrderBundle bundle) {
        boolean settled = bundle.order.status == OrderStatus.SETTLED;
        boolean locked = readOnly || settled;
        binding.payerButton.setEnabled(!locked);
        binding.editAssignmentsButton.setEnabled(!locked);
        binding.editParticipantsButton.setEnabled(!locked);
        binding.settleButton.setText(settled ? R.string.action_reopen : R.string.action_mark_settled);
        binding.statusChip.setVisibility(settled ? View.VISIBLE : View.GONE);
    }

    private void renderResult(SplitResult result) {
        if (binding == null) {
            return;
        }
        if (result == null) {
            binding.summaryContent.setVisibility(View.GONE);
            return;
        }
        binding.summaryContent.setVisibility(View.VISIBLE);
        adapter.submitList(result.members());

        // SPEC 7.10.3
        StringBuilder footer = new StringBuilder();
        footer.append(getString(R.string.summary_common_bucket,
                money.format(result.commonBucketCents()),
                result.participantCount(),
                money.symbol() + Cents.quotientForDisplay(result.commonBucketCents(),
                        Math.max(1, result.participantCount()))));
        footer.append('\n').append(getString(R.string.summary_item_subtotal,
                money.format(result.itemSubtotalCents())));
        for (AdjustmentType type : AdjustmentType.values()) {
            long value = result.adjustmentTotal(type);
            if (value != 0L) {
                footer.append('\n').append(type.label()).append(": ").append(money.format(value));
            }
        }
        footer.append('\n').append(getString(R.string.summary_computed_total,
                money.format(result.computedTotalCents())));
        if (result.statedTotalCents() != 0L) {
            footer.append('\n').append(getString(R.string.summary_stated_total,
                    money.format(result.statedTotalCents())));
        }
        binding.footerDetail.setText(footer.toString());

        // SPEC 7.10.3 and 8.9.4: the verdict, restated here as SPEC 7.7.4 requires.
        if (result.matchesStatedTotal()) {
            binding.verdict.setText(R.string.summary_matches);
            binding.verdict.setTextColor(0xFF2E7D32);
        } else {
            binding.verdict.setText(getString(R.string.summary_off_by,
                    money.formatSigned(result.deltaCents())));
            binding.verdict.setTextColor(0xFFC62828);
        }

        if (result.equalFallbackUsed()) {
            binding.fallbackNote.setVisibility(View.VISIBLE);
        } else {
            binding.fallbackNote.setVisibility(View.GONE);
        }

        if (!readOnly) {
            model.markAssigned();
        }
    }

    /** SPEC 7.10.6: a blocking panel listing the offenders, each tappable. */
    private void renderBlockingPanel(List<LineItem> unassigned) {
        if (binding == null) {
            return;
        }
        boolean blocked = unassigned != null && !unassigned.isEmpty();
        binding.blockingPanel.setVisibility(blocked ? View.VISIBLE : View.GONE);
        binding.summaryContent.setVisibility(blocked ? View.GONE : binding.summaryContent.getVisibility());
        if (!blocked) {
            return;
        }
        binding.blockingList.removeAllViews();
        binding.blockingTitle.setText(getResources().getQuantityString(
                R.plurals.summary_blocked, unassigned.size(), unassigned.size()));
        for (LineItem item : unassigned) {
            com.google.android.material.button.MaterialButton row =
                    new com.google.android.material.button.MaterialButton(requireContext(), null,
                            com.google.android.material.R.attr.materialButtonOutlinedStyle);
            row.setText(item.name);
            row.setMinHeight(dp(48));
            row.setOnClickListener(v -> {
                Bundle args = new Bundle();
                args.putLong(ParsingArgs.ARG_ORDER_ID, orderId);
                NavHostFragment.findNavController(this).navigate(R.id.assignFragment, args);
            });
            binding.blockingList.addView(row);
        }
    }

    /** SPEC 7.10.4. */
    private void showPayerMenu(View anchor) {
        OrderBundle bundle = model.bundle().getValue();
        if (bundle == null) {
            return;
        }
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        List<Member> members = bundle.participants;
        for (int i = 0; i < members.size(); i++) {
            menu.getMenu().add(0, i, i, members.get(i).name);
        }
        menu.getMenu().add(0, members.size(), members.size(), getString(R.string.payer_none));
        menu.setOnMenuItemClickListener(entry -> {
            model.setPayer(entry.getItemId() >= members.size()
                    ? null : members.get(entry.getItemId()).id);
            return true;
        });
        menu.show();
    }

    /** SPEC 7.11.1. */
    private void shareAsText() {
        OrderBundle bundle = model.bundle().getValue();
        SplitResult result = model.result().getValue();
        if (bundle == null || result == null) {
            return;
        }
        groupName(name -> {
            String text = exportService.buildShareText(bundle, name, result);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(intent, getString(R.string.action_share)));
        });
    }

    /** SPEC 7.11.2: default filename "<label>-split.csv". */
    private void exportCsv() {
        OrderBundle bundle = model.bundle().getValue();
        SplitResult result = model.result().getValue();
        if (bundle == null || result == null) {
            return;
        }
        groupName(name -> {
            pendingCsv = exportService.buildCsv(bundle, name, result);
            createCsv.launch(bundle.order.label.replaceAll("[^A-Za-z0-9 _-]", "") + "-split.csv");
        });
    }

    private void groupName(com.householdsplitter.util.Callback<String> callback) {
        locator().householdRepository().observeHousehold().observe(getViewLifecycleOwner(),
                household -> callback.onResult(household == null ? "" : household.name));
    }

    /** SPEC 7.10.7: unlocking needs an explicit Reopen with a confirmation. */
    private void toggleSettled() {
        OrderBundle bundle = model.bundle().getValue();
        if (bundle == null) {
            return;
        }
        if (bundle.order.status == OrderStatus.SETTLED) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.reopen_title)
                    .setMessage(R.string.reopen_message)
                    .setPositiveButton(R.string.action_reopen, (d, w) -> model.reopen())
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
        } else {
            model.markSettled();
        }
    }

    private void navigate(int destination) {
        Bundle args = new Bundle();
        args.putLong(ParsingArgs.ARG_ORDER_ID, orderId);
        NavHostFragment.findNavController(this).navigate(destination, args);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.memberList.setAdapter(null);
        binding = null;
    }
}
