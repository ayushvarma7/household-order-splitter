package com.householdsplitter.core.export;

import com.householdsplitter.core.calc.AdjustmentType;
import com.householdsplitter.core.calc.result.ItemShare;
import com.householdsplitter.core.calc.result.MemberSplit;
import com.householdsplitter.core.calc.result.SplitResult;
import com.householdsplitter.core.money.Cents;

import java.util.ArrayList;
import java.util.List;

/**
 * The CSV of SPEC 10.1, mirroring the spreadsheet this app replaces.
 *
 * <p>Member columns are generated from the household's actual members, however many there
 * are (SPEC 10.1, PROMPT hard rule 6): the common block occupies columns 0 and 1, and
 * member <em>m</em> occupies columns {@code 4 + 3m} and {@code 5 + 3m} with a blank spacer
 * between, which is what the worked layout in SPEC 10.1 lays out for three members.
 *
 * <p>Amounts are plain two-place decimals with no currency symbol so a spreadsheet reads
 * them as numbers (SPEC 10.1.1), and every field is quoted per RFC 4180 when it needs to
 * be (SPEC 10.1.2).
 */
public final class CsvExporter {

    private static final int COMMON_NAME_COLUMN = 0;
    private static final int COMMON_COST_COLUMN = 1;
    private static final int FIRST_MEMBER_COLUMN = 4;
    private static final int COLUMNS_PER_MEMBER = 3;

    private CsvExporter() {
    }

    /** SPEC 7.11.2. */
    public static String export(ExportOrder order) {
        StringBuilder out = new StringBuilder();
        appendOrder(out, order);
        return out.toString();
    }

    /**
     * SPEC 7.11.3: "Export all" writes every order into one file, separated by a blank
     * line and a header row per order.
     */
    public static String exportAll(List<ExportOrder> orders) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < orders.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            ExportOrder order = orders.get(i);
            out.append(row(new String[]{label(order)})).append('\n');
            appendOrder(out, order);
        }
        return out.toString();
    }

    private static String label(ExportOrder order) {
        if (order.groupName().isEmpty()) {
            return order.orderLabel();
        }
        return order.groupName() + " - " + order.orderLabel();
    }

    private static void appendOrder(StringBuilder out, ExportOrder order) {
        SplitResult result = order.result();
        List<MemberSplit> members = result.members();
        int width = FIRST_MEMBER_COLUMN + COLUMNS_PER_MEMBER * Math.max(1, members.size());

        // Header 1: Common,Cost,,,<Member1>,,,<Member2>,...
        String[] header = new String[width];
        header[COMMON_NAME_COLUMN] = "Common";
        header[COMMON_COST_COLUMN] = "Cost";
        for (int m = 0; m < members.size(); m++) {
            header[memberNameColumn(m)] = members.get(m).memberName();
        }
        out.append(row(header)).append('\n');

        // Header 2: Item,Cost,,,Item,Cost,,Item,Cost,...
        String[] subHeader = new String[width];
        subHeader[COMMON_NAME_COLUMN] = "Item";
        subHeader[COMMON_COST_COLUMN] = "Cost";
        for (int m = 0; m < members.size(); m++) {
            subHeader[memberNameColumn(m)] = "Item";
            subHeader[memberCostColumn(m)] = "Cost";
        }
        out.append(row(subHeader)).append('\n');

        // Body: common items down the left, each member's own items down their column.
        int rows = order.commonItems().size();
        for (MemberSplit member : members) {
            rows = Math.max(rows, member.itemShares().size());
        }
        for (int r = 0; r < rows; r++) {
            String[] line = new String[width];
            if (r < order.commonItems().size()) {
                line[COMMON_NAME_COLUMN] = order.commonItems().get(r).name();
                line[COMMON_COST_COLUMN] = Cents.toPlainString(order.commonItems().get(r).cents());
            }
            for (int m = 0; m < members.size(); m++) {
                List<ItemShare> shares = members.get(m).itemShares();
                if (r < shares.size()) {
                    ItemShare share = shares.get(r);
                    String name = share.shares() > 1
                            ? share.itemName() + " (x" + share.shares() + " share)"
                            : share.itemName();
                    line[memberNameColumn(m)] = name;
                    line[memberCostColumn(m)] = Cents.toPlainString(share.cents());
                }
            }
            out.append(row(line)).append('\n');
        }

        // The common block's own totals.
        out.append(row(new String[]{"Total", Cents.toPlainString(result.commonBucketCents())}))
                .append('\n');
        out.append(row(new String[]{
                        "Split between " + result.participantCount(),
                        Cents.quotientForDisplay(result.commonBucketCents(),
                                Math.max(1, result.participantCount()))}))
                .append('\n');
        for (AdjustmentType type : AdjustmentType.values()) {
            long amount = result.adjustmentTotal(type);
            if (amount != 0L) {
                out.append(row(new String[]{type.label(), Cents.toPlainString(amount)})).append('\n');
            }
        }

        // Per-member footer: pre-tax, then one row per non-zero adjustment, then the final.
        out.append(row(memberFooterRow(width, members, "Total", null))).append('\n');
        for (AdjustmentType type : AdjustmentType.values()) {
            if (result.adjustmentTotal(type) != 0L) {
                out.append(row(memberFooterRow(width, members, type.label() + " share", type)))
                        .append('\n');
            }
        }
        out.append(row(finalRow(width, members))).append('\n');

        out.append(row(new String[]{
                "Total Bill",
                Cents.toPlainString(result.computedTotalCents()),
                verdict(result)})).append('\n');
    }

    private static String[] memberFooterRow(int width, List<MemberSplit> members, String caption,
                                            AdjustmentType type) {
        String[] line = new String[width];
        for (int m = 0; m < members.size(); m++) {
            line[memberNameColumn(m)] = caption;
            long value = type == null
                    ? members.get(m).preTaxCents()
                    : members.get(m).adjustmentShare(type);
            line[memberCostColumn(m)] = Cents.toPlainString(value);
        }
        return line;
    }

    private static String[] finalRow(int width, List<MemberSplit> members) {
        String[] line = new String[width];
        for (int m = 0; m < members.size(); m++) {
            line[memberNameColumn(m)] = "Total with tax";
            line[memberCostColumn(m)] = Cents.toPlainString(members.get(m).finalCents());
        }
        return line;
    }

    /** SPEC 7.10.3 and 10.1: the reconciliation verdict, in words. */
    public static String verdict(SplitResult result) {
        if (result.matchesStatedTotal()) {
            return "Matches bill amount";
        }
        return "Off by " + Cents.toPlainString(result.deltaCents());
    }

    private static int memberNameColumn(int memberIndex) {
        return FIRST_MEMBER_COLUMN + COLUMNS_PER_MEMBER * memberIndex;
    }

    private static int memberCostColumn(int memberIndex) {
        return memberNameColumn(memberIndex) + 1;
    }

    /** RFC 4180 (SPEC 10.1.2), with trailing empty fields trimmed to keep the file legible. */
    static String row(String[] cells) {
        int last = -1;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] != null && !cells[i].isEmpty()) {
                last = i;
            }
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i <= last; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(escape(cells[i]));
        }
        return out.toString();
    }

    static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needsQuotes = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /** Convenience for building the common-item column from a split result. */
    public static List<ExportOrder.NamedAmount> noCommonItems() {
        return new ArrayList<>();
    }
}
