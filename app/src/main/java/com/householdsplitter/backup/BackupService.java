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
import com.householdsplitter.data.entity.MemberRule;
import com.householdsplitter.data.entity.OrderParticipant;
import com.householdsplitter.data.entity.SettlementPayment;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** SPEC 7.14.4 and 7.14.5. Everything stays on the device (SPEC 1.5, 7.14.6). */
public class BackupService {

    /**
     * The current backup format. Version 1 predates the settlement payments and standing
     * rules tables; a version 1 file simply has no such lists, and reading one leaves them
     * empty rather than failing, so an older backup still restores.
     */
    public static final int FORMAT_VERSION = 2;

    /** The on-disk shape. Plain fields so Gson needs no adapters. */
    public static final class Backup {

        public int version = FORMAT_VERSION;
        public long exportedAt;
        public Household household;
        public List<Member> members = new ArrayList<>();
        public List<Order> orders = new ArrayList<>();
        public List<OrderParticipant> participants = new ArrayList<>();
        public List<LineItem> lineItems = new ArrayList<>();
        public List<ItemAssignment> assignments = new ArrayList<>();
        public List<OrderImage> images = new ArrayList<>();
        public List<AssignmentMemory> memory = new ArrayList<>();
        /** Added in format 2, with database version 2. */
        public List<SettlementPayment> payments = new ArrayList<>();
        /** Added in format 2, with database version 3. */
        public List<MemberRule> rules = new ArrayList<>();
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
        // Both of these arrived with later migrations. Leaving them out would make the
        // backup quietly incomplete, which is worse than no backup: the user would believe
        // their settlement history and their standing rules were covered.
        backup.payments = database.settlementDao().getForHouseholdSync(backup.household.id);
        backup.rules = database.memberRuleDao().getForHouseholdSync(backup.household.id);
        return backup;
    }

    /**
     * Writes a backup's contents into the database, giving every row a fresh id and
     * rewriting the references between them.
     *
     * <p>Remapping rather than preserving ids is what makes the merge of SPEC 7.14.4 work
     * at all. The common case is a household restoring their own backup into the install
     * it came from, where every id in the file is already taken; inserting them as they
     * stand fails on the household row and gets no further.
     *
     * <p>Replace mode empties the tables first, so the ids would be free, but it takes the
     * same path deliberately. One code path that always remaps is easier to be sure of than
     * two that differ only in a case that is hard to test.
     */
    private void apply(Backup backup) {
        backup.household.id = 0L;
        long householdId = database.householdDao().insert(backup.household);

        Map<Long, Long> memberIds = new HashMap<>();
        for (Member member : backup.members) {
            long oldId = member.id;
            member.id = 0L;
            member.householdId = householdId;
            memberIds.put(oldId, database.memberDao().insert(member));
        }

        Map<Long, Long> orderIds = new HashMap<>();
        for (Order order : backup.orders) {
            long oldId = order.id;
            order.id = 0L;
            order.householdId = householdId;
            order.payerMemberId = order.payerMemberId == null
                    ? null : memberIds.get(order.payerMemberId);
            orderIds.put(oldId, database.orderDao().insert(order));
        }

        List<OrderParticipant> participants = new ArrayList<>();
        for (OrderParticipant participant : backup.participants) {
            Long orderId = orderIds.get(participant.orderId);
            Long memberId = memberIds.get(participant.memberId);
            if (orderId != null && memberId != null) {
                participants.add(new OrderParticipant(orderId, memberId));
            }
        }
        database.participantDao().insertAll(participants);

        Map<Long, Long> lineItemIds = new HashMap<>();
        for (LineItem item : backup.lineItems) {
            Long orderId = orderIds.get(item.orderId);
            if (orderId == null) {
                continue;
            }
            long oldId = item.id;
            item.id = 0L;
            item.orderId = orderId;
            lineItemIds.put(oldId, database.lineItemDao().insert(item));
        }

        List<ItemAssignment> assignments = new ArrayList<>();
        for (ItemAssignment assignment : backup.assignments) {
            Long lineItemId = lineItemIds.get(assignment.lineItemId);
            Long memberId = memberIds.get(assignment.memberId);
            if (lineItemId != null && memberId != null) {
                assignments.add(new ItemAssignment(lineItemId, memberId, assignment.shares));
            }
        }
        database.assignmentDao().insertAll(assignments);

        List<OrderImage> images = new ArrayList<>();
        for (OrderImage image : backup.images) {
            Long orderId = orderIds.get(image.orderId);
            if (orderId == null) {
                continue;
            }
            image.id = 0L;
            image.orderId = orderId;
            images.add(image);
        }
        database.orderImageDao().insertAll(images);

        for (AssignmentMemory memory : backup.memory) {
            memory.id = 0L;
            memory.householdId = householdId;
            database.assignmentMemoryDao().insert(memory);
        }

        // Both of these arrived with later migrations, so a file from format 1 has no such
        // list at all and Gson leaves the field null rather than empty.
        if (backup.payments != null) {
            for (SettlementPayment payment : backup.payments) {
                Long from = memberIds.get(payment.fromMemberId);
                Long to = memberIds.get(payment.toMemberId);
                if (from == null || to == null) {
                    continue;
                }
                payment.id = 0L;
                payment.householdId = householdId;
                payment.fromMemberId = from;
                payment.toMemberId = to;
                database.settlementDao().insert(payment);
            }
        }
        if (backup.rules != null) {
            for (MemberRule rule : backup.rules) {
                Long memberId = memberIds.get(rule.memberId);
                if (memberId == null) {
                    continue;
                }
                rule.id = 0L;
                rule.householdId = householdId;
                rule.memberId = memberId;
                database.memberRuleDao().insert(rule);
            }
        }
    }

    private <T> void post(Callback<T> callback, T value) {
        executors.mainThread().execute(() -> callback.onResult(value));
    }
}
