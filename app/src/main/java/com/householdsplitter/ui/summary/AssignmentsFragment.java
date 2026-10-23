package com.householdsplitter.ui.summary;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;

import com.householdsplitter.R;
import com.householdsplitter.core.calc.result.SplitResult;
import com.householdsplitter.core.export.ItemisedBreakdown;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.data.mapper.CalcMapper;
import com.householdsplitter.data.relation.OrderBundle;
import com.householdsplitter.databinding.FragmentAssignmentsBinding;
import com.householdsplitter.databinding.ItemBreakdownLineBinding;
import com.householdsplitter.export.ExportService;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.common.Insets;
import com.householdsplitter.ui.parsing.ParsingArgs;

import java.util.List;

/**
 * Every assignment on one order, in full, read-only.
 *
 * <p>The summary says what each person owes, and a member row expands to show their own
 * items. What neither showed is the list everybody is charged a slice of, so the only
 * complete answer to "what am I actually paying for" was the text produced by Share: you
 * had to post the order into a group chat to read your own breakdown.
 *
 * <p>It rebuilds from the order id rather than being handed a snapshot of figures. That is
 * what lets it be a destination: it survives rotation and process death, and it cannot
 * show numbers that have gone stale since the screen behind it computed them.
 */
public class AssignmentsFragment extends BaseFragment {

    private FragmentAssignmentsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAssignmentsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Insets.padTop(binding.toolbar);
        Insets.padBottomScrollable(binding.scroll);
        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        CurrencyFormat money = new CurrencyFormat(locator().settings().currencySymbol(),
                locator().settings().locale());
        ExportService exportService = new ExportService(locator().orderRepository(),
                locator().settings(), locator().executors(),
                requireContext().getContentResolver());

        // The same ViewModel the summary uses, on the same order id, so the split shown
        // here is the split shown there rather than a second run of the calculator.
        SummaryViewModel model = viewModel(SummaryViewModel.class);
        model.bundle().observe(getViewLifecycleOwner(), bundle -> {
            if (bundle != null && bundle.order != null) {
                model.recalculate();
            }
        });
        model.result().observe(getViewLifecycleOwner(), result -> {
            OrderBundle bundle = model.bundle().getValue();
            if (binding == null || bundle == null || bundle.order == null || result == null) {
                return;
            }
            render(bundle, result, exportService, money);
        });
    }

    private void render(OrderBundle bundle, SplitResult result, ExportService exportService,
                        CurrencyFormat money) {
        binding.toolbar.setTitle(bundle.order.label);
        binding.assignmentsSubtitle.setText(getString(R.string.assignments_subtitle,
                result.participantCount()));

        int sharedCount = CalcMapper.commonItems(bundle).size();
        String heading = sharedCount == 0
                ? getString(R.string.breakdown_shared_by_everyone)
                : getString(R.string.breakdown_shared_by_everyone_counted, sharedCount);
        List<ItemisedBreakdown.Line> lines =
                exportService.buildBreakdown(bundle, result, heading, labels());

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        binding.assignmentLines.removeAllViews();
        for (ItemisedBreakdown.Line line : lines) {
            binding.assignmentLines.addView(rowFor(inflater, line, money));
        }
    }

    private View rowFor(LayoutInflater inflater, ItemisedBreakdown.Line line,
                        CurrencyFormat money) {
        ItemBreakdownLineBinding row =
                ItemBreakdownLineBinding.inflate(inflater, binding.assignmentLines, false);
        row.lineLabel.setText(line.label());
        row.lineAmount.setText(line.cents() == null ? "" : money.format(line.cents()));

        if (line.note() == null) {
            row.lineNote.setVisibility(View.GONE);
        } else {
            row.lineNote.setVisibility(View.VISIBLE);
            row.lineNote.setText(line.note());
        }

        // Weight and indentation carry the structure. Four kinds of line in one type size
        // reads as one long list, which is what the share text already is.
        switch (line.kind()) {
            case SECTION:
                row.lineLabel.setTextAppearance(R.style.Text_SectionLabel);
                row.lineAmount.setTextAppearance(R.style.Text_Money);
                row.getRoot().setPadding(0, padding(R.dimen.space_l), 0,
                        padding(R.dimen.space_xs));
                break;
            case SUBHEADING:
                row.lineLabel.setTextAppearance(R.style.Text_Caption);
                row.getRoot().setPadding(padding(R.dimen.space_m), padding(R.dimen.space_s),
                        0, 0);
                break;
            case TOTAL:
                row.lineLabel.setTypeface(row.lineLabel.getTypeface(), Typeface.BOLD);
                row.lineAmount.setTypeface(row.lineAmount.getTypeface(), Typeface.BOLD);
                row.getRoot().setPadding(padding(R.dimen.space_m), padding(R.dimen.space_xs),
                        0, padding(R.dimen.space_xs));
                break;
            default:
                row.getRoot().setPadding(padding(R.dimen.space_m), padding(R.dimen.space_xxs),
                        0, padding(R.dimen.space_xxs));
                break;
        }
        return row.getRoot();
    }

    private int padding(int dimen) {
        return getResources().getDimensionPixelSize(dimen);
    }

    /** The wording, because :core holds no English. */
    private ItemisedBreakdown.Labels labels() {
        return new ItemisedBreakdown.Labels() {
            @Override
            public String shareOfShared() {
                return getString(R.string.breakdown_share_of_shared);
            }

            @Override
            public String ownItems() {
                return getString(R.string.breakdown_own_items);
            }

            @Override
            public String taxAndFees() {
                return getString(R.string.breakdown_tax_and_fees);
            }

            @Override
            public String total() {
                return getString(R.string.breakdown_total);
            }

            @Override
            public String nothingShared() {
                return getString(R.string.breakdown_nothing_shared);
            }

            @Override
            public String ways(int wayCount) {
                return getString(R.string.breakdown_ways, wayCount);
            }

            @Override
            public String waysWithShares(int wayCount, int shares) {
                return getString(R.string.breakdown_ways_with_shares, wayCount, shares);
            }
        };
    }

    /** The order this screen is about, which the ViewModel factory also reads. */
    public static Bundle argsFor(long orderId) {
        Bundle args = new Bundle();
        args.putLong(ParsingArgs.ARG_ORDER_ID, orderId);
        return args;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
