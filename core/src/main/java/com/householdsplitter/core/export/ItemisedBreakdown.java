package com.householdsplitter.core.export;

import com.householdsplitter.core.calc.AdjustmentType;
import com.householdsplitter.core.calc.result.ItemShare;
import com.householdsplitter.core.calc.result.MemberSplit;
import com.householdsplitter.core.calc.result.SplitResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Who is paying for what, item by item.
 *
 * <p>The summary answers "what does each person owe". The question underneath it is "what
 * for", and until now the only place that was answered in full was the text you get from
 * Share: you had to post the order into a group chat to read your own breakdown. The
 * per-member row does expand, but it leaves out the one list everybody is charged a slice
 * of, so "which items are in the shared pot" had no answer on the screen at all.
 *
 * <p>Structured lines rather than a block of text, so the screen can lay them out as a
 * table and a test can assert on them. The arithmetic is not redone here: every figure is
 * one the calculator already produced, because a second route to the same number is a
 * second number waiting to disagree.
 */
public final class ItemisedBreakdown {

    /** What a line is, which is all the renderer needs in order to style it. */
    public enum Kind {
        /** A heading with an optional amount, such as a person and their total. */
        SECTION,
        /** A heading inside a section, such as "Own items". */
        SUBHEADING,
        /** One item or one charge. */
        ROW,
        /** The closing figure of a section. */
        TOTAL
    }

    public static final class Line {

        private final Kind kind;
        private final String label;
        private final String note;
        private final Long cents;

        Line(Kind kind, String label, String note, Long cents) {
            this.kind = kind;
            this.label = label == null ? "" : label;
            this.note = note;
            this.cents = cents;
        }

        public Kind kind() {
            return kind;
        }

        public String label() {
            return label;
        }

        /** Context such as "3 ways", or null. */
        public String note() {
            return note;
        }

        /** Null on a line that carries no figure. */
        public Long cents() {
            return cents;
        }
    }

    private ItemisedBreakdown() {
    }

    /**
     * @param order    the order, its common rows and its computed split
     * @param sharedBy the heading for the common pot, already worded by the caller
     * @param labels   the words for the fixed lines, so no English lives in :core
     */
    public static List<Line> build(ExportOrder order, String sharedBy, Labels labels) {
        List<Line> lines = new ArrayList<>();
        if (order == null || order.result() == null) {
            return lines;
        }
        SplitResult result = order.result();

        // The shared pot first, once, in full. Every person below is charged a slice of
        // exactly this list, so printing it per person would be the same list N times.
        lines.add(new Line(Kind.SECTION, sharedBy, null, result.commonBucketCents()));
        if (order.commonItems().isEmpty()) {
            lines.add(new Line(Kind.ROW, labels.nothingShared(), null, null));
        } else {
            for (ExportOrder.NamedAmount item : order.commonItems()) {
                lines.add(new Line(Kind.ROW, item.name(), null, item.cents()));
            }
        }

        for (MemberSplit member : result.members()) {
            lines.add(new Line(Kind.SECTION, member.memberName(), null, member.finalCents()));
            lines.add(new Line(Kind.ROW, labels.shareOfShared(), null,
                    member.commonShareCents()));

            // Their own rows, which includes anything split between some of the household
            // but not all of it. The way count is what makes those legible: "Pizza, 3
            // ways" is a different fact from "Pizza", and the amount alone hides it.
            if (!member.itemShares().isEmpty()) {
                lines.add(new Line(Kind.SUBHEADING, labels.ownItems(), null, null));
                for (ItemShare share : member.itemShares()) {
                    lines.add(new Line(Kind.ROW, share.itemName(),
                            noteFor(share, labels), share.cents()));
                }
            }

            boolean anyAdjustment = false;
            for (AdjustmentType type : AdjustmentType.values()) {
                if (member.adjustmentShare(type) != 0L) {
                    anyAdjustment = true;
                    break;
                }
            }
            if (anyAdjustment) {
                lines.add(new Line(Kind.SUBHEADING, labels.taxAndFees(), null,
                        member.adjustmentsTotalCents()));
                for (AdjustmentType type : AdjustmentType.values()) {
                    long value = member.adjustmentShare(type);
                    if (value != 0L) {
                        lines.add(new Line(Kind.ROW, type.label(), null, value));
                    }
                }
            }
            lines.add(new Line(Kind.TOTAL, labels.total(), null, member.finalCents()));
        }
        return Collections.unmodifiableList(lines);
    }

    private static String noteFor(ItemShare share, Labels labels) {
        if (share.wayCount() <= 1) {
            return null;
        }
        return share.shares() > 1
                ? labels.waysWithShares(share.wayCount(), share.shares())
                : labels.ways(share.wayCount());
    }

    /**
     * The words, supplied by the caller.
     *
     * <p>:core holds no string resources and no English, so a screen passes its own
     * localised wording in rather than this module inventing any.
     */
    public interface Labels {

        String shareOfShared();

        String ownItems();

        String taxAndFees();

        String total();

        String nothingShared();

        /** For example "3 ways". */
        String ways(int wayCount);

        /** For example "3 ways, 2 shares". */
        String waysWithShares(int wayCount, int shares);
    }
}
