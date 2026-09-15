package com.householdsplitter.ui.common;

import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.widget.EditText;

import com.householdsplitter.core.money.Cents;

/**
 * SPEC 7.6.6: the price field accepts only valid currency input and converts to cents
 * without floating point.
 *
 * <p>{@link Cents} does the conversion by integer string surgery, so no {@code double} ever
 * holds a price. This class only constrains what can be typed and reads the field back.
 */
public final class CurrencyInput {

    private CurrencyInput() {
    }

    /**
     * Digits plus a single separator, at most two decimal places, and a zero that gets out
     * of the way.
     */
    public static void attach(EditText field) {
        clearZeroPlaceholderOnFocus(field);
        field.setFilters(new InputFilter[]{new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest,
                                       int dstart, int dend) {
                StringBuilder candidate = new StringBuilder(dest);
                candidate.replace(dstart, dend, source.subSequence(start, end).toString());
                String value = candidate.toString();
                if (value.isEmpty()) {
                    return null;
                }
                if (!value.matches("^-?\\d{0,9}([.,]\\d{0,2})?$")) {
                    return "";
                }
                return null;
            }
        }});
    }

    /** @return the amount in cents, or {@code fallback} when the field is empty or invalid */
    public static long readCents(EditText field, long fallback) {
        CharSequence text = field.getText();
        if (text == null || text.toString().trim().isEmpty()) {
            return fallback;
        }
        try {
            return Cents.parse(text.toString());
        } catch (NumberFormatException notAnAmount) {
            return fallback;
        }
    }

    public static void writeCents(EditText field, long cents) {
        field.setText(Cents.toPlainString(cents));
    }

    /**
     * A field showing nothing but zero empties itself when tapped, and fills itself back in
     * if it is left empty.
     *
     * <p>Most of these fields are zero most of the time: a manually added row starts at
     * 0.00, and so do tip, discount and the other fees on almost every order. Typing into
     * one meant deleting four characters first, every time, with the backspace key on a
     * numeric keyboard. So the zero steps aside on focus.
     *
     * <p>Only a zero does. A field holding a real amount keeps it, because clearing that
     * would turn a glance into an accidental deletion. And the zero comes back on the way
     * out, so the field never sits empty looking like a value went missing, and what is
     * displayed always matches what will be saved.
     */
    private static void clearZeroPlaceholderOnFocus(EditText field) {
        field.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                if (isEmpty(field) || readCents(field, 0L) == 0L) {
                    field.setText("");
                }
            } else if (isEmpty(field)) {
                writeCents(field, 0L);
            }
        });
    }

    private static boolean isEmpty(EditText field) {
        CharSequence text = field.getText();
        return text == null || text.toString().trim().isEmpty();
    }

    /**
     * Fires when the amount changes, for the live reconciliation strip of SPEC 7.7.3.
     *
     * <p>The amount, not the text. An empty field and a field reading 0.00 are the same
     * amount, because every caller reading one back passes zero as its fallback, so the
     * placeholder stepping aside and stepping back is not an edit and must not be reported
     * as one.
     *
     * <p>That distinction is load bearing rather than tidy. {@code OrderDetailsFragment}
     * uses this to drop the "from screenshot" marker from a field the user has changed, and
     * the marker is a claim about where a number came from. Reporting text churn would
     * retract that claim the moment someone tapped a zero field and tapped away again,
     * without having changed anything at all.
     */
    public static void onChange(EditText field, Runnable action) {
        final long[] lastAmount = {readCents(field, 0L)};
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                long amount = readCents(field, 0L);
                if (amount == lastAmount[0]) {
                    return;
                }
                lastAmount[0] = amount;
                action.run();
            }
        });
    }
}
