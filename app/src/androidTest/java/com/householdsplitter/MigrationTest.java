package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.Cursor;

import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.householdsplitter.data.db.AppDatabase;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * The migration from schema 1 to 2, checked against the exported schema.
 *
 * <p>This app holds data nobody can recreate: no one is going to re-photograph six months of
 * grocery orders. So there is no destructive fallback anywhere, which means every migration
 * has to be right, and the only way to know a hand-written one matches what Room expects is
 * to run it and let Room validate the result.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class MigrationTest {

    private static final String NAME = "migration-test.db";

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase.class,
            new java.util.ArrayList<>(),
            new FrameworkSQLiteOpenHelperFactory());

    /** The headline check: Room validates the resulting schema against 2.json itself. */
    @Test
    public void migratesOneToTwo() throws IOException {
        SupportSQLiteDatabase database = helper.createDatabase(NAME, 1);
        database.close();

        // runMigrationsAndValidate throws if the migration leaves the schema differing in
        // any way from the exported one, which is the whole point of running it here.
        helper.runMigrationsAndValidate(NAME, 2, true, AppDatabase.MIGRATION_1_2).close();
    }

    /** And the data that was already there survives it, which is why it exists. */
    @Test
    public void existingDataSurvivesTheMigration() throws IOException {
        SupportSQLiteDatabase database = helper.createDatabase(NAME, 1);

        ContentValues household = new ContentValues();
        household.put("id", 1L);
        household.put("name", "Fixture Group");
        household.put("createdAt", 1L);
        database.insert("households", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                household);

        ContentValues member = new ContentValues();
        member.put("id", 1L);
        member.put("householdId", 1L);
        member.put("name", "Alpha");
        member.put("colorHex", "#1F6FB2");
        member.put("sortOrder", 0);
        member.put("isArchived", 0);
        database.insert("members", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, member);
        database.close();

        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(
                NAME, 2, true, AppDatabase.MIGRATION_1_2);

        try (Cursor cursor = migrated.query("SELECT name FROM households")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("Fixture Group", cursor.getString(0));
        }
        try (Cursor cursor = migrated.query("SELECT name FROM members")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("Alpha", cursor.getString(0));
        }
        // And the new table is usable, not merely present.
        try (Cursor cursor = migrated.query("SELECT COUNT(*) FROM settlement_payments")) {
            assertTrue(cursor.moveToFirst());
            assertEquals(0, cursor.getInt(0));
        }
        migrated.close();
    }

    /** Schema 2 to 3: the delivered unit count and the standing rules table. */
    @Test
    public void migratesTwoToThree() throws IOException {
        helper.createDatabase(NAME, 2).close();
        helper.runMigrationsAndValidate(NAME, 3, true, AppDatabase.MIGRATION_2_3).close();
    }


    @Test
    public void migratesThreeToFour() throws IOException {
        helper.createDatabase(NAME, 3).close();
        helper.runMigrationsAndValidate(NAME, 4, true, AppDatabase.MIGRATION_3_4).close();
    }

    @Test
    public void migratesFiveToSix() throws IOException {
        helper.createDatabase(NAME, 5).close();
        helper.runMigrationsAndValidate(NAME, 6, true, AppDatabase.MIGRATION_5_6).close();
    }

    /**
     * A row imported before the reader was being measured is not counted as read correctly.
     *
     * <p>It has an empty snapshot, so nothing is known about whether anyone edited it. The
     * honest answer is to leave it out of the score rather than assume in the parser's
     * favour, which is the one direction a measurement of the parser must not round in.
     */
    @Test
    public void anOlderRowIsNotCountedAsReadCorrectly() throws IOException {
        SupportSQLiteDatabase database = helper.createDatabase(NAME, 5);
        ContentValues household = new ContentValues();
        household.put("id", 1L);
        household.put("name", "Fixture Group");
        household.put("createdAt", 1L);
        database.insert("households", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                household);

        ContentValues order = new ContentValues();
        order.put("id", 1L);
        order.put("householdId", 1L);
        order.put("label", "Weekly shop");
        order.put("orderDate", 1L);
        order.put("status", "DRAFT");
        order.put("createdAt", 1L);
        order.put("draftStep", "REVIEW");
        order.put("store", "WALMART");
        database.insert("orders", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, order);

        ContentValues item = new ContentValues();
        item.put("id", 1L);
        item.put("orderId", 1L);
        item.put("name", "Coffee beans");
        item.put("rawOcrText", "Coffee beans");
        item.put("lineTotalCents", 1299L);
        item.put("scope", "UNASSIGNED");
        item.put("position", 0);
        database.insert("line_items", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                item);
        database.close();

        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(NAME, 6, true,
                AppDatabase.MIGRATION_5_6);

        try (Cursor cursor = migrated.query(
                "SELECT name, origin, parsedName, parsedCents FROM line_items")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("the row itself survives", "Coffee beans", cursor.getString(0));
            assertEquals("it was the reader's output", "PARSED", cursor.getString(1));
            assertEquals("but nothing is known about what the reader said",
                    null, cursor.getString(2));
            assertEquals(0L, cursor.getLong(3));
        }
        try (Cursor cursor = migrated.query("SELECT COUNT(*) FROM discarded_rows")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("the new table exists and is empty", 0, cursor.getInt(0));
        }
        migrated.close();
    }

    @Test
    public void migratesFourToFive() throws IOException {
        helper.createDatabase(NAME, 4).close();
        helper.runMigrationsAndValidate(NAME, 5, true, AppDatabase.MIGRATION_4_5).close();
    }

    /**
     * An order that predates the store column reports Walmart, because that is what it is.
     *
     * <p>Walmart was the only layout the app could read before this column existed, so the
     * default is a statement of fact rather than a fallback. Leaving it null and showing no
     * chip would be worse: it would suggest the store is unknown when it is known exactly.
     */
    @Test
    public void anOlderOrderReportsWalmart() throws IOException {
        SupportSQLiteDatabase database = helper.createDatabase(NAME, 4);
        ContentValues household = new ContentValues();
        household.put("id", 1L);
        household.put("name", "Fixture Group");
        household.put("createdAt", 1L);
        database.insert("households", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                household);

        ContentValues order = new ContentValues();
        order.put("id", 1L);
        order.put("householdId", 1L);
        order.put("label", "Sep 03 Walmart");
        order.put("orderDate", 1L);
        order.put("status", "DRAFT");
        order.put("createdAt", 1L);
        order.put("draftStep", "REVIEW");
        database.insert("orders", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, order);
        database.close();

        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(NAME, 5, true,
                AppDatabase.MIGRATION_4_5);

        try (Cursor cursor = migrated.query("SELECT label, store FROM orders")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("the order itself survives", "Sep 03 Walmart", cursor.getString(0));
            assertEquals("WALMART", cursor.getString(1));
        }
        migrated.close();
    }

    /**
     * A row imported before the columns existed reports no screenshot region, and -1 is
     * what says so. Zero would name the first screenshot and put a red ring around the top
     * left corner of it, which is worse than offering nothing: it would be a confident
     * answer to "where did this come from?" that happens to be wrong.
     */
    @Test
    public void anOlderRowReportsNoScreenshotRegion() throws IOException {
        SupportSQLiteDatabase database = helper.createDatabase(NAME, 3);
        ContentValues household = new ContentValues();
        household.put("id", 1L);
        household.put("name", "Fixture Group");
        household.put("createdAt", 1L);
        database.insert("households", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                household);

        ContentValues order = new ContentValues();
        order.put("id", 1L);
        order.put("householdId", 1L);
        order.put("label", "Weekly shop");
        order.put("orderDate", 1L);
        order.put("status", "DRAFT");
        order.put("createdAt", 1L);
        order.put("draftStep", "REVIEW");
        database.insert("orders", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, order);

        ContentValues item = new ContentValues();
        item.put("id", 1L);
        item.put("orderId", 1L);
        item.put("name", "Coffee beans");
        item.put("rawOcrText", "Coffee beans");
        item.put("lineTotalCents", 1299L);
        item.put("scope", "UNASSIGNED");
        item.put("position", 0);
        database.insert("line_items", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                item);
        database.close();

        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(NAME, 4, true,
                AppDatabase.MIGRATION_3_4);

        try (Cursor cursor = migrated.query(
                "SELECT name, sourceImageIndex, boundsLeftPermille, boundsRightPermille "
                        + "FROM line_items")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("the row itself survives", "Coffee beans", cursor.getString(0));
            assertEquals("no screenshot region, not screenshot zero", -1, cursor.getInt(1));
            assertEquals(0, cursor.getInt(2));
            assertEquals("an empty box, which isKnown() rejects", 0, cursor.getInt(3));
        }
        migrated.close();
    }

    /** Straight from 1 to the current version, which is the path an early install takes. */
    @Test
    public void migratesAllTheWayFromOne() throws IOException {
        SupportSQLiteDatabase database = helper.createDatabase(NAME, 1);
        ContentValues household = new ContentValues();
        household.put("id", 1L);
        household.put("name", "Fixture Group");
        household.put("createdAt", 1L);
        database.insert("households", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                household);
        database.close();

        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(NAME, 4, true,
                AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4);

        try (Cursor cursor = migrated.query("SELECT name FROM households")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("Fixture Group", cursor.getString(0));
        }
        migrated.close();
    }

    /**
     * The new count defaults to -1, not 0. Zero would read as "nothing was delivered" on
     * every order imported before the column existed, and the value's whole purpose is to
     * be absent when it is unknown.
     */
    @Test
    public void theUnitCountDefaultsToUnknownNotZero() throws IOException {
        SupportSQLiteDatabase database = helper.createDatabase(NAME, 2);
        ContentValues household = new ContentValues();
        household.put("id", 1L);
        household.put("name", "Fixture Group");
        household.put("createdAt", 1L);
        database.insert("households", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                household);
        ContentValues order = new ContentValues();
        order.put("id", 1L);
        order.put("householdId", 1L);
        order.put("label", "Fixture order");
        order.put("orderDate", 1L);
        order.put("status", "DRAFT");
        order.put("createdAt", 1L);
        order.put("draftStep", "IMPORT");
        database.insert("orders", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, order);
        database.close();

        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(NAME, 3, true,
                AppDatabase.MIGRATION_2_3);
        try (Cursor cursor = migrated.query("SELECT deliveredUnitCount FROM orders")) {
            assertTrue(cursor.moveToFirst());
            assertEquals(-1, cursor.getInt(0));
        }
        migrated.close();
    }

    /** Opening the real database through Room applies every migration end to end. */
    @Test
    public void theAppOpensAMigratedDatabase() throws IOException {
        helper.createDatabase(NAME, 1).close();

        AppDatabase database = Room.databaseBuilder(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        AppDatabase.class, NAME)
                // The app's own list, not a copy of it, so a migration added to the app
                // cannot leave this test walking a chain that no longer exists.
                .addMigrations(AppDatabase.migrations())
                .build();
        try {
            // Any query forces the open, and therefore the migration and its validation.
            assertEquals(0, database.settlementDao().getForHouseholdSync(1L).size());
        } finally {
            database.close();
        }
    }
}
