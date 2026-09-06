package com.householdsplitter.core.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Conversion between displayed money strings and {@code long} cents (SPEC 6.2).
 *
 * <p>Parsing is done by integer string surgery rather than by {@code Double.parseDouble},
 * so no binary floating point value ever exists in the money path (SPEC 6.1). Where a
 * decimal value genuinely has to be produced for display, {@link BigDecimal} is used,
 * which is exact.
 */
public final class Cents {

    private Cents() {
    }

    /**
     * Parses a displayed amount such as {@code $53.52}, {@code 53,52}, {@code 1,234.56}
     * or {@code $0} into cents.
     *
     * <p>SPEC 8.6.4 requires tolerating an amount printed without cents. SPEC 11.13
     * requires tolerating a locale that uses {@code ,} as the decimal separator, so the
     * separator is inferred from the string rather than assumed: when both separators are
     * present the last one is decimal and the other is grouping; when only one is present
     * it is decimal only if exactly two digits follow it at the end of the string.
     *
     * @return the amount in cents
     * @throws NumberFormatException when the text holds no parsable amount
     */
    public static long parse(String text) {
        if (text == null) {
            throw new NumberFormatException("null is not an amount");
        }
        String s = text.trim();
        if (s.isEmpty()) {
            throw new NumberFormatException("empty string is not an amount");
        }

        boolean negative = false;
        // Accounting notation, occasionally produced for discounts.
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true;
            s = s.substring(1, s.length() - 1).trim();
        }

        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '-' && cleaned.length() == 0) {
                negative = true;
            } else if (c == '+' && cleaned.length() == 0) {
                continue;
            } else if (Character.isDigit(c) || c == '.' || c == ',') {
                cleaned.append(c);
            }
            // Currency symbols, spaces and stray glyphs are dropped.
        }
        String digits = cleaned.toString();
        if (digits.isEmpty()) {
            throw new NumberFormatException("no digits in '" + text + "'");
        }

        int lastDot = digits.lastIndexOf('.');
        int lastComma = digits.lastIndexOf(',');
        int decimalAt = -1;
        if (lastDot >= 0 && lastComma >= 0) {
            decimalAt = Math.max(lastDot, lastComma);
        } else if (lastDot >= 0 || lastComma >= 0) {
            int only = Math.max(lastDot, lastComma);
            int following = digits.length() - only - 1;
            if (following == 2 || following == 1) {
                decimalAt = only;
            }
        }

        String whole;
        String fraction;
        if (decimalAt >= 0) {
            whole = digits.substring(0, decimalAt);
            fraction = digits.substring(decimalAt + 1);
        } else {
            whole = digits;
            fraction = "";
        }
        whole = whole.replace(",", "").replace(".", "");
        fraction = fraction.replace(",", "").replace(".", "");

        if (fraction.length() > 2) {
            fraction = fraction.substring(0, 2);
        }
        while (fraction.length() < 2) {
            fraction = fraction + "0";
        }
        if (whole.isEmpty()) {
            whole = "0";
        }
        for (int i = 0; i < whole.length(); i++) {
            if (!Character.isDigit(whole.charAt(i))) {
                throw new NumberFormatException("not an amount: '" + text + "'");
            }
        }
        for (int i = 0; i < fraction.length(); i++) {
            if (!Character.isDigit(fraction.charAt(i))) {
                throw new NumberFormatException("not an amount: '" + text + "'");
            }
        }

        long value;
        try {
            value = Math.addExact(
                    Math.multiplyExact(Long.parseLong(whole), 100L),
                    Long.parseLong(fraction));
        } catch (ArithmeticException overflow) {
            throw new NumberFormatException("amount out of range: '" + text + "'");
        }
        return negative ? -value : value;
    }

    /** True when {@link #parse(String)} would succeed. */
    public static boolean isParsable(String text) {
        try {
            parse(text);
            return true;
        } catch (NumberFormatException notAnAmount) {
            return false;
        }
    }

    /**
     * Machine readable form: a plain decimal with two places, no grouping and no currency
     * symbol, so a spreadsheet reads it as a number (SPEC 10.1.1).
     */
    public static String toPlainString(long cents) {
        boolean negative = cents < 0L;
        long magnitude = Math.abs(cents);
        long whole = magnitude / 100L;
        long fraction = magnitude % 100L;
        StringBuilder out = new StringBuilder();
        if (negative) {
            out.append('-');
        }
        out.append(whole).append('.');
        if (fraction < 10L) {
            out.append('0');
        }
        out.append(fraction);
        return out.toString();
    }

    /** Exact decimal representation of a cents amount, for locale aware formatting. */
    public static BigDecimal toDecimal(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    /**
     * The mathematical quotient of an amount over a divisor, to two places, HALF_UP.
     * Display only: the amounts people are actually charged come from
     * {@link MoneySplitter#split}, never from this.
     */
    public static String quotientForDisplay(long cents, int divisor) {
        if (divisor <= 0) {
            throw new IllegalArgumentException("divisor must be positive");
        }
        return toDecimal(cents)
                .divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }
}
