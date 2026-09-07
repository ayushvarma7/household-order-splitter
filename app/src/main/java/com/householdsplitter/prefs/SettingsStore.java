package com.householdsplitter.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import com.householdsplitter.core.calc.AllocationMode;

import java.util.Currency;
import java.util.Locale;

/** SPEC 7.14. */
public class SettingsStore {

    private static final String FILE = "settings";
    private static final String KEY_ALLOCATION = "allocation_mode";
    private static final String KEY_PARSER = "parser_cloud";
    private static final String KEY_CURRENCY = "currency_symbol";

    private final SharedPreferences preferences;
    private final String defaultCurrencySymbol;

    public SettingsStore(Context context) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
        this.defaultCurrencySymbol = defaultSymbol();
    }

    /** SPEC 7.14.1: proportional by default. */
    public AllocationMode allocationMode() {
        String stored = preferences.getString(KEY_ALLOCATION, AllocationMode.PROPORTIONAL.name());
        try {
            return AllocationMode.valueOf(stored);
        } catch (IllegalArgumentException unknown) {
            return AllocationMode.PROPORTIONAL;
        }
    }

    public void allocationMode(AllocationMode mode) {
        preferences.edit().putString(KEY_ALLOCATION, mode.name()).apply();
    }

    /** SPEC 7.14.2: on-device by default. */
    public boolean useCloudParser() {
        return preferences.getBoolean(KEY_PARSER, false);
    }

    public void useCloudParser(boolean value) {
        preferences.edit().putBoolean(KEY_PARSER, value).apply();
    }

    /** SPEC 7.14.3: defaults to the device locale's symbol. */
    public String currencySymbol() {
        return preferences.getString(KEY_CURRENCY, defaultCurrencySymbol);
    }

    public void currencySymbol(String symbol) {
        preferences.edit().putString(KEY_CURRENCY, symbol).apply();
    }

    public Locale locale() {
        return Locale.getDefault();
    }

    private static String defaultSymbol() {
        try {
            return Currency.getInstance(Locale.getDefault()).getSymbol(Locale.getDefault());
        } catch (IllegalArgumentException noCurrencyForLocale) {
            return "$";
        }
    }

    /** SPEC 7.14.5: wipe leaves the preferences at their defaults too. */
    public void clear() {
        preferences.edit().clear().apply();
    }
}
