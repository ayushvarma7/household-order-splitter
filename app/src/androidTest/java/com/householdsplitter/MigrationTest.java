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

    /** Opening the real database through Room applies the migration end to end. */
    @Test
    public void theAppOpensAMigratedDatabase() throws IOException {
        helper.createDatabase(NAME, 1).close();

        AppDatabase database = Room.databaseBuilder(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        AppDatabase.class, NAME)
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .build();
        try {
            // Any query forces the open, and therefore the migration and its validation.
            assertEquals(0, database.settlementDao().getForHouseholdSync(1L).size());
        } finally {
            database.close();
        }
    }
}
