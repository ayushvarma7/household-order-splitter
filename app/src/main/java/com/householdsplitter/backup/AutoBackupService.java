package com.householdsplitter.backup;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.householdsplitter.core.backup.BackupRotation;
import com.householdsplitter.prefs.SettingsStore;
import com.householdsplitter.util.AppExecutors;
import com.householdsplitter.util.Callback;
import com.householdsplitter.util.Result;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps a rolling set of dated JSON backups in a folder the user picked.
 *
 * <p>SPEC 7.14.4 gives the user a manual export, which only helps the households that
 * remember to use it. The data here is unrecreatable: an order's screenshots are gone from
 * the phone's gallery long before the household stops arguing about who owed what. So the
 * backup happens on its own, once a day, at the moments the app already knows something
 * changed.
 *
 * <p>Deliberately not a scheduled job. WorkManager is outside SPEC 4.11's library budget,
 * and a household's data only changes when they use the app, so there is nothing for a
 * background wake-up to find. This runs on the same hook the workbook already uses.
 *
 * <p>Everything stays on the device (SPEC 1.5, 7.14.6): the standard flavour has no network
 * permission, so the folder is local or a sync app's local folder, and the app never knows
 * the difference.
 */
public class AutoBackupService {

    private final BackupService backups;
    private final SettingsStore settings;
    private final AppExecutors executors;
    private final ContentResolver contentResolver;

    public AutoBackupService(BackupService backups, SettingsStore settings,
                             AppExecutors executors, ContentResolver contentResolver) {
        this.backups = backups;
        this.settings = settings;
        this.executors = executors;
        this.contentResolver = contentResolver;
    }

    public boolean hasFolder() {
        return settings.autoBackupFolderUri() != null;
    }

    /** Remembers a folder the user just granted, and takes the first backup straight away. */
    public void useFolder(Uri folder, Callback<Result<String>> callback) {
        settings.autoBackupFolderUri(folder.toString());
        // Zeroed so the first backup is not skipped by a stale timestamp from a previous
        // folder. Choosing a folder and seeing nothing appear in it would be alarming.
        settings.lastAutoBackupAt(0L);
        backupNow(callback);
    }

    public void forget() {
        settings.autoBackupFolderUri(null);
        settings.lastAutoBackupAt(0L);
    }

    /**
     * Takes a backup if one is due. Silent, including on failure: this runs after settling
     * an order, and a household that has not set a folder should not be nagged, nor should
     * a failed write interrupt what they were doing. The Settings screen shows the date of
     * the last one, which is where an absence becomes visible.
     */
    public void backupIfDue() {
        if (!settings.autoBackupEnabled() || settings.autoBackupFolderUri() == null) {
            return;
        }
        if (!BackupRotation.isDue(settings.lastAutoBackupAt(), System.currentTimeMillis(),
                ZoneId.systemDefault())) {
            return;
        }
        backupNow(result -> {
        });
    }

    /** Writes a backup now, whether or not one is due, and reports the file name. */
    public void backupNow(Callback<Result<String>> callback) {
        String folderUri = settings.autoBackupFolderUri();
        if (folderUri == null) {
            post(callback, Result.failure("Choose a folder for backups first"));
            return;
        }
        long now = System.currentTimeMillis();
        String name = BackupRotation.nameFor(now, ZoneId.systemDefault());
        Uri tree = Uri.parse(folderUri);

        executors.diskIO().execute(() -> {
            try {
                Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                        tree, DocumentsContract.getTreeDocumentId(tree));
                Uri target = findChild(children, name);
                if (target == null) {
                    target = DocumentsContract.createDocument(
                            contentResolver, children, "application/json", name);
                }
                if (target == null) {
                    post(callback, Result.failure("Could not create a file in that folder"));
                    return;
                }
                Uri file = target;
                // The export is written first. Pruning only after it succeeds means a
                // failed write never costs the household an old backup as well.
                backups.export(file, exported -> {
                    if (!exported.isOk()) {
                        callback.onResult(Result.failure(exported.error()));
                        return;
                    }
                    settings.lastAutoBackupAt(now);
                    executors.diskIO().execute(() -> {
                        prune(children, name);
                        post(callback, Result.ok(name));
                    });
                });
            } catch (Exception failed) {
                post(callback, Result.failure(String.valueOf(failed.getMessage())));
            }
        });
    }

    /** How many of the app's own backups are in the folder, or -1 if it cannot be read. */
    public void countBackups(Callback<Integer> callback) {
        String folderUri = settings.autoBackupFolderUri();
        if (folderUri == null) {
            post(callback, 0);
            return;
        }
        Uri tree = Uri.parse(folderUri);
        executors.diskIO().execute(() -> {
            try {
                Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                        tree, DocumentsContract.getTreeDocumentId(tree));
                int count = 0;
                for (String name : listNames(children)) {
                    if (BackupRotation.isBackupName(name)) {
                        count++;
                    }
                }
                post(callback, count);
            } catch (Exception failed) {
                post(callback, -1);
            }
        });
    }

    private void prune(Uri children, String newName) {
        try {
            for (String doomed : BackupRotation.toDelete(listNames(children), newName,
                    BackupRotation.DEFAULT_KEEP)) {
                Uri file = findChild(children, doomed);
                if (file != null) {
                    DocumentsContract.deleteDocument(contentResolver, file);
                }
            }
        } catch (Exception ignored) {
            // A folder that cannot be pruned is a folder with too many backups in it, which
            // is not a problem worth failing a successful backup over.
        }
    }

    private List<String> listNames(Uri children) {
        List<String> names = new ArrayList<>();
        try (Cursor cursor = contentResolver.query(children, new String[]{
                DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                names.add(cursor.getString(0));
            }
        }
        return names;
    }

    private Uri findChild(Uri children, String name) {
        try (Cursor cursor = contentResolver.query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                if (name.equals(cursor.getString(1))) {
                    return DocumentsContract.buildDocumentUriUsingTree(children,
                            cursor.getString(0));
                }
            }
        }
        return null;
    }

    private <T> void post(Callback<T> callback, T value) {
        executors.mainThread().execute(() -> callback.onResult(value));
    }
}
