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
    private static final String KEY_WORKBOOK_URI = "workbook_uri";
    private static final String KEY_WORKBOOK_AUTO = "workbook_auto";
    private static final String KEY_AUTO_BACKUP_FOLDER = "auto_backup_folder";
    private static final String KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled";
    private static final String KEY_AUTO_BACKUP_AT = "auto_backup_at";

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

    /**
     * Where the Excel workbook lives, as a document URI the user picked once. Null until
     * they choose a file. The app rewrites that one file whenever the orders change, which
     * is how each order ends up as its own sheet without the user exporting anything.
     */
    public String workbookUri() {
        return preferences.getString(KEY_WORKBOOK_URI, null);
    }

    public void workbookUri(String uri) {
        preferences.edit().putString(KEY_WORKBOOK_URI, uri).apply();
    }

    /** Keep the workbook up to date on its own whenever an order changes. */
    public boolean workbookAutoUpdate() {
        return preferences.getBoolean(KEY_WORKBOOK_AUTO, true);
    }

    public void workbookAutoUpdate(boolean value) {
        preferences.edit().putBoolean(KEY_WORKBOOK_AUTO, value).apply();
    }

    /**
     * The folder automatic backups are written to, as a persisted SAF tree URI, or null.
     * A folder rather than a file, because a backup that overwrites itself is not a backup:
     * the failure it has to survive is a bad write.
     */
    public String autoBackupFolderUri() {
        return preferences.getString(KEY_AUTO_BACKUP_FOLDER, null);
    }

    public void autoBackupFolderUri(String uri) {
        preferences.edit().putString(KEY_AUTO_BACKUP_FOLDER, uri).apply();
    }

    public boolean autoBackupEnabled() {
        return preferences.getBoolean(KEY_AUTO_BACKUP_ENABLED, true);
    }

    public void autoBackupEnabled(boolean value) {
        preferences.edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, value).apply();
    }

    /** When the last automatic backup was written, or 0. Used to keep it to once a day. */
    public long lastAutoBackupAt() {
        return preferences.getLong(KEY_AUTO_BACKUP_AT, 0L);
    }

    public void lastAutoBackupAt(long millis) {
        preferences.edit().putLong(KEY_AUTO_BACKUP_AT, millis).apply();
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
