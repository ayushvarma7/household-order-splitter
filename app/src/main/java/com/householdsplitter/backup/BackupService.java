package com.householdsplitter.backup;

import android.content.ContentResolver;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.householdsplitter.BuildConfig;
import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.AssignmentMemory;
import com.householdsplitter.data.entity.CorrectionEvent;
import com.householdsplitter.data.entity.DiscardedRow;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.ItemAssignment;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.MemberRule;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderImage;
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

/**
 * SPEC 7.14.4 and 7.14.5: the whole install as one JSON file. Everything stays on the
 * device (SPEC 1.5, 7.14.6) until the user chooses where to put that file.
 *
 * <p>The file doubles as the analysis export. It carries every group with every table that
 * belongs to it, including the two logs of what the reader got wrong (the correction log
 * and the discarded rows), so it can be loaded into a notebook as it stands and also
 * restored into another install.
 */
public class BackupService {

    /**
     * The current backup format.
     *
     * <ul>
     *   <li>1: one household and its core tables.</li>
     *   <li>2: adds settlement payments and standing rules.</li>
     *   <li>3: every household, each in a {@link Group}, plus the correction log and the
     *       discarded rows, plus the schema and app version the file was written by.</li>
     * </ul>
     *
     * Formats 1 and 2 still restore: their single household is read as a one-group file.
     */
    public static final int FORMAT_VERSION = 3;

    /** The on-disk shape. Plain fields so Gson needs no adapters. */
    public static final class Backup {

        public int version = FORMAT_VERSION;
        public long exportedAt;
        /** Room schema version of the install that wrote the file. Format 3 onwards. */
        public int databaseVersion;
        /** {@code versionName} of the app that wrote the file. Format 3 onwards. */
        public String appVersion;
        /** Every household in the install. Format 3 onwards. */
        public List<Group> groups;

        // Formats 1 and 2 kept a single household at the top level. These fields are only
        // read, never written, so an older file still restores.
        public Household household;
        public List<Member> members;
        public List<Order> orders;
        public List<OrderParticipant> participants;
        public List<LineItem> lineItems;
        public List<ItemAssignment> assignments;
        public List<OrderImage> images;
        public List<AssignmentMemory> memory;
        public List<SettlementPayment> payments;
        public List<MemberRule> rules;
    }

    /** One household and every row that belongs to it. */
    public static final class Group {

        public Household household;
        public List<Member> members = new ArrayList<>();
        public List<Order> orders = new ArrayList<>();
        public List<OrderParticipant> participants = new ArrayList<>();
        public List<LineItem> lineItems = new ArrayList<>();
        public List<ItemAssignment> assignments = new ArrayList<>();
        public List<OrderImage> images = new ArrayList<>();
        public List<AssignmentMemory> memory = new ArrayList<>();
        public List<SettlementPayment> payments = new ArrayList<>();
        public List<MemberRule> rules = new ArrayList<>();
        /** What the user corrected after a parse: the reader's error log. */
        public List<CorrectionEvent> corrections = new ArrayList<>();
        /** Parsed rows the user deleted as wrong. */
        public List<DiscardedRow> discardedRows = new ArrayList<>();
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
                List<Group> groups = groupsOf(backup);
                if (groups.isEmpty()) {
                    throw new IOException("That file is not a backup from this app");
                }
                database.runInTransaction(() -> {
                    if (replace) {
                        clearEverything();
                    }
                    for (Group group : groups) {
                        apply(group);
                    }
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
            database.runInTransaction(this::clearEverything);
            post(callback, Result.ok(null));
        });
    }

    /**
     * Deletes every household, which cascades to the tables that hang off one, and then the
     * correction log, which does not: it has no foreign key so an entry can outlive the row
     * it describes, and so a wipe has to clear it explicitly or item names stay behind.
     */
    private void clearEverything() {
        database.householdDao().deleteAll();
        database.correctionEventDao().deleteAll();
    }

    private Backup collect() {
        Backup backup = new Backup();
        backup.exportedAt = System.currentTimeMillis();
        backup.databaseVersion = database.getOpenHelper().getReadableDatabase().getVersion();
        backup.appVersion = BuildConfig.VERSION_NAME;
        backup.groups = new ArrayList<>();
        for (Household household : database.householdDao().getAllSync()) {
            backup.groups.add(collect(household));
        }
        return backup;
    }

    private Group collect(Household household) {
        Group group = new Group();
        group.household = household;
        group.members = database.memberDao().getAllSync(household.id);
        for (OrderBundle bundle : database.orderDao().getAllBundlesSync(household.id)) {
            group.orders.add(bundle.order);
            for (Member member : bundle.participants) {
                group.participants.add(new OrderParticipant(bundle.order.id, member.id));
            }
            for (LineItemWithAssignments row : bundle.items) {
                group.lineItems.add(row.item);
                group.assignments.addAll(row.assignments);
            }
            group.images.addAll(bundle.images);
        }
        group.memory = database.assignmentMemoryDao().getAllSync(household.id);
        group.payments = database.settlementDao().getForHouseholdSync(household.id);
        group.rules = database.memberRuleDao().getForHouseholdSync(household.id);
        group.corrections = database.correctionEventDao().forHouseholdSync(household.id);
        group.discardedRows = database.discardedRowDao().allForHouseholdSync(household.id);
        return group;
    }

    /** The groups in a file of any format, or none if it is not one of ours. */
    private static List<Group> groupsOf(Backup backup) {
        List<Group> groups = new ArrayList<>();
        if (backup == null) {
            return groups;
        }
        if (backup.groups != null) {
            for (Group group : backup.groups) {
                if (group != null && group.household != null) {
                    groups.add(group);
                }
            }
            return groups;
        }
        if (backup.household == null) {
            return groups;
        }
        Group legacy = new Group();
        legacy.household = backup.household;
        legacy.members = orEmpty(backup.members);
        legacy.orders = orEmpty(backup.orders);
        legacy.participants = orEmpty(backup.participants);
        legacy.lineItems = orEmpty(backup.lineItems);
        legacy.assignments = orEmpty(backup.assignments);
        legacy.images = orEmpty(backup.images);
        legacy.memory = orEmpty(backup.memory);
        // Added in format 2, so a format 1 file has no such lists at all.
        legacy.payments = orEmpty(backup.payments);
        legacy.rules = orEmpty(backup.rules);
        groups.add(legacy);
        return groups;
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    /**
     * Writes one group into the database, giving every row a fresh id and rewriting the
     * references between them.
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
    private void apply(Group group) {
        group.household.id = 0L;
        long householdId = database.householdDao().insert(group.household);

        Map<Long, Long> memberIds = new HashMap<>();
        for (Member member : group.members) {
            long oldId = member.id;
            member.id = 0L;
            member.householdId = householdId;
            memberIds.put(oldId, database.memberDao().insert(member));
        }

        Map<Long, Long> orderIds = new HashMap<>();
        for (Order order : group.orders) {
            long oldId = order.id;
            order.id = 0L;
            order.householdId = householdId;
            order.payerMemberId = order.payerMemberId == null
                    ? null : memberIds.get(order.payerMemberId);
            orderIds.put(oldId, database.orderDao().insert(order));
        }

        List<OrderParticipant> participants = new ArrayList<>();
        for (OrderParticipant participant : group.participants) {
            Long orderId = orderIds.get(participant.orderId);
            Long memberId = memberIds.get(participant.memberId);
            if (orderId != null && memberId != null) {
                participants.add(new OrderParticipant(orderId, memberId));
            }
        }
        database.participantDao().insertAll(participants);

        Map<Long, Long> lineItemIds = new HashMap<>();
        for (LineItem item : group.lineItems) {
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
        for (ItemAssignment assignment : group.assignments) {
            Long lineItemId = lineItemIds.get(assignment.lineItemId);
            Long memberId = memberIds.get(assignment.memberId);
            if (lineItemId != null && memberId != null) {
                assignments.add(new ItemAssignment(lineItemId, memberId, assignment.shares));
            }
        }
        database.assignmentDao().insertAll(assignments);

        List<OrderImage> images = new ArrayList<>();
        for (OrderImage image : group.images) {
            Long orderId = orderIds.get(image.orderId);
            if (orderId == null) {
                continue;
            }
            image.id = 0L;
            image.orderId = orderId;
            images.add(image);
        }
        database.orderImageDao().insertAll(images);

        for (AssignmentMemory memory : group.memory) {
            memory.id = 0L;
            memory.householdId = householdId;
            database.assignmentMemoryDao().insert(memory);
        }

        for (SettlementPayment payment : orEmpty(group.payments)) {
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

        for (MemberRule rule : orEmpty(group.rules)) {
            Long memberId = memberIds.get(rule.memberId);
            if (memberId == null) {
                continue;
            }
            rule.id = 0L;
            rule.householdId = householdId;
            rule.memberId = memberId;
            database.memberRuleDao().insert(rule);
        }

        // The log entries are kept even when the order they describe is no longer in the
        // file, because the observation is the point. Such a reference becomes 0, meaning
        // unknown, rather than an old id that could now name a different order.
        for (CorrectionEvent event : orEmpty(group.corrections)) {
            event.id = 0L;
            event.householdId = householdId;
            event.orderId = remapped(orderIds, event.orderId);
            event.lineItemId = remapped(lineItemIds, event.lineItemId);
            database.correctionEventDao().insert(event);
        }

        // A discarded row hangs off its order by a foreign key, so it needs one. Its own
        // line item is deleted by definition, so that reference cannot survive the move.
        for (DiscardedRow row : orEmpty(group.discardedRows)) {
            Long orderId = orderIds.get(row.orderId);
            if (orderId == null) {
                continue;
            }
            row.id = 0L;
            row.orderId = orderId;
            row.lineItemId = remapped(lineItemIds, row.lineItemId);
            database.discardedRowDao().insert(row);
        }
    }

    private static long remapped(Map<Long, Long> ids, long oldId) {
        Long newId = ids.get(oldId);
        return newId == null ? 0L : newId;
    }

    private <T> void post(Callback<T> callback, T value) {
        executors.mainThread().execute(() -> callback.onResult(value));
    }
}
