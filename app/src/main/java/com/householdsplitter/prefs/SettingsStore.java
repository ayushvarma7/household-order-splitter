package com.householdsplitter.prefs;

import android.content.Context;

import com.householdsplitter.core.parse.StoreKind;
import com.householdsplitter.ui.theme.Palette;
import android.content.SharedPreferences;

import com.householdsplitter.core.calc.AllocationMode;

import java.util.Currency;
import java.util.Locale;

/** SPEC 7.14. */
public class SettingsStore {

    private static final String FILE = "settings";
    private static final String KEY_ALLOCATION = "allocation_mode";
    private static final String KEY_CURRENCY = "currency_symbol";
    private static final String KEY_WORKBOOK_URI = "workbook_uri";
    private static final String KEY_WORKBOOK_AUTO = "workbook_auto";
    private static final String KEY_AUTO_BACKUP_FOLDER = "auto_backup_folder";
    private static final String KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled";
    private static final String KEY_AUTO_BACKUP_AT = "auto_backup_at";
    private static final String KEY_WELCOME_SEEN = "welcome_seen";
    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_LAST_STORE = "last_store";
    private static final String KEY_SELF_PREFIX = "self_member_in_";
    private static final String KEY_CURRENT_HOUSEHOLD = "current_household";
    private static final String KEY_PALETTE = "palette";

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

    /**
     * Whether the introduction has been read.
     *
     * <p>Separate from whether a household exists, because those are different questions.
     * Somebody who reads the introduction and closes the app before naming their group
     * should land on the setup screen next time, not read the introduction again.
     */
    public boolean welcomeSeen() {
        return preferences.getBoolean(KEY_WELCOME_SEEN, false);
    }

    public void welcomeSeen(boolean value) {
        preferences.edit().putBoolean(KEY_WELCOME_SEEN, value).apply();
    }

    /**
     * Light, dark, or whatever the system is doing.
     *
     * <p>Stored as the AppCompat constant so there is nothing to translate between here and
     * {@code setDefaultNightMode}. Defaults to following the system, which is what the app
     * did before the setting existed.
     */
    public int themeMode() {
        return preferences.getInt(KEY_THEME,
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public void themeMode(int mode) {
        preferences.edit().putInt(KEY_THEME, mode).apply();
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

    /**
     * The store picked for the previous order, offered first next time.
     *
     * <p>A convenience, not a decision: the picker still appears and the user still
     * chooses. Remembering it is what keeps the extra tap cheap for a household that
     * orders from the same store every week, without ever parsing an order as a store
     * nobody selected.
     */
    public StoreKind lastStore() {
        return StoreKind.fromName(preferences.getString(KEY_LAST_STORE, null));
    }

    public void lastStore(StoreKind store) {
        preferences.edit().putString(KEY_LAST_STORE,
                (store == null ? StoreKind.WALMART : store).name()).apply();
    }

    /**
     * Which group the user was last looking at.
     *
     * <p>Zero means "not chosen", which is what every existing install reports and what
     * sends the app back to its previous behaviour of opening whichever group exists.
     * Only somebody who has actually switched groups ever has a value here.
     */
    public long currentHouseholdId() {
        return preferences.getLong(KEY_CURRENT_HOUSEHOLD, 0L);
    }

    public void currentHouseholdId(long householdId) {
        preferences.edit().putLong(KEY_CURRENT_HOUSEHOLD, householdId).apply();
    }

    /**
     * Which member of a group is the person holding the phone.
     *
     * <p>The app has never needed to know. A split is symmetric: it produces a figure for
     * everybody and has no opinion about which of them is reading it, and that is the right
     * shape for the arithmetic. It is the wrong shape for a sentence, though. "Ben pays Ana
     * $12.40" is an instruction to somebody, and the app cannot say which without this.
     *
     * <p>Kept per group, because you are a different row in each one. Zero means not chosen,
     * which is every existing install, and every screen that uses this has to read as well
     * without it as it did before, since most people will never set it.
     */
    public long selfMemberId(long householdId) {
        return preferences.getLong(KEY_SELF_PREFIX + householdId, 0L);
    }

    public void selfMemberId(long householdId, long memberId) {
        preferences.edit().putLong(KEY_SELF_PREFIX + householdId, memberId).apply();
    }

    /**
     * The chosen colour scheme, by name.
     *
     * <p>Absent for every existing install, which resolves to the original scheme, so
     * nobody's app changes colour because this was added.
     */
    public Palette palette() {
        return Palette.fromKey(preferences.getString(KEY_PALETTE, null));
    }

    public void palette(Palette palette) {
        preferences.edit().putString(KEY_PALETTE,
                (palette == null ? Palette.AMETHYST : palette).key()).apply();
    }
}
