package com.householdsplitter.core.money;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Human readable money, for the UI and for the share text (SPEC 10.2).
 *
 * <p>SPEC 11.13: the grouping and decimal separators come from the locale. The value fed
 * to the formatter is an exact {@link java.math.BigDecimal} built from cents, never a
 * {@code double}.
 */
public final class CurrencyFormat {

    private final String symbol;
    private final DecimalFormat format;

    public CurrencyFormat(String symbol, Locale locale) {
        this.symbol = symbol == null ? "" : symbol;
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(locale));
        decimalFormat.setParseBigDecimal(true);
        this.format = decimalFormat;
    }

    /** e.g. {@code $1.66}. */
    public String format(long cents) {
        boolean negative = cents < 0L;
        String body = format.format(Cents.toDecimal(Math.abs(cents)));
        return (negative ? "-" : "") + symbol + body;
    }

    /** e.g. {@code 1.66}, without the currency symbol. */
    public String formatBare(long cents) {
        boolean negative = cents < 0L;
        return (negative ? "-" : "") + format.format(Cents.toDecimal(Math.abs(cents)));
    }

    /** Signed form used for reconciliation deltas (SPEC 7.7.3). */
    public String formatSigned(long cents) {
        if (cents > 0L) {
            return "+" + format(cents);
        }
        return format(cents);
    }

    public String symbol() {
        return symbol;
    }
}
