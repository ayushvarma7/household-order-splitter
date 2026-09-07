package com.householdsplitter.ui.analytics;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.chip.Chip;
import com.householdsplitter.R;
import com.householdsplitter.core.analytics.Balances;
import com.householdsplitter.core.analytics.SpendingAnalytics;
import com.householdsplitter.export.WorkbookService;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.databinding.FragmentAnalyticsBinding;
import com.householdsplitter.databinding.ItemAnalyticsMemberBinding;
import com.householdsplitter.databinding.ItemAnalyticsMonthBinding;
import com.householdsplitter.databinding.ItemPaymentBinding;
import com.householdsplitter.databinding.ItemSettleUpBinding;
import com.householdsplitter.data.entity.SettlementPayment;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.common.Insets;
import com.householdsplitter.ui.common.MemberPalette;
import com.householdsplitter.widget.BalancesWidgetProvider;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * S15, spending over time.
 *
 * <p>Charted with weighted views rather than a charting library, which keeps the dependency
 * budget of SPEC 4.11 intact and, more usefully, means every bar is a real view with its own
 * content description, so the screen reads properly with a screen reader instead of being
 * one opaque canvas.
 */
public class AnalyticsFragment extends BaseFragment {

    private FragmentAnalyticsBinding binding;
    private AnalyticsViewModel model;
    private CurrencyFormat money;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAnalyticsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        model = viewModel(AnalyticsViewModel.class);
        money = new CurrencyFormat(locator().settings().currencySymbol(),
                locator().settings().locale());

        Insets.padTop(binding.toolbar);
        Insets.padBottomScrollable(binding.scroll);
        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        model.loading().observe(getViewLifecycleOwner(), loading ->
                binding.loading.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));

        model.report().observe(getViewLifecycleOwner(), this::render);
        model.payments().observe(getViewLifecycleOwner(), this::renderPayments);
        model.message().observe(getViewLifecycleOwner(), text -> {
            if (text != null && binding != null) {
                com.google.android.material.snackbar.Snackbar
                        .make(binding.getRoot(), text,
                                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                        .show();
                model.messageShown();
            }
        });
        model.load();
    }

    private void render(WorkbookService.Insight insight) {
        if (binding == null || insight == null) {
            return;
        }
        SpendingAnalytics.Report report = insight.spending;
        boolean empty = report.isEmpty();
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.content.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            return;
        }

        binding.totalSpent.setText(money.format(report.totalSpentCents()));
        binding.orderCount.setText(getResources().getQuantityString(
                R.plurals.analytics_orders, report.orderCount(), report.orderCount()));
        binding.averageOrder.setText(money.format(report.averageOrderCents()));
        binding.largestOrder.setText(money.format(report.largestOrderCents()));
        binding.largestOrderLabel.setText(report.largestOrderLabel());

        renderSharedVersusPersonal(report);
        renderMembers(report.members(), report.totalSpentCents());
        renderMonths(report.months());
        renderFrequentItems(report.frequentItems());
        renderSettleUp(insight.balances);
    }

    /**
     * Who is out of pocket, and the shortest way to level up.
     *
     * <p>A single order answers "what do I owe for this shop?". Over a run of orders the
     * useful question becomes "what do I owe, all in?", because one person fronted each one
     * and those advances accumulate.
     */
    private void renderSettleUp(Balances.Report balances) {
        boolean anything = balances != null && !balances.balances().isEmpty();
        binding.settleSection.setVisibility(anything ? View.VISIBLE : View.GONE);
        if (!anything) {
            return;
        }
        binding.settleList.removeAllViews();

        if (balances.isLevel()) {
            binding.settleLevel.setVisibility(View.VISIBLE);
            binding.settleList.setVisibility(View.GONE);
            binding.settleSummary.setText(R.string.settle_nothing_outstanding);
            return;
        }
        binding.settleLevel.setVisibility(View.GONE);
        binding.settleList.setVisibility(View.VISIBLE);
        binding.settleSummary.setText(getString(R.string.settle_outstanding,
                money.format(balances.outstandingCents())));

        for (Balances.Transfer transfer : balances.transfers()) {
            ItemSettleUpBinding row = ItemSettleUpBinding.inflate(
                    getLayoutInflater(), binding.settleList, false);
            row.settleLine.setText(getString(R.string.settle_owes,
                    transfer.from().name(), transfer.to().name()));
            row.settleAmount.setText(money.format(transfer.amountCents()));
            row.getRoot().setContentDescription(getString(R.string.settle_owes_description,
                    transfer.from().name(), money.format(transfer.amountCents()),
                    transfer.to().name()));
            row.markPaidButton.setOnClickListener(v -> {
                model.recordPayment(
                        transfer.from().memberId(), transfer.to().memberId(),
                        transfer.amountCents(),
                        getString(R.string.settle_payment_recorded));
                // A recorded payment changes who owes whom, so the widget is now stale.
                BalancesWidgetProvider.refresh(requireContext().getApplicationContext());
            });
            binding.settleList.addView(row.getRoot());
        }
    }

    /** What has already been handed over, newest first, each undoable. */
    private void renderPayments(java.util.List<SettlementPayment> payments) {
        if (binding == null) {
            return;
        }
        boolean any = payments != null && !payments.isEmpty();
        binding.paymentSection.setVisibility(any ? View.VISIBLE : View.GONE);
        binding.paymentList.removeAllViews();
        if (!any) {
            return;
        }
        java.util.Map<Long, String> names = memberNames();
        java.text.SimpleDateFormat when =
                new java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault());
        for (SettlementPayment payment : payments) {
            ItemPaymentBinding row = ItemPaymentBinding.inflate(
                    getLayoutInflater(), binding.paymentList, false);
            String from = names.get(payment.fromMemberId);
            String to = names.get(payment.toMemberId);
            row.paymentLine.setText(getString(R.string.settle_paid_line,
                    from == null ? "?" : from, to == null ? "?" : to));
            row.paymentDate.setText(when.format(new java.util.Date(payment.paidAt)));
            row.paymentAmount.setText(money.format(payment.amountCents));
            row.undoButton.setOnClickListener(v -> {
                model.deletePayment(payment.id,
                        getString(R.string.settle_payment_removed));
                BalancesWidgetProvider.refresh(requireContext().getApplicationContext());
            });
            binding.paymentList.addView(row.getRoot());
        }
    }

    /**
     * Names for the payment history, taken from the balances so an archived member is still
     * named on a payment they made (SPEC 11.7 applies to money as much as to orders).
     */
    private java.util.Map<Long, String> memberNames() {
        java.util.Map<Long, String> names = new java.util.LinkedHashMap<>();
        WorkbookService.Insight insight = model.report().getValue();
        if (insight != null) {
            for (Balances.Balance balance : insight.balances.balances()) {
                names.put(balance.memberId(), balance.name());
            }
        }
        return names;
    }

    /** One bar showing how much of the household's money went on things everyone shared. */
    private void renderSharedVersusPersonal(SpendingAnalytics.Report report) {
        long shared = Math.max(0L, report.commonSpendCents());
        long personal = Math.max(0L, report.personalSpendCents());
        long together = shared + personal;

        binding.sharedAmount.setText(money.format(shared));
        binding.personalAmount.setText(money.format(personal));
        setWeight(binding.sharedBar, together == 0L ? 1f : shared);
        setWeight(binding.personalBar, together == 0L ? 1f : personal);
        binding.splitBarRow.setContentDescription(getString(R.string.analytics_split_description,
                money.format(shared), money.format(personal)));
    }

    private void renderMembers(List<SpendingAnalytics.MemberTotal> members, long total) {
        binding.memberContainer.removeAllViews();
        for (SpendingAnalytics.MemberTotal member : members) {
            ItemAnalyticsMemberBinding row = ItemAnalyticsMemberBinding.inflate(
                    getLayoutInflater(), binding.memberContainer, false);
            row.memberName.setText(member.name());
            row.memberTotal.setText(money.format(member.totalCents()));
            // Permille to a percentage with one decimal, kept in integers throughout.
            row.memberShare.setText(getString(R.string.analytics_share,
                    member.sharePermille() / 10, member.sharePermille() % 10));

            int colour = MemberPalette.resolve(requireContext(),
                    com.householdsplitter.ui.common.MemberPalette.colorForIndex(
                            binding.memberContainer.getChildCount()));
            row.bar.setBackgroundTintList(ColorStateList.valueOf(colour));
            setWeight(row.bar, Math.max(1, member.sharePermille()));
            setWeight(row.barRemainder, Math.max(1, 1000 - member.sharePermille()));

            row.getRoot().setContentDescription(getString(R.string.analytics_member_description,
                    member.name(), money.format(member.totalCents()),
                    member.sharePermille() / 10));
            binding.memberContainer.addView(row.getRoot());
        }
    }

    private void renderMonths(List<SpendingAnalytics.MonthPoint> months) {
        binding.monthContainer.removeAllViews();
        binding.monthSection.setVisibility(months.size() < 2 ? View.GONE : View.VISIBLE);
        if (months.size() < 2) {
            return;
        }
        long peak = 1L;
        for (SpendingAnalytics.MonthPoint month : months) {
            peak = Math.max(peak, month.totalCents());
        }
        for (SpendingAnalytics.MonthPoint month : months) {
            ItemAnalyticsMonthBinding column = ItemAnalyticsMonthBinding.inflate(
                    getLayoutInflater(), binding.monthContainer, false);
            // Integer arithmetic: the tallest bar is full height and the rest scale to it.
            int filled = (int) ((month.totalCents() * 100L) / peak);
            setWeight(column.bar, Math.max(1, filled));
            setWeight(column.barSpacer, Math.max(1, 100 - filled));
            column.monthLabel.setText(shortMonth(month.month()));
            column.monthAmount.setText(money.formatBare(month.totalCents()));
            column.getRoot().setContentDescription(getString(R.string.analytics_month_description,
                    shortMonth(month.month()), money.format(month.totalCents()),
                    month.orderCount()));
            binding.monthContainer.addView(column.getRoot());
        }
    }

    private void renderFrequentItems(List<String> items) {
        binding.frequentSection.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        binding.frequentItems.removeAllViews();
        for (String item : items) {
            Chip chip = new Chip(requireContext());
            chip.setText(capitalise(item));
            chip.setClickable(false);
            chip.setCheckable(false);
            binding.frequentItems.addView(chip);
        }
    }

    private static void setWeight(View view, float weight) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.weight = weight;
        view.setLayoutParams(params);
    }

    /** {@code 2026-08} to {@code Aug}. */
    private static String shortMonth(String yearMonth) {
        try {
            String[] parts = yearMonth.split("-");
            Calendar calendar = Calendar.getInstance();
            calendar.clear();
            calendar.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, 1);
            return new SimpleDateFormat("MMM", Locale.getDefault()).format(calendar.getTime());
        } catch (RuntimeException notADate) {
            return yearMonth;
        }
    }

    private static String capitalise(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
