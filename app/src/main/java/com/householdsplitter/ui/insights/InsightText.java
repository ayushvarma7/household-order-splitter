package com.householdsplitter.ui.insights;

import android.content.Context;

import com.householdsplitter.R;
import com.householdsplitter.core.insight.Insight;
import com.householdsplitter.core.insight.InsightGenerator;
import com.householdsplitter.core.insight.InsightTree;
import com.householdsplitter.core.insight.InsightTree.Node;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.data.entity.Member;

import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;

/**
 * The words for an {@link Insight}.
 *
 * <p>The core finds the fact and keeps it as numbers. Here it becomes a sentence from string
 * resources, so it can be translated and pluralised properly, and so a wording change never
 * touches the code that decided what was worth saying.
 */
final class InsightText {

    /** What fits inside a small floating bubble. */
    static final class Label {
        final String big;
        final String tiny;

        Label(String big, String tiny) {
            this.big = big;
            this.tiny = tiny;
        }
    }

    /** What the full-screen story says. */
    static final class Story {
        String big = "";
        String tiny = "";
        String chip = "";
        String sub = "";
        String badge = "";
        String fact = "";
    }

    private InsightText() {
    }

    /** {@code $14} for whole amounts and {@code $14.50} otherwise, small enough for a bubble. */
    static String compact(CurrencyFormat money, long cents) {
        if (cents % 100L == 0L) {
            String whole = NumberFormat.getIntegerInstance().format(Math.abs(cents) / 100L);
            return (cents < 0L ? "-" : "") + money.symbol() + whole;
        }
        return money.format(cents);
    }

    static String monthShort(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault());
    }

    private static String signed(CurrencyFormat money, long cents, boolean up) {
        return (up ? "+" : "-") + compact(money, Math.abs(cents));
    }

    private static String signedPercent(int percent) {
        return (percent > 0 ? "+" : "") + percent + "%";
    }

    static Label label(Context context, Insight insight, CurrencyFormat money) {
        android.content.res.Resources res = context.getResources();
        switch (insight.kind()) {
            case VS_LAST_MONTH:
                return new Label(signedPercent(insight.percent()),
                        context.getString(R.string.insight_vs_tiny, "last"));
            case NEW_THIS_MONTH:
                return new Label(compact(money, insight.cents()),
                        context.getString(R.string.insight_new_tiny));
            case TOP_MOVER:
                return new Label(signed(money, insight.cents(), insight.tone() == Insight.Tone.UP),
                        context.getString(R.string.insight_mover_tiny));
            case WHO_BOUGHT_MORE:
                return new Label(compact(money, insight.cents()),
                        context.getString(R.string.insight_who_tiny));
            case SHARE:
                return new Label(insight.percent() + "%",
                        context.getString(R.string.insight_share_tiny));
            case BIGGEST_SLICE:
                return new Label(insight.percent() + "%",
                        context.getString(R.string.insight_slice_tiny));
            case MISSING_MONEY:
                return new Label(compact(money, insight.cents()),
                        context.getString(R.string.insight_missing_tiny));
            case COFFEES:
                return new Label(String.valueOf(insight.count()),
                        res.getQuantityString(R.plurals.insight_coffees_tiny, insight.count()));
            case PER_DAY:
                return new Label(compact(money, insight.cents()),
                        context.getString(R.string.insight_day_tiny));
            default:
                return new Label(String.valueOf(insight.count()),
                        context.getString(R.string.insight_times_tiny));
        }
    }

    static Story story(Context context, Insight insight, CurrencyFormat money, InsightTree tree,
                       Map<Long, Member> members) {
        android.content.res.Resources res = context.getResources();
        Story out = new Story();
        Label label = label(context, insight, money);
        out.big = label.big;
        out.tiny = label.tiny;
        Node node = insight.node();
        String name = node.name();
        String previous = monthShort(tree.previousMonth());
        out.tiny = label.tiny;

        switch (insight.kind()) {
            case VS_LAST_MONTH: {
                out.tiny = context.getString(R.string.insight_vs_tiny, previous);
                out.chip = context.getString(R.string.insight_vs_chip, name);
                out.sub = context.getString(R.string.insight_vs_sub, name, previous);
                int percent = Math.abs(insight.percent());
                switch (insight.tone()) {
                    case UP:
                        out.badge = context.getString(R.string.insight_vs_badge_up, percent,
                                previous);
                        break;
                    case DOWN:
                        out.badge = context.getString(R.string.insight_vs_badge_down, percent,
                                previous);
                        break;
                    default:
                        out.badge = context.getString(R.string.insight_vs_badge_steady, previous);
                }
                out.fact = context.getString(R.string.insight_vs_fact,
                        money.format(insight.cents()), money.format(insight.otherCents()));
                break;
            }
            case NEW_THIS_MONTH:
                out.chip = context.getString(R.string.insight_new_chip, name);
                out.sub = context.getString(R.string.insight_new_sub, name);
                out.badge = context.getString(R.string.insight_new_badge, previous);
                out.fact = context.getString(R.string.insight_new_fact,
                        money.format(insight.cents()));
                break;
            case TOP_MOVER: {
                boolean up = insight.tone() == Insight.Tone.UP;
                Node target = insight.target();
                long last = insight.otherCents();
                long now = up ? last + insight.cents() : last - insight.cents();
                out.chip = context.getString(R.string.insight_mover_chip, name);
                out.sub = context.getString(R.string.insight_mover_sub, target.name());
                out.badge = context.getString(up ? R.string.insight_mover_badge_up
                                : R.string.insight_mover_badge_down, Math.abs(insight.percent()),
                        compact(money, last), compact(money, now));
                out.fact = context.getString(R.string.insight_mover_fact,
                        target.parent().name());
                break;
            }
            case WHO_BOUGHT_MORE: {
                String leader = memberName(context, members, insight.memberId());
                String second = memberName(context, members, insight.otherMemberId());
                long leaderCents = insight.otherCents() + insight.cents();
                String of = node.depth() == 0
                        ? context.getString(R.string.insights_everything) : name.toLowerCase();
                out.chip = context.getString(R.string.insight_who_chip, name);
                out.sub = context.getString(R.string.insight_who_sub, leader,
                        compact(money, insight.cents()), of);
                out.badge = context.getString(R.string.insight_who_badge, leader,
                        money.format(leaderCents), second, money.format(insight.otherCents()));
                out.fact = context.getString(R.string.insight_who_fact,
                        money.format(node.thisCents()));
                break;
            }
            case SHARE: {
                String parent = node.parent().depth() == 0
                        ? context.getString(R.string.insights_everything_you_bought)
                        : node.parent().name().toLowerCase();
                out.chip = context.getString(R.string.insight_share_chip, name);
                out.sub = context.getString(R.string.insight_share_sub, name, insight.percent(),
                        parent);
                out.badge = context.getString(R.string.insight_share_badge,
                        money.format(insight.cents()), money.format(insight.otherCents()));
                out.fact = context.getString(insight.percent() >= 40
                        ? R.string.insight_share_fact_big : R.string.insight_share_fact_small);
                break;
            }
            case BIGGEST_SLICE:
                out.chip = context.getString(R.string.insight_slice_chip);
                out.sub = context.getString(R.string.insight_slice_sub,
                        insight.target().name());
                out.badge = context.getString(R.string.insight_slice_badge,
                        money.format(insight.cents()), money.format(insight.otherCents()));
                out.fact = context.getString(R.string.insight_slice_fact);
                break;
            case MISSING_MONEY:
                out.chip = context.getString(R.string.insight_missing_chip);
                out.sub = context.getString(R.string.insight_missing_sub,
                        money.format(insight.cents()));
                out.badge = res.getQuantityString(R.plurals.insight_missing_badge,
                        insight.count(), insight.count());
                out.fact = context.getString(insight.target() != null
                        ? R.string.insight_missing_fact_root : R.string.insight_missing_fact_here);
                break;
            case COFFEES: {
                out.chip = context.getString(R.string.insight_fun_chip);
                out.sub = context.getString(insight.tone() == Insight.Tone.UP
                        ? R.string.insight_coffees_sub_up : R.string.insight_coffees_sub_down,
                        insight.count(), previous);
                out.badge = context.getString(R.string.insight_coffees_badge,
                        money.format(insight.cents()));
                out.fact = context.getString(R.string.insight_coffees_fact,
                        money.format(InsightGenerator.COFFEE_CENTS));
                break;
            }
            case PER_DAY:
                out.chip = context.getString(R.string.insight_fun_chip);
                out.sub = context.getString(R.string.insight_day_sub, name);
                out.badge = context.getString(R.string.insight_day_badge,
                        money.format(insight.otherCents()), insight.count());
                out.fact = context.getString(R.string.insight_day_fact);
                break;
            default:
                out.chip = context.getString(R.string.insight_fun_chip);
                out.sub = context.getString(R.string.insight_times_sub, name, insight.count());
                out.badge = context.getString(R.string.insight_times_badge,
                        money.format(insight.cents()));
                out.fact = context.getString(R.string.insight_times_fact);
        }
        return out;
    }

    private static String memberName(Context context, Map<Long, Member> members, long id) {
        Member member = members.get(id);
        return member == null || member.name == null || member.name.isEmpty()
                ? context.getString(R.string.insights_someone) : member.name;
    }
}
