package com.householdsplitter.core.export;

import com.householdsplitter.core.calc.AdjustmentType;
import com.householdsplitter.core.calc.result.ItemShare;
import com.householdsplitter.core.calc.result.MemberSplit;
import com.householdsplitter.core.calc.result.SplitResult;
import com.householdsplitter.core.export.xlsx.Cell;
import com.householdsplitter.core.export.xlsx.Sheet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the household's orders into a workbook: an overview sheet, then one sheet per
 * order, newest first.
 *
 * <p>The overview is the sheet worth keeping open. It has a row per order and a column per
 * member, so a year of shopping reads as a table that can be sorted, charted or summed with
 * no further work. Each order then gets its own sheet carrying the full detail behind its
 * row, which is what makes a figure in the overview checkable.
 *
 * <p>Every amount is a real number with a currency format, so the columns add up in the
 * spreadsheet rather than being text that looks like money.
 */
public final class OrderWorkbook {

    private static final String OVERVIEW = "All orders";

    private OrderWorkbook() {
    }

    /** One entry per order, already calculated. */
    public static final class Entry {

        private final ExportOrder order;
        private final long orderDateMillis;
        private final String status;

        public Entry(ExportOrder order, long orderDateMillis, String status) {
            this.order = order;
            this.orderDateMillis = orderDateMillis;
            this.status = status == null ? "" : status;
        }

        public ExportOrder order() {
            return order;
        }

        public long orderDateMillis() {
            return orderDateMillis;
        }

        public String status() {
            return status;
        }
    }

    public static List<Sheet> build(String groupName, List<Entry> entries) {
        List<Sheet> sheets = new ArrayList<>();
        sheets.add(overview(groupName, entries));
        for (Entry entry : entries) {
            sheets.add(orderSheet(entry));
        }
        return sheets;
    }

    private static Sheet overview(String groupName, List<Entry> entries) {
        // Column per member, in a stable order across every order, so the table is
        // rectangular even when an order had fewer participants than the household has.
        Map<String, Integer> memberColumn = new LinkedHashMap<>();
        for (Entry entry : entries) {
            for (MemberSplit member : entry.order().result().members()) {
                if (!memberColumn.containsKey(member.memberName())) {
                    memberColumn.put(member.memberName(), memberColumn.size());
                }
            }
        }

        Sheet sheet = new Sheet(OVERVIEW);
        int[] widths = new int[4 + memberColumn.size()];
        widths[0] = 26;
        widths[1] = 13;
        widths[2] = 11;
        widths[3] = 13;
        for (int i = 4; i < widths.length; i++) {
            widths[i] = 13;
        }
        sheet.widths(widths);

        sheet.row(Cell.label(groupName));
        sheet.row(Cell.text("Every order, newest first. Each order also has its own sheet."));
        sheet.blank();

        List<Cell> header = new ArrayList<>();
        header.add(Cell.header("Order"));
        header.add(Cell.header("Date"));
        header.add(Cell.header("Status"));
        header.add(Cell.header("Total"));
        for (String name : memberColumn.keySet()) {
            header.add(Cell.header(name));
        }
        sheet.row(header.toArray(new Cell[0]));

        long grandTotal = 0L;
        long[] memberTotals = new long[memberColumn.size()];

        for (Entry entry : entries) {
            SplitResult result = entry.order().result();
            List<Cell> row = new ArrayList<>();
            row.add(Cell.text(entry.order().orderLabel()));
            row.add(Cell.text(date(entry.orderDateMillis())));
            row.add(Cell.text(entry.status()));
            row.add(Cell.money(result.computedTotalCents()));
            grandTotal += result.computedTotalCents();

            Cell[] perMember = new Cell[memberColumn.size()];
            for (MemberSplit member : result.members()) {
                Integer column = memberColumn.get(member.memberName());
                if (column == null) {
                    continue;
                }
                perMember[column] = Cell.money(member.finalCents());
                memberTotals[column] += member.finalCents();
            }
            for (Cell cell : perMember) {
                row.add(cell == null ? Cell.empty() : cell);
            }
            sheet.row(row.toArray(new Cell[0]));
        }

        sheet.blank();
        List<Cell> totals = new ArrayList<>();
        totals.add(Cell.label("Total"));
        totals.add(Cell.empty());
        totals.add(Cell.empty());
        totals.add(Cell.moneyTotal(grandTotal));
        for (long value : memberTotals) {
            totals.add(Cell.moneyTotal(value));
        }
        sheet.row(totals.toArray(new Cell[0]));

        sheet.row(Cell.text("Orders"), Cell.number(entries.size()));
        return sheet;
    }

    private static Sheet orderSheet(Entry entry) {
        ExportOrder order = entry.order();
        SplitResult result = order.result();
        Sheet sheet = new Sheet(order.orderLabel());
        sheet.widths(38, 8, 12, 26, 12);

        sheet.row(Cell.label(order.orderLabel()));
        sheet.row(Cell.text("Date"), Cell.text(date(entry.orderDateMillis())));
        sheet.row(Cell.text("Status"), Cell.text(entry.status()));
        if (order.payerName() != null && !order.payerName().isEmpty()) {
            sheet.row(Cell.text("Paid by"), Cell.text(order.payerName()));
        }
        sheet.blank();

        // Shared items first, since they are the bulk of a household shop.
        sheet.row(Cell.header("Shared item"), Cell.header(""), Cell.header("Charged"),
                Cell.header(""), Cell.header(""));
        long commonTotal = 0L;
        for (ExportOrder.NamedAmount item : order.commonItems()) {
            sheet.row(Cell.text(item.name()), Cell.empty(), Cell.money(item.cents()));
            commonTotal += item.cents();
        }
        sheet.row(Cell.label("Shared total"), Cell.empty(), Cell.moneyTotal(commonTotal));
        sheet.blank();

        sheet.row(Cell.header("Item"), Cell.header("Ways"), Cell.header("Their share"),
                Cell.header("For"), Cell.header(""));
        for (MemberSplit member : result.members()) {
            for (ItemShare share : member.itemShares()) {
                sheet.row(
                        Cell.text(share.itemName()),
                        Cell.number(share.wayCount()),
                        Cell.money(share.cents()),
                        Cell.text(member.memberName()));
            }
        }
        sheet.blank();

        sheet.row(Cell.header("Person"), Cell.header("Shared"), Cell.header("Own items"),
                Cell.header("Tax and fees"), Cell.header("Owes"));
        for (MemberSplit member : result.members()) {
            sheet.row(
                    Cell.text(member.memberName()),
                    Cell.money(member.commonShareCents()),
                    Cell.money(member.itemsTotalCents()),
                    Cell.money(member.adjustmentsTotalCents()),
                    Cell.moneyTotal(member.finalCents()));
        }
        sheet.blank();

        for (AdjustmentType type : AdjustmentType.values()) {
            long value = result.adjustmentTotal(type);
            if (value != 0L) {
                sheet.row(Cell.text(type.label()), Cell.empty(), Cell.empty(), Cell.empty(),
                        Cell.money(value));
            }
        }
        sheet.row(Cell.label("Computed total"), Cell.empty(), Cell.empty(), Cell.empty(),
                Cell.moneyTotal(result.computedTotalCents()));
        if (result.statedTotalCents() != 0L) {
            sheet.row(Cell.text("Printed on the bill"), Cell.empty(), Cell.empty(), Cell.empty(),
                    Cell.money(result.statedTotalCents()));
            sheet.row(Cell.label(CsvExporter.verdict(result)));
        }
        return sheet;
    }

    private static String date(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(millis));
    }
}
