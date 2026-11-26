package com.householdsplitter.splitwise;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Everything Splitwise-related the app remembers.
 *
 * <p>The API key is the only secret. It is sealed with an AES key that lives in the
 * Android Keystore and never leaves it, so the preferences file on its own holds nothing
 * usable. The app's data is also excluded from Android backup (allowBackup is off).
 *
 * <p>The group link, the member mapping and the sent records are plain preferences, keyed
 * by id. They belong to one connected account, so disconnecting clears all of them: a
 * different account would have different user ids and expense ids.
 */
final class SplitwiseStore {

    private static final String FILE = "splitwise";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "splitwise_api_key";
    private static final String K_KEY = "api_key_sealed";
    private static final String K_USER_ID = "user_id";
    private static final String K_USER_NAME = "user_name";
    private static final String K_CURRENCY = "currency";
    private static final String GROUP = "group_";
    private static final String GROUP_NAME = "group_name_";
    private static final String MEMBER = "member_";
    private static final String EXPENSE = "expense_";
    private static final String SENT_AT = "sent_at_";

    private final SharedPreferences prefs;

    SplitwiseStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    boolean isConnected() {
        return prefs.contains(K_KEY) && prefs.getLong(K_USER_ID, 0L) != 0L;
    }

    /** The key, or null when none is stored or the Keystore can no longer open it. */
    @Nullable
    String apiKey() {
        String sealed = prefs.getString(K_KEY, null);
        if (sealed == null) {
            return null;
        }
        try {
            byte[] all = Base64.decode(sealed, Base64.NO_WRAP);
            byte[] iv = new byte[12];
            System.arraycopy(all, 0, iv, 0, iv.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(all, iv.length, all.length - iv.length),
                    StandardCharsets.UTF_8);
        } catch (Exception unreadable) {
            return null;
        }
    }

    boolean saveConnection(String apiKey, long userId, String userName, String currency) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey());
            byte[] iv = cipher.getIV();
            byte[] sealed = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(sealed, 0, all, iv.length, sealed.length);
            prefs.edit()
                    .putString(K_KEY, Base64.encodeToString(all, Base64.NO_WRAP))
                    .putLong(K_USER_ID, userId)
                    .putString(K_USER_NAME, userName)
                    .putString(K_CURRENCY, currency)
                    .apply();
            return true;
        } catch (Exception cannotSeal) {
            return false;
        }
    }

    void clear() {
        prefs.edit().clear().apply();
        try {
            KeyStore store = KeyStore.getInstance(KEYSTORE);
            store.load(null);
            store.deleteEntry(ALIAS);
        } catch (Exception ignored) {
            // The sealed key is gone either way, so a leftover Keystore entry is inert.
        }
    }

    long userId() {
        return prefs.getLong(K_USER_ID, 0L);
    }

    String userName() {
        return prefs.getString(K_USER_NAME, "");
    }

    /** The account's default currency code, e.g. USD, or empty. */
    String currency() {
        return prefs.getString(K_CURRENCY, "");
    }

    long groupId(long householdId) {
        return prefs.getLong(GROUP + householdId, 0L);
    }

    String groupName(long householdId) {
        return prefs.getString(GROUP_NAME + householdId, "");
    }

    void saveGroup(long householdId, long groupId, String name) {
        prefs.edit().putLong(GROUP + householdId, groupId)
                .putString(GROUP_NAME + householdId, name).apply();
    }

    /** The Splitwise user id a member is linked to, or 0. */
    long memberUser(long memberId) {
        return prefs.getLong(MEMBER + memberId, 0L);
    }

    void saveMemberUser(long memberId, long userId) {
        SharedPreferences.Editor edit = prefs.edit();
        if (userId == 0L) {
            edit.remove(MEMBER + memberId);
        } else {
            edit.putLong(MEMBER + memberId, userId);
        }
        edit.apply();
    }

    /** The expense an order was sent as, or 0. */
    long expenseId(long orderId) {
        return prefs.getLong(EXPENSE + orderId, 0L);
    }

    long sentAt(long orderId) {
        return prefs.getLong(SENT_AT + orderId, 0L);
    }

    void saveSent(long orderId, long expenseId, long at) {
        prefs.edit().putLong(EXPENSE + orderId, expenseId).putLong(SENT_AT + orderId, at).apply();
    }

    private static SecretKey secretKey() throws Exception {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        if (store.containsAlias(ALIAS)) {
            return (SecretKey) store.getKey(ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
