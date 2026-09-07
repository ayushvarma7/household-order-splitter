package com.householdsplitter.backup;

import android.content.ContentResolver;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.AssignmentMemory;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.ItemAssignment;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderImage;
import com.householdsplitter.data.entity.OrderParticipant;
import com.householdsplitter.data.relation.LineItemWithAssignments;
import com.householdsplitter.data.relation.OrderBundle;
import com.householdsplitter.util.AppExecutors;
import com.householdsplitter.util.Callback;
import com.householdsplitter.util.Result;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** SPEC 7.14.4 and 7.14.5. Everything stays on the device (SPEC 1.5, 7.14.6). */
public class BackupService {

    /** The on-disk shape. Plain fields so Gson needs no adapters. */
    public static final class Backup {

        public int version = 1;
        public long exportedAt;
        public Household household;
        public List<Member> members = new ArrayList<>();
        public List<Order> orders = new ArrayList<>();
        public List<OrderParticipant> participants = new ArrayList<>();
        public List<LineItem> lineItems = new ArrayList<>();
        public List<ItemAssignment> assignments = new ArrayList<>();
        public List<OrderImage> images = new ArrayList<>();
        public List<AssignmentMemory> memory = new ArrayList<>();
    }

    private final AppDatabase database;
    private final AppExecutors executors;
    private final ContentResolver contentResolver;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public BackupService(AppDatabase database, AppExecutors executors,
                         ContentResolver contentResolver) {
        this.database = database;
        this.executors = executors;
        this.contentResolver = contentResolver;
    }

    /** SPEC 7.14.4: export all data as JSON. */
    public void export(Uri target, Callback<Result<Void>> callback) {
        executors.diskIO().execute(() -> {
            try {
                Backup backup = collect();
                try (OutputStream stream = contentResolver.openOutputStream(target)) {
                    if (stream == null) {
                        throw new IOException("Could not open that file for writing");
                    }
                    stream.write(gson.toJson(backup).getBytes(StandardCharsets.UTF_8));
                }
                post(callback, Result.ok(null));
            } catch (Exception failed) {
                post(callback, Result.failure(String.valueOf(failed.getMessage())));
            }
        });
    }

    /**
     * SPEC 7.14.4: restore with a merge-or-replace choice. Replace is destructive, so it
     * runs inside a transaction and the caller confirms first.
     */
    public void restore(Uri source, boolean replace, Callback<Result<Void>> callback) {
        executors.diskIO().execute(() -> {
            try (InputStream stream = contentResolver.openInputStream(source)) {
                if (stream == null) {
                    throw new IOException("Could not open that file");
                }
                Backup backup = gson.fromJson(
                        new InputStreamReader(stream, StandardCharsets.UTF_8), Backup.class);
                if (backup == null || backup.household == null) {
                    throw new IOException("That file is not a backup from this app");
                }
                database.runInTransaction(() -> {
                    if (replace) {
                        database.householdDao().deleteAll();
                    }
                    apply(backup);
                });
                post(callback, Result.ok(null));
            } catch (Exception failed) {
                post(callback, Result.failure(String.valueOf(failed.getMessage())));
            }
        });
    }

    /** SPEC 7.14.5: wipe, behind a typed confirmation in the UI. */
    public void wipe(Callback<Result<Void>> callback) {
        executors.diskIO().execute(() -> {
            database.runInTransaction(() -> database.householdDao().deleteAll());
            post(callback, Result.ok(null));
        });
    }

    private Backup collect() {
        Backup backup = new Backup();
        backup.exportedAt = System.currentTimeMillis();
        backup.household = database.householdDao().getHouseholdSync();
        if (backup.household == null) {
            return backup;
        }
        backup.members = database.memberDao().getAllSync(backup.household.id);
        for (OrderBundle bundle : database.orderDao().getAllBundlesSync(backup.household.id)) {
            backup.orders.add(bundle.order);
            for (Member member : bundle.participants) {
                backup.participants.add(new OrderParticipant(bundle.order.id, member.id));
            }
            for (LineItemWithAssignments row : bundle.items) {
                backup.lineItems.add(row.item);
                backup.assignments.addAll(row.assignments);
            }
            backup.images.addAll(bundle.images);
        }
        backup.memory = database.assignmentMemoryDao().getAllSync(backup.household.id);
        return backup;
    }

    private void apply(Backup backup) {
        long householdId = database.householdDao().insert(backup.household);
        for (Member member : backup.members) {
            member.householdId = householdId;
            database.memberDao().insert(member);
        }
        for (Order order : backup.orders) {
            order.householdId = householdId;
            database.orderDao().insert(order);
        }
        database.participantDao().insertAll(backup.participants);
        database.lineItemDao().insertAll(backup.lineItems);
        database.assignmentDao().insertAll(backup.assignments);
        database.orderImageDao().insertAll(backup.images);
        for (AssignmentMemory memory : backup.memory) {
            memory.householdId = householdId;
            database.assignmentMemoryDao().insert(memory);
        }
    }

    private <T> void post(Callback<T> callback, T value) {
        executors.mainThread().execute(() -> callback.onResult(value));
    }
}
