package com.householdsplitter.core.insight;

/**
 * One small fact about a bubble, kept as numbers and a kind rather than as a sentence.
 *
 * <p>The words belong to the app, where they come from string resources and so can be
 * translated and given correct plurals. Keeping this class free of text also means a test
 * can assert on exactly what was found without matching a phrase.
 */
public final class Insight {

    /** What the fact is about. */
    public enum Kind {
        /** This month against last month for the bubble itself. */
        VS_LAST_MONTH,
        /** The bubble was not bought last month at all. */
        NEW_THIS_MONTH,
        /** The item inside the bubble whose spending moved the most. */
        TOP_MOVER,
        /** One person paid for noticeably more of it than anyone else. */
        WHO_BOUGHT_MORE,
        /** How big a slice of its parent it is. */
        SHARE,
        /** On the top bubble: the category that takes the biggest slice. */
        BIGGEST_SLICE,
        /** Money the bill charged that no row accounts for. Always worth a look. */
        MISSING_MONEY,
        /** The change since last month, expressed as cups of coffee. */
        COFFEES,
        /** What it comes to per day. */
        PER_DAY,
        /** How many times it went into a basket. */
        BOUGHT_TIMES
    }

    /** How the story should feel. Always shown with words as well, never colour alone. */
    public enum Tone {
        UP,
        DOWN,
        STEADY,
        INK
    }

    private final String id;
    private final Kind kind;
    private final Tone tone;
    private final InsightTree.Node node;
    private final InsightTree.Node target;
    private final long cents;
    private final long otherCents;
    private final int percent;
    private final int count;
    private final long memberId;
    private final long otherMemberId;

    Insight(Kind kind, Tone tone, InsightTree.Node node, InsightTree.Node target, long cents,
            long otherCents, int percent, int count, long memberId, long otherMemberId) {
        this.id = node.path() + "#" + kind.name();
        this.kind = kind;
        this.tone = tone;
        this.node = node;
        this.target = target;
        this.cents = cents;
        this.otherCents = otherCents;
        this.percent = percent;
        this.count = count;
        this.memberId = memberId;
        this.otherMemberId = otherMemberId;
    }

    /** Stable for a bubble and a kind, so "already popped" survives a rebuild. */
    public String id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public Tone tone() {
        return tone;
    }

    /** The bubble the fact is about. */
    public InsightTree.Node node() {
        return node;
    }

    /** Where "Show me" should zoom to, or null if there is nothing to point at. */
    public InsightTree.Node target() {
        return target;
    }

    /** The main amount: the difference, the share, or the spending, per the kind. */
    public long cents() {
        return cents;
    }

    /** The comparison amount: last month, the parent's total, or the runner-up. */
    public long otherCents() {
        return otherCents;
    }

    /** A whole percent, signed where it is a change. */
    public int percent() {
        return percent;
    }

    public int count() {
        return count;
    }

    /** The member the fact is about, or -1. */
    public long memberId() {
        return memberId;
    }

    /** The runner-up for {@link Kind#WHO_BOUGHT_MORE}, or -1. */
    public long otherMemberId() {
        return otherMemberId;
    }
}
