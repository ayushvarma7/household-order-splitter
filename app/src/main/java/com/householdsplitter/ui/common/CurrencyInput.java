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

    /** Digits plus a single separator, at most two decimal places. */
    public static void attach(EditText field) {
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

    /** Fires after every edit, for the live reconciliation strip of SPEC 7.7.3. */
    public static void onChange(EditText field, Runnable action) {
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                action.run();
            }
        });
    }
}
