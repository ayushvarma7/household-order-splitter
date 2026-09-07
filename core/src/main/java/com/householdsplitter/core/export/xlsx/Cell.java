package com.householdsplitter.core.export.xlsx;

/**
 * One spreadsheet cell.
 *
 * <p>A money cell carries its value as a number with a currency format, never as text, so
 * the spreadsheet can sum a column. The value is built from {@code long} cents by exact
 * decimal string construction, so no {@code double} ever touches the money path on the way
 * out (SPEC 6.1).
 */
public final class Cell {

    public enum Kind {
        /** Plain text. */
        TEXT,
        /** Bold text on a tinted background: a header. */
        HEADER,
        /** A number formatted as money. */
        MONEY,
        /** A number formatted as money, in bold: a total. */
        MONEY_TOTAL,
        /** A plain number. */
        NUMBER,
        /** Bold text, no fill: a section label inside the sheet. */
        LABEL
    }

    private final Kind kind;
    private final String text;
    private final String numeric;

    private Cell(Kind kind, String text, String numeric) {
        this.kind = kind;
        this.text = text;
        this.numeric = numeric;
    }

    public static Cell empty() {
        return new Cell(Kind.TEXT, "", null);
    }

    public static Cell text(String value) {
        return new Cell(Kind.TEXT, value == null ? "" : value, null);
    }

    public static Cell header(String value) {
        return new Cell(Kind.HEADER, value == null ? "" : value, null);
    }

    public static Cell label(String value) {
        return new Cell(Kind.LABEL, value == null ? "" : value, null);
    }

    public static Cell number(long value) {
        return new Cell(Kind.NUMBER, null, Long.toString(value));
    }

    /** @param cents the amount, exactly as stored */
    public static Cell money(long cents) {
        return new Cell(Kind.MONEY, null, com.householdsplitter.core.money.Cents.toPlainString(cents));
    }

    public static Cell moneyTotal(long cents) {
        return new Cell(Kind.MONEY_TOTAL, null,
                com.householdsplitter.core.money.Cents.toPlainString(cents));
    }

    public Kind kind() {
        return kind;
    }

    public String text() {
        return text;
    }

    public String numeric() {
        return numeric;
    }

    public boolean isNumeric() {
        return numeric != null;
    }

    public boolean isBlank() {
        return numeric == null && (text == null || text.isEmpty());
    }
}
