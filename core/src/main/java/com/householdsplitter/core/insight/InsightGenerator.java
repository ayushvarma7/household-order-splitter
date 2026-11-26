package com.householdsplitter.core.insight;

import com.householdsplitter.core.insight.Insight.Kind;
import com.householdsplitter.core.insight.Insight.Tone;
import com.householdsplitter.core.insight.InsightTree.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Picks the few things worth saying about a bubble.
 *
 * <p>What it finds depends on where you are: inside Snacks it talks about snacks, on a single
 * item it talks about that item. There are never more than four, because they float around
 * the edge of a small screen and each one has to be tappable.
 *
 * <p>The last of the four is the fun one, chosen by the calendar day. Opening the screen a
 * second time on the same day shows the same fact, and tomorrow shows a different one, which
 * is the point: there is a reason to look again.
 */
public final class InsightGenerator {

    /** Roughly what a cup of coffee costs, for turning a change into something to picture. */
    public static final long COFFEE_CENTS = 500L;

    /** Below this a change is "about the same". One tenth of last month. */
    private static final long STEADY_TENTHS = 1L;

    /** A lead has to be this big a share of the total before it is worth saying. */
    private static final long LEAD_PERCENT = 15L;

    /** At least this much a day, in cents, before "per day" is worth saying. */
    private static final long MIN_PER_DAY_CENTS = 100L;

    private static final int MAX_INSIGHTS = 4;

    private InsightGenerator() {
    }

    /**
     * @param daySeed the number of the day (for example {@code LocalDate.toEpochDay()}),
     *                which decides which fun fact is shown
     */
    public static List<Insight> forNode(InsightTree tree, Node node, long daySeed) {
        List<Insight> core = new ArrayList<>();
        addMissingMoney(tree, node, core);
        if (!node.isMissing()) {
            addVersusLastMonth(tree, node, core);
        }
        addShare(node, core);
        if (!node.isMissing()) {
            addTopMover(tree, node, core);
        }
        addWhoBoughtMore(node, core);

        Insight fun = pickFun(tree, node, daySeed);
        int room = fun == null ? MAX_INSIGHTS : MAX_INSIGHTS - 1;
        List<Insight> out = new ArrayList<>(core.subList(0, Math.min(room, core.size())));
        if (fun != null) {
            out.add(fun);
        }
        return out;
    }

    /** Which way a change points, with one tenth either side counting as steady. */
    public static Tone toneOf(long thisCents, long lastCents) {
        if (lastCents <= 0L) {
            return Tone.STEADY;
        }
        if ((thisCents - lastCents) * 10L > lastCents * STEADY_TENTHS) {
            return Tone.UP;
        }
        if ((lastCents - thisCents) * 10L > lastCents * STEADY_TENTHS) {
            return Tone.DOWN;
        }
        return Tone.STEADY;
    }

    /** The change as a whole percent, rounded half away from zero. */
    public static int percentChange(long thisCents, long lastCents) {
        if (lastCents <= 0L) {
            return 0;
        }
        long numerator = (thisCents - lastCents) * 100L;
        long half = lastCents / 2L;
        return (int) ((numerator + (numerator < 0L ? -half : half)) / lastCents);
    }

    /** A part of a whole as a whole percent, rounded to nearest. */
    public static int percentOf(long part, long whole) {
        if (whole <= 0L) {
            return 0;
        }
        return (int) ((part * 100L + whole / 2L) / whole);
    }

    /**
     * Said first, on the top bubble and on the missing bubble itself, because it is the one
     * figure here that is a problem to fix and not just a fact: money was charged and the
     * rows do not show where it went.
     */
    private static void addMissingMoney(InsightTree tree, Node node, List<Insight> out) {
        Node missing = tree.missing();
        if (missing == null || (node.parent() != null && node != missing)) {
            return;
        }
        out.add(new Insight(Kind.MISSING_MONEY, Tone.UP, node, node == missing ? null : missing,
                missing.thisCents(), tree.root().thisCents(),
                percentOf(missing.thisCents(), tree.root().thisCents()),
                missing.purchaseCount(), -1L, -1L));
    }

    private static void addVersusLastMonth(InsightTree tree, Node node, List<Insight> out) {
        if (!tree.hasPreviousMonth()) {
            return;
        }
        if (node.lastCents() > 0L) {
            out.add(new Insight(Kind.VS_LAST_MONTH, toneOf(node.thisCents(), node.lastCents()),
                    node, null, node.thisCents(), node.lastCents(),
                    percentChange(node.thisCents(), node.lastCents()), 0, -1L, -1L));
        } else {
            out.add(new Insight(Kind.NEW_THIS_MONTH, Tone.STEADY, node, null,
                    node.thisCents(), 0L, 0, 0, -1L, -1L));
        }
    }

    private static void addTopMover(InsightTree tree, Node node, List<Insight> out) {
        if (!tree.hasPreviousMonth() || node.isLeaf()) {
            return;
        }
        Node best = null;
        long bestMove = 0L;
        for (Node leaf : leaves(node)) {
            if (leaf.lastCents() <= 0L) {
                continue;
            }
            long move = Math.abs(leaf.thisCents() - leaf.lastCents());
            if (move > bestMove) {
                bestMove = move;
                best = leaf;
            }
        }
        if (best == null) {
            return;
        }
        Tone tone = best.thisCents() > best.lastCents() ? Tone.UP : Tone.DOWN;
        out.add(new Insight(Kind.TOP_MOVER, tone, node, best, bestMove, best.lastCents(),
                percentChange(best.thisCents(), best.lastCents()), 0, -1L, -1L));
    }

    private static void addWhoBoughtMore(Node node, List<Insight> out) {
        Map<Long, Long> shares = node.thisByMember();
        if (shares.size() < 2) {
            return;
        }
        long topId = -1L;
        long topCents = -1L;
        long secondId = -1L;
        long secondCents = -1L;
        for (Map.Entry<Long, Long> entry : shares.entrySet()) {
            long cents = entry.getValue();
            if (cents > topCents) {
                secondId = topId;
                secondCents = topCents;
                topId = entry.getKey();
                topCents = cents;
            } else if (cents > secondCents) {
                secondId = entry.getKey();
                secondCents = cents;
            }
        }
        long gap = topCents - secondCents;
        if (gap * 100L < node.thisCents() * LEAD_PERCENT) {
            return;
        }
        out.add(new Insight(Kind.WHO_BOUGHT_MORE, Tone.INK, node, null, gap, secondCents,
                percentOf(topCents, node.thisCents()), 0, topId, secondId));
    }

    private static void addShare(Node node, List<Insight> out) {
        if (node.parent() != null) {
            out.add(new Insight(Kind.SHARE, Tone.INK, node, null, node.thisCents(),
                    node.parent().thisCents(),
                    percentOf(node.thisCents(), node.parent().thisCents()), 0, -1L, -1L));
        } else if (!node.children().isEmpty()) {
            Node top = node.children().get(0);
            out.add(new Insight(Kind.BIGGEST_SLICE, Tone.INK, node, top, top.thisCents(),
                    node.thisCents(), percentOf(top.thisCents(), node.thisCents()), 0,
                    -1L, -1L));
        }
    }

    private static Insight pickFun(InsightTree tree, Node node, long daySeed) {
        List<Insight> candidates = new ArrayList<>();

        if (tree.hasPreviousMonth() && node.lastCents() > 0L && !node.isMissing()) {
            long change = node.thisCents() - node.lastCents();
            if (Math.abs(change) >= COFFEE_CENTS) {
                candidates.add(new Insight(Kind.COFFEES, change < 0L ? Tone.DOWN : Tone.UP,
                        node, null, Math.abs(change), node.lastCents(),
                        percentChange(node.thisCents(), node.lastCents()),
                        (int) (Math.abs(change) / COFFEE_CENTS), -1L, -1L));
            }
        }
        long perDay = node.thisCents() / tree.month().lengthOfMonth();
        if (perDay >= MIN_PER_DAY_CENTS) {
            candidates.add(new Insight(Kind.PER_DAY, Tone.INK, node, null, perDay,
                    node.thisCents(), 0, tree.month().lengthOfMonth(), -1L, -1L));
        }
        if (node.isLeaf() && node.purchaseCount() >= 2) {
            candidates.add(new Insight(Kind.BOUGHT_TIMES, Tone.INK, node, null,
                    node.thisCents(), 0L, 0, node.purchaseCount(), -1L, -1L));
        }
        if (candidates.isEmpty()) {
            return null;
        }
        // Mix the bubble in, so two bubbles on the same day do not always show the same kind.
        long mixed = daySeed + node.path().hashCode();
        return candidates.get((int) Math.floorMod(mixed, (long) candidates.size()));
    }

    private static List<Node> leaves(Node node) {
        List<Node> out = new ArrayList<>();
        collect(node, out);
        return out;
    }

    private static void collect(Node node, List<Node> out) {
        if (node.isLeaf()) {
            out.add(node);
            return;
        }
        for (Node child : node.children()) {
            collect(child, out);
        }
    }
}
