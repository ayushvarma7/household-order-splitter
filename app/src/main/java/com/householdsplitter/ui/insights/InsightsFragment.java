package com.householdsplitter.ui.insights;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.color.MaterialColors;
import com.householdsplitter.R;
import com.householdsplitter.core.insight.Insight;
import com.householdsplitter.core.insight.InsightGenerator;
import com.householdsplitter.core.insight.InsightTree;
import com.householdsplitter.core.insight.InsightTree.Node;
import com.householdsplitter.core.insight.Trivia;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.databinding.FragmentInsightsBinding;
import com.householdsplitter.databinding.ItemInsightFactBinding;
import com.householdsplitter.databinding.ItemInsightMemberBinding;
import com.householdsplitter.databinding.ItemInsightRowBinding;
import com.householdsplitter.insight.InsightService;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.common.Insets;
import com.householdsplitter.ui.common.MemberPalette;
import com.householdsplitter.ui.parsing.ParsingArgs;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Explore: what was bought, as bubbles you can zoom into, read like a short article.
 *
 * <p>The screen leads with one number and one plain sentence about whatever is in front, then
 * the picture, then a few quiet sections: money the reader missed (the only thing in an alarm
 * colour, because it is the only thing to fix), who it was for, what is inside as tappable
 * rows, a swipeable row of facts that each open full screen, and one "did you know" that
 * changes every day.
 *
 * <p>Everything here reads from the same stored orders as the Spending screen, and nothing is
 * written back.
 */
public class InsightsFragment extends BaseFragment implements BubbleView.Listener {

    /** Rows shown inside a bubble before "Show more", so a long item list stays a glance. */
    private static final int ROWS_AT_FIRST = 6;

    /** Material's emphasised easing, for things that arrive. */
    private static final PathInterpolator EMPHASIS = new PathInterpolator(.2f, 0f, 0f, 1f);

    /** A tab, not a step down: siblings fade through rather than sliding. */
    @Override
    protected boolean isTopLevel() {
        return true;
    }

    private FragmentInsightsBinding binding;
    private InsightsViewModel model;
    private CurrencyFormat money;
    private InsightData current;

    private long shownCents = -1L;
    private ValueAnimator counting;
    private Node expandedFor;
    private int triviaIndex;

    private Insight openStory;
    private boolean storyBusy;
    private float dragStartY;
    private float dragStartX;
    private boolean dragging;

    private final OnBackPressedCallback closeStoryOnBack = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            dismissStory(false);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentInsightsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        model = viewModel(InsightsViewModel.class);
        money = new CurrencyFormat(locator().settings().currencySymbol(),
                locator().settings().locale());

        Insets.padTop(binding.toolbar);
        Insets.padBottomScrollable(binding.scroll);
        Insets.padTopAndBottom(binding.storyColumn);
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                closeStoryOnBack);

        // On a tablet or a phone on its side the column stops growing at a comfortable width
        // and centres, instead of stretching the picture and the text across the screen. The
        // fact row still runs edge to edge, so its bleed follows the margin.
        binding.scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            float dp = getResources().getDisplayMetrics().density;
            int margin = getResources().getDimensionPixelSize(R.dimen.screen_margin);
            int side = Math.max(margin, Math.round(((r - l) - 600f * dp) / 2f));
            if (binding.content.getPaddingStart() != side) {
                binding.content.setPaddingRelative(side, binding.content.getPaddingTop(), side,
                        binding.content.getPaddingBottom());
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)
                        binding.factScroll.getLayoutParams();
                lp.setMarginStart(-side);
                lp.setMarginEnd(-side);
                binding.factScroll.setLayoutParams(lp);
                binding.factScroll.setPaddingRelative(side, 0, side, 0);
                int story = Math.max(Math.round(24 * dp),
                        Math.round(((r - l) - 560f * dp) / 2f));
                binding.storyColumn.setPaddingRelative(story,
                        binding.storyColumn.getPaddingTop(), story,
                        binding.storyColumn.getPaddingBottom());
            }
        });

        // Headings let a screen reader jump section to section.
        for (View heading : new View[] {binding.monthLabel, binding.peopleLabel,
                binding.insideLabel, binding.factsTitle}) {
            ViewCompat.setAccessibilityHeading(heading, true);
        }

        binding.bubbles.setMoney(money);
        binding.bubbles.setListener(this);
        binding.bringBack.setOnClickListener(v -> {
            model.bringAllBack();
            if (binding.bubbles.focus() != null) {
                showFocus(binding.bubbles.focus());
            }
        });
        binding.exampleButton.setOnClickListener(v -> model.startExample());
        binding.exampleExit.setOnClickListener(v -> model.exitExample());
        binding.earlierMonth.setOnClickListener(v -> stepMonth(1));
        binding.laterMonth.setOnClickListener(v -> stepMonth(-1));
        binding.triviaCard.setOnClickListener(v -> {
            triviaIndex++;
            if (current != null) {
                renderTrivia(current.trivia, true);
            }
        });
        setUpStory();

        model.loading().observe(getViewLifecycleOwner(), loading ->
                binding.loading.setVisibility(Boolean.TRUE.equals(loading)
                        ? View.VISIBLE : View.GONE));
        model.data().observe(getViewLifecycleOwner(), this::render);
        model.load();
    }

    // ------------------------------------------------------------------ rendering

    private void render(InsightData data) {
        if (binding == null || data == null) {
            return;
        }
        boolean monthChanged = current == null || !data.month.equals(current.month)
                || data.example != current.example;
        current = data;
        boolean empty = data.isEmpty();
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.scroll.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.exampleBanner.setVisibility(data.example ? View.VISIBLE : View.GONE);
        if (empty) {
            return;
        }
        binding.monthLabel.setText(monthTitle(data.month));
        int index = data.months.indexOf(data.month);
        binding.earlierMonth.setEnabled(index >= 0 && index < data.months.size() - 1);
        binding.laterMonth.setEnabled(index > 0);
        binding.earlierMonth.setAlpha(binding.earlierMonth.isEnabled() ? 1f : .3f);
        binding.laterMonth.setAlpha(binding.laterMonth.isEnabled() ? 1f : .3f);

        if (monthChanged) {
            shownCents = -1L;
            triviaIndex = 0;
        }
        binding.bubbles.setTree(data.tree, model.focusPath());
        showFocus(binding.bubbles.focus());
        renderTrivia(data.trivia, false);
        renderPatterns(data);
    }

    /** The three pictures across every finished order, each followed by its facts in words. */
    private void renderPatterns(InsightData data) {
        boolean any = data.calendar != null && data.calendar.shoppingDays > 0;
        binding.patternsSection.setVisibility(any ? View.VISIBLE : View.GONE);
        if (!any) {
            return;
        }
        renderWeb(data);
        renderCalendar(data.calendar);
        renderPrices(data.prices);
    }

    private void renderWeb(InsightData data) {
        com.householdsplitter.core.insight.Patterns.Web web = data.web;
        binding.webView.setWeb(web, data.members);
        String text;
        if (web == null || web.nodes.size() < 3) {
            text = getString(R.string.patterns_web_small);
        } else if (web.edges.isEmpty()) {
            text = getString(R.string.patterns_web_none);
        } else {
            com.householdsplitter.core.insight.Patterns.Edge top = web.edges.get(0);
            text = getString(R.string.patterns_web_top, nameOf(data, top.a), nameOf(data, top.b),
                    money.format(top.cents),
                    getResources().getQuantityString(R.plurals.patterns_web_items, top.items,
                            top.items));
            if (web.edges.size() > 1) {
                com.householdsplitter.core.insight.Patterns.Edge next = web.edges.get(1);
                text += " " + getString(R.string.patterns_web_more, nameOf(data, next.a),
                        nameOf(data, next.b), money.format(next.cents));
            }
        }
        binding.webSummary.setText(text);
        binding.webView.setContentDescription(getString(R.string.patterns_web_description, text));
        binding.webSummary.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private String nameOf(InsightData data, long memberId) {
        com.householdsplitter.data.entity.Member member = data.members.get(memberId);
        return member == null ? "?" : member.name;
    }

    private void renderCalendar(com.householdsplitter.core.insight.Patterns.Calendar calendar) {
        binding.calendarView.setCalendar(calendar);
        String weekday = calendar.busiestWeekday == null ? ""
                : calendar.busiestWeekday.getDisplayName(java.time.format.TextStyle.FULL,
                        Locale.getDefault());
        String text = calendar.shoppingDays == 1
                ? getString(R.string.patterns_calendar_one, calendar.weeks(), weekday)
                : getString(R.string.patterns_calendar_summary, calendar.shoppingDays,
                        calendar.weeks(), weekday, money.format(calendar.busiestWeekdayCents),
                        calendar.longestGap);
        binding.calendarSummary.setText(text);
        binding.calendarView.setContentDescription(
                getString(R.string.patterns_calendar_description, text));
        binding.calendarSummary.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private void renderPrices(List<com.householdsplitter.core.insight.Patterns.PriceSeries> prices) {
        binding.priceChips.removeAllViews();
        boolean none = prices == null || prices.isEmpty();
        binding.priceView.setVisibility(none ? View.GONE : View.VISIBLE);
        if (none) {
            binding.priceSummary.setText(R.string.patterns_price_none);
            return;
        }
        for (int i = 0; i < prices.size(); i++) {
            com.householdsplitter.core.insight.Patterns.PriceSeries series = prices.get(i);
            com.google.android.material.chip.Chip chip =
                    new com.google.android.material.chip.Chip(requireContext());
            chip.setText(ItemNames.shortName(series.name));
            chip.setCheckable(true);
            chip.setId(View.generateViewId());
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (checked) {
                    showPrice(series);
                }
            });
            binding.priceChips.addView(chip);
            if (i == 0) {
                chip.setChecked(true);
            }
        }
    }

    private void showPrice(com.householdsplitter.core.insight.Patterns.PriceSeries series) {
        binding.priceView.setSeries(series, money);
        long change = series.change();
        String trend = change > 0L ? getString(R.string.patterns_price_up, money.format(change))
                : change < 0L ? getString(R.string.patterns_price_down, money.format(-change))
                : getString(R.string.patterns_price_same);
        String cheapest = series.cheapestStore();
        if (cheapest != null) {
            trend += " " + getString(R.string.patterns_price_cheapest, cheapest);
        }
        String text = getString(R.string.patterns_price_summary, series.points.size(),
                money.format(series.lowest()), money.format(series.highest()), trend);
        binding.priceSummary.setText(text);
        binding.priceView.setContentDescription(
                getString(R.string.patterns_price_description, series.name, text));
        binding.priceSummary.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Everything that depends on which bubble is in front. */
    private void showFocus(Node node) {
        if (binding == null || current == null || node == null) {
            return;
        }
        renderCrumbs(node);
        renderHero(node);
        binding.bubbles.setContentDescription(getString(R.string.insights_canvas_description,
                monthTitle(current.month), node.name()));
        renderMissing(node);
        renderPeople(node);
        renderInside(node);
        renderFacts(node);
    }

    /** Where you are, as small text you can tap to step back out: All groceries > Snacks. */
    private void renderCrumbs(Node node) {
        binding.crumbs.removeAllViews();
        List<Node> path = new ArrayList<>();
        for (Node n = node; n != null; n = n.parent()) {
            path.add(0, n);
        }
        int muted = MaterialColors.getColor(binding.crumbs,
                com.google.android.material.R.attr.colorOnSurfaceVariant);
        int strong = MaterialColors.getColor(binding.crumbs,
                com.google.android.material.R.attr.colorPrimary);
        float dp = getResources().getDisplayMetrics().density;
        for (int i = 0; i < path.size(); i++) {
            Node step = path.get(i);
            boolean here = step == node;
            if (i > 0) {
                TextView divider = new TextView(requireContext());
                divider.setText("/");
                divider.setTextColor(muted);
                divider.setAlpha(.5f);
                divider.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                binding.crumbs.addView(divider);
            }
            TextView label = new TextView(requireContext());
            label.setText(step.name());
            label.setTextAppearance(R.style.TextAppearance_Splitter_LabelLarge);
            label.setTextColor(here ? muted : strong);
            label.setGravity(Gravity.CENTER);
            label.setMinHeight(Math.round(40 * dp));
            label.setPadding(Math.round(6 * dp), 0, Math.round(6 * dp), 0);
            if (!here) {
                label.setBackgroundResource(R.drawable.bg_insight_row);
                label.setOnClickListener(v -> binding.bubbles.zoomTo(step));
            }
            binding.crumbs.addView(label);
        }
    }

    /** The big number, then one sentence and the change since last month. */
    private void renderHero(Node node) {
        InsightTree tree = current.tree;
        countTo(node.thisCents());
        binding.heroSentence.setText(sentenceFor(node));

        String change = changeFor(tree, node);
        binding.heroChange.setVisibility(change.isEmpty() ? View.GONE : View.VISIBLE);
        binding.heroChange.setText(change);
        // Spending less is quietly good news, so it gets the success colour. Spending more is
        // ordinary life, not an alarm, so it stays the plain muted text. The words say which.
        boolean down = tree.hasPreviousMonth() && !node.isMissing() && node.lastCents() > 0L
                && InsightGenerator.toneOf(node.thisCents(), node.lastCents())
                == Insight.Tone.DOWN;
        binding.heroChange.setTextColor(down
                ? ContextCompat.getColor(requireContext(), R.color.success)
                : MaterialColors.getColor(binding.heroChange,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        binding.heroAmount.setTextColor(node.isMissing()
                ? ContextCompat.getColor(requireContext(), R.color.warning)
                : MaterialColors.getColor(binding.heroAmount,
                com.google.android.material.R.attr.colorOnSurface));
    }

    /** Counts the hero up or down to a new amount, so a zoom reads as one movement. */
    private void countTo(long cents) {
        if (counting != null) {
            counting.cancel();
        }
        long from = shownCents;
        shownCents = cents;
        binding.heroAmount.setContentDescription(money.format(cents));
        if (from < 0L || from == cents || isAnimationOff()) {
            binding.heroAmount.setText(money.format(cents));
            return;
        }
        counting = ValueAnimator.ofFloat(0f, 1f);
        counting.setDuration(520);
        counting.setInterpolator(EMPHASIS);
        counting.addUpdateListener(a -> {
            if (binding == null) {
                return;
            }
            // The fraction only places the tick; each figure shown is whole cents.
            long step = from + Math.round((cents - from) * (double) a.getAnimatedFraction());
            binding.heroAmount.setText(money.format(step));
        });
        counting.start();
    }

    private String sentenceFor(Node node) {
        if (node.isMissing() && node.depth() == 1) {
            return getString(R.string.insights_hero_missing);
        }
        if (node.parent() == null) {
            Node biggest = null;
            for (Node kid : node.children()) {
                if (!kid.isMissing() && (biggest == null
                        || kid.thisCents() > biggest.thisCents())) {
                    biggest = kid;
                }
            }
            if (biggest == null) {
                return "";
            }
            return getString(R.string.insights_hero_root, biggest.name(),
                    InsightGenerator.percentOf(biggest.thisCents(), node.thisCents()));
        }
        Node parent = node.parent();
        String of = parent.parent() == null
                ? getString(R.string.insights_everything) : parent.name();
        return getString(R.string.insights_hero_part, node.name(),
                InsightGenerator.percentOf(node.thisCents(), parent.thisCents()), of);
    }

    private String changeFor(InsightTree tree, Node node) {
        if (node.isMissing()) {
            return "";
        }
        if (!tree.hasPreviousMonth()) {
            return getString(R.string.insights_card_no_compare);
        }
        if (node.lastCents() <= 0L) {
            return getString(R.string.insights_card_new);
        }
        int percent = Math.abs(InsightGenerator.percentChange(node.thisCents(),
                node.lastCents()));
        String previous = tree.previousMonth().getMonth()
                .getDisplayName(TextStyle.FULL, Locale.getDefault());
        switch (InsightGenerator.toneOf(node.thisCents(), node.lastCents())) {
            case UP:
                return getString(R.string.insights_card_up, percent, previous);
            case DOWN:
                return getString(R.string.insights_card_down, percent, previous);
            default:
                return getString(R.string.insights_card_steady, previous);
        }
    }

    /** The orders behind the missing money, at the top and inside the missing bubble. */
    private void renderMissing(Node node) {
        List<InsightService.MissingOrder> orders = current.missingOrders;
        boolean show = !orders.isEmpty()
                && (node.parent() == null || ItemNames.isMissing(node));
        binding.missingSection.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.missingRows.removeAllViews();
        if (!show) {
            return;
        }
        long total = 0L;
        long largest = 1L;
        for (InsightService.MissingOrder order : orders) {
            total += order.cents;
            largest = Math.max(largest, order.cents);
        }
        binding.missingAmount.setText(money.format(total));
        int ink = ContextCompat.getColor(requireContext(), R.color.on_warning_container);
        int bar = ContextCompat.getColor(requireContext(), R.color.warning);
        for (InsightService.MissingOrder order : orders) {
            ItemInsightRowBinding row = row(binding.missingRows, order.label,
                    money.format(order.cents), dateOf(order.dateMillis),
                    order.cents, largest, bar);
            row.rowName.setTextColor(ink);
            row.rowAmount.setTextColor(ink);
            row.rowDetail.setTextColor(ink);
            row.rowChevron.setImageTintList(ColorStateList.valueOf(ink));
            row.getRoot().setContentDescription(getString(
                    R.string.analytics_missing_row_description, order.label,
                    money.format(order.cents)));
            row.getRoot().setOnClickListener(v -> openOrder(order.orderId));
        }
    }

    /** A bar and a row per person, from the same cents the calculator split. */
    private void renderPeople(Node node) {
        binding.splitBar.removeAllViews();
        binding.memberRows.removeAllViews();
        Map<Long, Long> shares = node.thisByMember();
        long total = Math.max(1L, node.thisCents());
        float dp = getResources().getDisplayMetrics().density;
        boolean first = true;
        for (Map.Entry<Long, Long> entry : shares.entrySet()) {
            Member member = current.members.get(entry.getKey());
            String name = member == null ? getString(R.string.insights_someone) : member.name;
            int color = MemberPalette.resolve(requireContext(),
                    member == null ? MemberPalette.colorForIndex(0) : member.colorHex);

            View segment = new View(requireContext());
            segment.setBackgroundColor(color);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1L, entry.getValue()));
            // A hairline of page between people, so the bar reads as parts, not a gradient.
            lp.setMarginStart(first ? 0 : Math.round(2 * dp));
            segment.setLayoutParams(lp);
            binding.splitBar.addView(segment);
            first = false;

            int percent = InsightGenerator.percentOf(entry.getValue(), total);
            ItemInsightMemberBinding row = ItemInsightMemberBinding.inflate(
                    getLayoutInflater(), binding.memberRows, false);
            row.dot.setBackgroundTintList(ColorStateList.valueOf(color));
            row.memberName.setText(name);
            row.memberShare.setText(getString(R.string.insights_row_share, percent));
            row.memberAmount.setText(money.format(entry.getValue()));
            row.getRoot().setContentDescription(getString(R.string.insights_member_description,
                    name, money.format(entry.getValue()), percent));
            binding.memberRows.addView(row.getRoot());
        }
        boolean any = !shares.isEmpty();
        binding.peopleLabel.setVisibility(any ? View.VISIBLE : View.GONE);
        binding.splitBar.setVisibility(any ? View.VISIBLE : View.GONE);
        binding.memberRows.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    /**
     * What is inside the bubble in front, biggest first, each a row that zooms to it. This is
     * also how the picture is reached without seeing it.
     */
    private void renderInside(Node node) {
        binding.insideRows.removeAllViews();
        List<Node> kids = new ArrayList<>(node.children());
        kids.sort((a, b) -> Long.compare(b.thisCents(), a.thisCents()));
        boolean any = !kids.isEmpty();
        binding.insideLabel.setVisibility(any ? View.VISIBLE : View.GONE);
        binding.insideMore.setVisibility(View.GONE);
        if (!any) {
            return;
        }
        binding.insideLabel.setText(getString(R.string.insights_inside_of,
                node.parent() == null ? monthTitle(current.month) : node.name()));
        long largest = Math.max(1L, kids.get(0).thisCents());
        long whole = Math.max(1L, node.thisCents());
        int primary = MaterialColors.getColor(binding.insideRows,
                com.google.android.material.R.attr.colorPrimary);
        int warning = ContextCompat.getColor(requireContext(), R.color.warning);
        boolean expanded = expandedFor == node;
        int shown = expanded ? kids.size() : Math.min(ROWS_AT_FIRST, kids.size());
        for (int i = 0; i < shown; i++) {
            Node kid = kids.get(i);
            ItemInsightRowBinding row = row(binding.insideRows, kid.name(),
                    money.format(kid.thisCents()),
                    getString(R.string.insights_row_share,
                            InsightGenerator.percentOf(kid.thisCents(), whole)),
                    kid.thisCents(), largest, kid.isMissing() ? warning : primary);
            row.getRoot().setContentDescription(getString(R.string.insights_chip_description,
                    kid.name(), money.format(kid.thisCents())));
            row.getRoot().setOnClickListener(v -> binding.bubbles.zoomTo(kid));
        }
        if (kids.size() > ROWS_AT_FIRST) {
            binding.insideMore.setVisibility(View.VISIBLE);
            binding.insideMore.setText(expanded ? getString(R.string.insights_show_fewer)
                    : getResources().getQuantityString(R.plurals.insights_show_all,
                    kids.size() - ROWS_AT_FIRST, kids.size() - ROWS_AT_FIRST));
            binding.insideMore.setOnClickListener(v -> {
                expandedFor = expanded ? null : node;
                renderInside(node);
            });
        }
    }

    private ItemInsightRowBinding row(ViewGroup parent, String name, String amount,
                                      String detail, long cents, long largest, int barColor) {
        ItemInsightRowBinding row = ItemInsightRowBinding.inflate(getLayoutInflater(), parent,
                false);
        row.rowName.setText(name);
        row.rowAmount.setText(amount);
        row.rowDetail.setText(detail);
        row.rowDetail.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
        // The bar is relative to the biggest row, so the top one fills the width and the
        // rest read against it; whole cents decide the weights.
        float share = Math.max(.02f, Math.min(1f, cents / (float) largest));
        ((LinearLayout.LayoutParams) row.rowBar.getLayoutParams()).weight = share;
        ((LinearLayout.LayoutParams) row.rowBarRest.getLayoutParams()).weight = 1f - share;
        row.rowBar.setBackgroundTintList(ColorStateList.valueOf(barColor));
        row.rowBar.setAlpha(.7f);
        parent.addView(row.getRoot());
        return row;
    }

    /** The facts about what is in front, as tiles; a fact seen full screen leaves the row. */
    private void renderFacts(Node node) {
        List<Insight> all = InsightGenerator.forNode(current.tree, node, todaySeed());
        binding.factRow.removeAllViews();
        int left = 0;
        for (Insight insight : all) {
            if (model.isPopped(insight.id())) {
                continue;
            }
            left++;
            InsightText.Story text = InsightText.story(requireContext(), insight, money,
                    current.tree, current.members);
            ItemInsightFactBinding tile = ItemInsightFactBinding.inflate(getLayoutInflater(),
                    binding.factRow, false);
            tile.factAbout.setText(text.chip);
            tile.factFigure.setText(text.big);
            tile.factFigure.setTextColor(accentOf(insight));
            // A change reads best as the two amounts behind it; everything else as its sentence.
            String line = insight.kind() == Insight.Kind.VS_LAST_MONTH ? text.fact : text.sub;
            tile.factLine.setText(line);
            tile.getRoot().setContentDescription(getString(R.string.insights_fact_description,
                    text.big, line));
            tile.getRoot().setOnClickListener(v -> openInsight(insight, v));
            binding.factRow.addView(tile.getRoot());
        }
        binding.factScroll.setVisibility(left > 0 ? View.VISIBLE : View.GONE);
        binding.noFacts.setVisibility(left == 0 ? View.VISIBLE : View.GONE);
        binding.bringBack.setVisibility(left < all.size() ? View.VISIBLE : View.GONE);
        binding.factsHeader.setVisibility(all.isEmpty() ? View.GONE : View.VISIBLE);
        if (all.isEmpty()) {
            binding.noFacts.setVisibility(View.GONE);
        }
    }

    /**
     * The colour a fact is drawn in. Violet for most, green when spending fell, and amber
     * only for money that is actually missing. Never red: a bigger month is not an error.
     */
    private int accentOf(Insight insight) {
        if (insight.kind() == Insight.Kind.MISSING_MONEY) {
            return ContextCompat.getColor(requireContext(), R.color.warning);
        }
        if (insight.tone() == Insight.Tone.DOWN) {
            return ContextCompat.getColor(requireContext(), R.color.success);
        }
        return ContextCompat.getColor(requireContext(), R.color.primary);
    }

    /** One fact at a time; tapping moves to the next. The daily order picks the first. */
    private void renderTrivia(List<Trivia.Fact> facts, boolean animate) {
        binding.triviaCard.setVisibility(facts.isEmpty() ? View.GONE : View.VISIBLE);
        if (facts.isEmpty()) {
            return;
        }
        int at = Math.floorMod(triviaIndex, facts.size());
        Trivia.Fact fact = facts.get(at);
        String title;
        String body;
        switch (fact.kind()) {
            case MOST_BOUGHT_ITEM:
                title = getString(R.string.trivia_most_title, fact.name());
                body = getString(R.string.trivia_most_body, fact.count(),
                        money.format(fact.cents()));
                break;
            case PRICIEST_ITEM:
                title = getString(R.string.trivia_priciest_title, fact.name());
                body = getString(R.string.trivia_priciest_body, money.format(fact.cents()));
                break;
            case BUSIEST_WEEKDAY:
                title = getString(R.string.trivia_weekday_title,
                        fact.day().getDisplayName(TextStyle.FULL, Locale.getDefault()));
                body = getResources().getQuantityString(R.plurals.trivia_weekday_body,
                        fact.count(), fact.count());
                break;
            case AVERAGE_SHOPPING_DAY:
                title = getString(R.string.trivia_average_title, money.format(fact.cents()));
                body = getString(R.string.trivia_average_body, fact.count());
                break;
            default:
                title = getResources().getQuantityString(R.plurals.trivia_logged_title,
                        fact.count(), fact.count());
                body = getString(R.string.trivia_logged_body,
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                                .withLocale(Locale.getDefault()).format(fact.since()));
        }
        binding.triviaTitle.setText(title);
        binding.triviaBody.setText(body);
        boolean more = facts.size() > 1;
        binding.triviaNext.setVisibility(more ? View.VISIBLE : View.GONE);
        binding.triviaNext.setText(getString(R.string.insights_trivia_next, at + 1,
                facts.size()));
        binding.triviaCard.setClickable(more);
        binding.triviaCard.setContentDescription(getString(R.string.insights_trivia_description,
                title, body));
        if (animate && !isAnimationOff()) {
            float dp = getResources().getDisplayMetrics().density;
            for (View v : new View[] {binding.triviaTitle, binding.triviaBody}) {
                v.setAlpha(0f);
                v.setTranslationY(8 * dp);
                v.animate().alpha(1f).translationY(0f).setDuration(320)
                        .setInterpolator(EMPHASIS).start();
            }
        }
    }

    private String monthTitle(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault())
                + " " + month.getYear();
    }

    private String dateOf(long millis) {
        return DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).format(
                java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    private static long todaySeed() {
        return LocalDate.now(ZoneId.systemDefault()).toEpochDay();
    }

    private void stepMonth(int offsetInList) {
        if (current == null) {
            return;
        }
        int index = current.months.indexOf(current.month) + offsetInList;
        if (index >= 0 && index < current.months.size()) {
            model.selectMonth(current.months.get(index));
        }
    }

    private void openOrder(long orderId) {
        Bundle args = new Bundle();
        args.putLong(ParsingArgs.ARG_ORDER_ID, orderId);
        NavHostFragment.findNavController(this).navigate(R.id.reviewItemsFragment, args);
    }

    // ------------------------------------------------------------------ BubbleView.Listener

    @Override
    public void onFocusChanged(@NonNull Node focus) {
        model.setFocusPath(focus.path());
        showFocus(focus);
    }

    // ------------------------------------------------------------------ story

    private void setUpStory() {
        binding.storyClose.setOnClickListener(v -> dismissStory(false));
        binding.storyAction.setOnClickListener(v -> dismissStory(true));
        binding.story.setOnTouchListener((v, event) -> {
            if (openStory == null || storyBusy) {
                return true;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragStartX = event.getRawX();
                    dragStartY = event.getRawY();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - dragStartX;
                    float dy = event.getRawY() - dragStartY;
                    if (Math.hypot(dx, dy) > 12 * getResources().getDisplayMetrics().density) {
                        dragging = true;
                    }
                    if (dragging) {
                        // The page follows the finger and shrinks a little, like a card.
                        float pull = (float) Math.min(1.0, Math.hypot(dx, dy)
                                / (400 * getResources().getDisplayMetrics().density));
                        binding.story.setTranslationX(dx);
                        binding.story.setTranslationY(dy);
                        binding.story.setScaleX(1f - .08f * pull);
                        binding.story.setScaleY(1f - .08f * pull);
                        binding.story.setRotation(dx * .015f);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    float dx = event.getRawX() - dragStartX;
                    float dy = event.getRawY() - dragStartY;
                    float far = 110 * getResources().getDisplayMetrics().density;
                    if (dragging && event.getActionMasked() == MotionEvent.ACTION_UP
                            && Math.hypot(dx, dy) > far) {
                        floatAway(dx, dy, false);
                    } else {
                        binding.story.animate().translationX(0f).translationY(0f)
                                .scaleX(1f).scaleY(1f).rotation(0f).setDuration(280)
                                .setInterpolator(EMPHASIS).start();
                    }
                    return true;
                }
                default:
                    return true;
            }
        });
    }

    private void openInsight(Insight insight, View from) {
        if (openStory != null || storyBusy || current == null) {
            return;
        }
        int accent = accentOf(insight);
        InsightText.Story text = InsightText.story(requireContext(), insight, money,
                current.tree, current.members);
        binding.particles.setColor(accent);
        binding.storyBig.setTextColor(accent);
        binding.storyRule.setBackgroundColor(accent);
        binding.storyChip.setText(text.chip);
        binding.storyBig.setText(text.big);
        binding.storySub.setText(text.sub);
        binding.storyBadge.setText(text.badge);
        binding.storyFact.setText(text.fact);
        binding.storyAction.setText(insight.target() != null
                ? R.string.insights_show_me : R.string.insights_nice);
        binding.story.setContentDescription(getString(R.string.insights_story_description,
                text.sub + ". " + text.badge + ". " + text.fact));

        openStory = insight;
        closeStoryOnBack.setEnabled(true);
        // Keep a screen reader inside the story while it covers the page.
        binding.scroll.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        binding.toolbar.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        binding.story.setTranslationX(0f);
        binding.story.setTranslationY(0f);
        binding.story.setRotation(0f);
        binding.story.setScaleX(1f);
        binding.story.setScaleY(1f);
        binding.story.setAlpha(1f);
        binding.story.setVisibility(View.VISIBLE);

        // Grow out of the tile that was tapped.
        int[] tileAt = new int[2];
        int[] storyAt = new int[2];
        from.getLocationOnScreen(tileAt);
        binding.story.getLocationOnScreen(storyAt);
        int cx = tileAt[0] - storyAt[0] + from.getWidth() / 2;
        int cy = tileAt[1] - storyAt[1] + from.getHeight() / 2;
        float radius = (float) Math.hypot(binding.story.getWidth(), binding.story.getHeight());
        boolean still = isAnimationOff();
        if (binding.story.getWidth() > 0 && !still) {
            Animator reveal = ViewAnimationUtils.createCircularReveal(binding.story, cx, cy,
                    Math.min(from.getWidth(), from.getHeight()) / 2f, radius);
            reveal.setDuration(560);
            reveal.setInterpolator(EMPHASIS);
            reveal.start();
            // The words follow the reveal in, one after another.
            float dp = getResources().getDisplayMetrics().density;
            View[] lines = {binding.storyBig, binding.storySub, binding.storyRule,
                    binding.storyBadge, binding.storyFact};
            for (int i = 0; i < lines.length; i++) {
                lines[i].setAlpha(0f);
                lines[i].setTranslationY(18 * dp);
                lines[i].animate().alpha(1f).translationY(0f).setStartDelay(160 + i * 60L)
                        .setDuration(460).setInterpolator(EMPHASIS).start();
            }
        }
        binding.story.post(() -> {
            if (binding == null) {
                return;
            }
            binding.storyClose.requestFocus();
            binding.story.sendAccessibilityEvent(
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        });
    }

    private void dismissStory(boolean follow) {
        if (openStory == null || storyBusy) {
            return;
        }
        floatAway(0f, 1f, follow);
    }

    /**
     * Sends the story off in the direction it was thrown, marks the fact as seen so its tile
     * leaves the row, then optionally zooms to what it was about.
     */
    private void floatAway(float dx, float dy, boolean follow) {
        if (openStory == null || storyBusy) {
            return;
        }
        storyBusy = true;
        Insight closing = openStory;
        double length = Math.max(1.0, Math.hypot(dx, dy));
        float distance = 320 * getResources().getDisplayMetrics().density;
        binding.story.animate()
                .translationX((float) (dx / length) * distance)
                .translationY((float) (dy / length) * distance)
                .scaleX(.88f).scaleY(.88f).alpha(0f).rotation(binding.story.getRotation())
                .setStartDelay(0)
                .setDuration(isAnimationOff() ? 1 : 300)
                .setInterpolator(new PathInterpolator(.3f, 0f, .8f, .15f))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (binding == null) {
                            return;
                        }
                        binding.story.animate().setListener(null);
                        binding.story.setVisibility(View.GONE);
                        binding.story.setScaleX(1f);
                        binding.story.setScaleY(1f);
                        binding.scroll.setImportantForAccessibility(
                                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
                        binding.toolbar.setImportantForAccessibility(
                                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
                        openStory = null;
                        storyBusy = false;
                        closeStoryOnBack.setEnabled(false);
                        model.markPopped(closing.id());
                        if (follow && closing.target() != null) {
                            binding.bubbles.zoomTo(closing.target());
                            binding.scroll.smoothScrollTo(0, 0);
                        } else if (binding.bubbles.focus() != null) {
                            renderFacts(binding.bubbles.focus());
                        }
                    }
                }).start();
    }

    private boolean isAnimationOff() {
        return android.provider.Settings.Global.getFloat(requireContext().getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (counting != null) {
            counting.cancel();
        }
        closeStoryOnBack.setEnabled(false);
        binding = null;
    }
}
