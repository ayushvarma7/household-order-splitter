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
import com.householdsplitter.core.analytics.SpendingAnalytics;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.databinding.FragmentAnalyticsBinding;
import com.householdsplitter.databinding.ItemAnalyticsMemberBinding;
import com.householdsplitter.databinding.ItemAnalyticsMonthBinding;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.common.Insets;
import com.householdsplitter.ui.common.MemberPalette;

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
        model.load();
    }

    private void render(SpendingAnalytics.Report report) {
        if (binding == null || report == null) {
            return;
        }
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
