package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.householdsplitter.backup.BackupService;
import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.ItemAssignment;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.MemberRule;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderParticipant;
import com.householdsplitter.data.entity.SettlementPayment;
import com.householdsplitter.util.Result;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SPEC 7.14.4: export everything as JSON and restore it.
 *
 * <p>"Everything" is the part worth testing, and the part that quietly rots. The settlement
 * payments and standing rules tables arrived with later migrations, and a backup that omits
 * a table is worse than no backup at all: the household believes their history is covered
 * and finds out otherwise at the only moment it matters.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class BackupRoundTripTest {

    private AppDatabase database;
    private BackupService backups;
    private File file;
    private long householdId;
    private long anaId;
    private long benId;

    @Before
    public void setUp() {
        TestData.wipe();
        database = TestData.database();
        backups = TestData.locator().backupService();
        file = new File(ApplicationProvider.getApplicationContext().getCacheDir(),
                "backup-test.json");
        if (file.exists()) {
            assertTrue(file.delete());
        }
        seed();
    }

    private void seed() {
        householdId = database.householdDao().insert(new Household("Flat 12", 1L));
        anaId = database.memberDao().insert(new Member(householdId, "Ana", "#1F6FB2", 0));
        benId = database.memberDao().insert(new Member(householdId, "Ben", "#B3261E", 1));

        Order order = new Order();
        order.householdId = householdId;
        order.label = "Weekly shop";
        order.orderDate = 1_700_000_000_000L;
        order.createdAt = 1_700_000_000_000L;
        order.statedTotalCents = 2270L;
        order.taxCents = 129L;
        order.payerMemberId = anaId;
        long orderId = database.orderDao().insert(order);
        database.participantDao().insertAll(Arrays.asList(
                new OrderParticipant(orderId, anaId),
                new OrderParticipant(orderId, benId)));

        LineItem item = new LineItem();
        item.orderId = orderId;
        item.name = "Coffee beans";
        item.rawOcrText = "Coffee beans";
        item.lineTotalCents = 1299L;
        item.quantity = 1;
        item.scope = Scope.SUBSET;
        item.position = 0;
        long itemId = database.lineItemDao().insert(item);
        database.assignmentDao().insertAll(Arrays.asList(
                new ItemAssignment(itemId, anaId, 1)));

        SettlementPayment payment = new SettlementPayment(
                householdId, benId, anaId, 850L, 1_700_000_100_000L);
        payment.note = "Venmo";
        database.settlementDao().insert(payment);
        database.memberRuleDao().insert(
                new MemberRule(householdId, benId, "beer", MemberRule.Kind.EXCLUDE));
    }

    @Test
    public void everythingComesBack() throws Exception {
        export();
        TestData.wipe();
        assertEquals(0, database.householdDao().countSync());

        restore(false);

        Household household = database.householdDao().getHouseholdSync();
        assertNotNull(household);
        assertEquals("Flat 12", household.name);
        assertEquals(2, database.memberDao().getAllSync(household.id).size());
        assertEquals(1, database.orderDao().getAllBundlesSync(household.id).size());
    }

    /** The table added with database version 2. */
    @Test
    public void settlementPaymentsComeBack() throws Exception {
        export();
        TestData.wipe();

        restore(false);

        Household household = database.householdDao().getHouseholdSync();
        List<SettlementPayment> payments =
                database.settlementDao().getForHouseholdSync(household.id);
        assertEquals("a backup that omits the payment history is not a backup",
                1, payments.size());
        assertEquals(850L, payments.get(0).amountCents);
        assertEquals("Venmo", payments.get(0).note);
        assertEquals(household.id, payments.get(0).householdId);
    }

    /** The table added with database version 3. */
    @Test
    public void standingRulesComeBack() throws Exception {
        export();
        TestData.wipe();

        restore(false);

        Household household = database.householdDao().getHouseholdSync();
        List<MemberRule> rules = database.memberRuleDao().getForHouseholdSync(household.id);
        assertEquals(1, rules.size());
        assertEquals("beer", rules.get(0).keyword);
        assertEquals(MemberRule.Kind.EXCLUDE, rules.get(0).kind);
        assertEquals(household.id, rules.get(0).householdId);
    }

    /**
     * Every id is reassigned on restore, so the references between rows have to be rewritten
     * with them. A payer pointing at the wrong member would put the whole order's balances
     * on the wrong person.
     */
    @Test
    public void referencesBetweenRowsSurviveTheIdRemap() throws Exception {
        export();
        TestData.wipe();

        restore(false);

        Household household = database.householdDao().getHouseholdSync();
        var bundle = database.orderDao().getAllBundlesSync(household.id).get(0);
        assertEquals("both participants", 2, bundle.participants.size());

        long anaAfter = -1L;
        for (Member member : database.memberDao().getAllSync(household.id)) {
            if ("Ana".equals(member.name)) {
                anaAfter = member.id;
            }
        }
        assertTrue(anaAfter > 0);
        assertEquals("the payer still points at Ana",
                Long.valueOf(anaAfter), bundle.order.payerMemberId);
        assertEquals("and so does the item assignment",
                anaAfter, bundle.items.get(0).assignments.get(0).memberId);

        List<SettlementPayment> payments =
                database.settlementDao().getForHouseholdSync(household.id);
        assertEquals("the payment was to Ana", anaAfter, payments.get(0).toMemberId);
    }

    /** The item assignments are what make the split reproducible, so they must survive. */
    @Test
    public void itemAssignmentsComeBack() throws Exception {
        export();
        TestData.wipe();

        restore(false);

        Household household = database.householdDao().getHouseholdSync();
        var bundles = database.orderDao().getAllBundlesSync(household.id);
        assertEquals(1, bundles.get(0).items.size());
        assertEquals(Scope.SUBSET, bundles.get(0).items.get(0).item.scope);
        assertEquals(1, bundles.get(0).items.get(0).assignments.size());
    }

    /** A file from an older version of the app has no such lists and must still restore. */
    @Test
    public void aFormatOneBackupStillRestores() throws Exception {
        String legacy = "{\"version\":1,\"exportedAt\":1700000000000,"
                + "\"household\":{\"id\":1,\"name\":\"Old Flat\",\"createdAt\":1},"
                + "\"members\":[{\"id\":1,\"householdId\":1,\"name\":\"Ana\","
                + "\"colorHex\":\"#1F6FB2\",\"sortOrder\":0,\"archived\":false}],"
                + "\"orders\":[],\"participants\":[],\"lineItems\":[],"
                + "\"assignments\":[],\"images\":[],\"memory\":[]}";
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(legacy.getBytes(StandardCharsets.UTF_8));
        }
        TestData.wipe();

        restore(false);

        Household household = database.householdDao().getHouseholdSync();
        assertNotNull("a missing list is not a corrupt file", household);
        assertEquals("Old Flat", household.name);
        assertTrue(database.settlementDao().getForHouseholdSync(household.id).isEmpty());
        assertTrue(database.memberRuleDao().getForHouseholdSync(household.id).isEmpty());
    }

    /**
     * Nothing references these rows, so they are restored with fresh ids and a merge must
     * not fail on one that clashes with a row already present.
     */
    @Test
    public void mergingOverExistingDataDoesNotCollideOnPaymentOrRuleIds() throws Exception {
        export();

        restore(false);

        assertEquals("both copies are present, and nothing threw", 2, countOf("households"));
        assertEquals(2, countOf("settlement_payments"));
        assertEquals(2, countOf("member_rules"));
    }

    private int countOf(String table) {
        try (android.database.Cursor cursor = database.getOpenHelper().getReadableDatabase()
                .query("SELECT COUNT(*) FROM " + table)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    @Test
    public void replaceLeavesOnlyTheBackedUpHousehold() throws Exception {
        export();
        database.householdDao().insert(new Household("Somewhere else", 2L));
        assertEquals(2, countOf("households"));

        restore(true);

        assertEquals(1, countOf("households"));
        assertEquals("Flat 12", database.householdDao().getHouseholdSync().name);
    }

    @Test
    public void anUnrelatedFileIsRefusedRatherThanPartlyApplied() throws Exception {
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write("{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8));
        }

        Result<Void> result = attemptRestore(false);

        assertFalse(result.isOk());
        assertEquals("the existing data is untouched", 1, countOf("households"));
    }

    private void export() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Result<Void>> captured = new AtomicReference<>();
        backups.export(Uri.fromFile(file), result -> {
            captured.set(result);
            latch.countDown();
        });
        assertTrue(latch.await(20, TimeUnit.SECONDS));
        assertTrue("export failed: " + captured.get().error(), captured.get().isOk());
        assertTrue(file.length() > 0);
    }

    private void restore(boolean replace) throws Exception {
        Result<Void> result = attemptRestore(replace);
        assertTrue("restore failed: " + result.error(), result.isOk());
    }

    private Result<Void> attemptRestore(boolean replace) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Result<Void>> captured = new AtomicReference<>();
        backups.restore(Uri.fromFile(file), replace, result -> {
            captured.set(result);
            latch.countDown();
        });
        assertTrue(latch.await(20, TimeUnit.SECONDS));
        return captured.get();
    }
}
