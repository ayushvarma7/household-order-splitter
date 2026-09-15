package com.householdsplitter.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.householdsplitter.data.converter.Converters;
import com.householdsplitter.data.dao.AssignmentDao;
import com.householdsplitter.data.dao.AssignmentMemoryDao;
import com.householdsplitter.data.dao.HouseholdDao;
import com.householdsplitter.data.dao.LineItemDao;
import com.householdsplitter.data.dao.MemberDao;
import com.householdsplitter.data.dao.MemberRuleDao;
import com.householdsplitter.data.dao.OrderDao;
import com.householdsplitter.data.dao.OrderImageDao;
import com.householdsplitter.data.dao.ParticipantDao;
import com.householdsplitter.data.dao.SettlementDao;
import com.householdsplitter.data.entity.AssignmentMemory;
import com.householdsplitter.data.entity.DiscardedRow;
import com.householdsplitter.data.dao.DiscardedRowDao;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.ItemAssignment;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.MemberRule;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderImage;
import com.householdsplitter.data.entity.OrderParticipant;
import com.householdsplitter.data.entity.SettlementPayment;

/**
 * The whole local store. SPEC 4.6.
 *
 * <p>SPEC 1.6 and PROMPT hard rule 2: there is no {@code RoomDatabase.Callback}, no
 * prepopulated asset and no seed of any kind. A fresh install opens an empty database, and
 * the first thing the user sees is an empty "Group name" field.
 *
 * <p>The schema is exported to {@code app/schemas} and checked into version control, so a
 * change to any entity shows up as a reviewable diff.
 */
@Database(
        entities = {
                Household.class,
                Member.class,
                Order.class,
                OrderImage.class,
                OrderParticipant.class,
                LineItem.class,
                ItemAssignment.class,
                AssignmentMemory.class,
                SettlementPayment.class,
                MemberRule.class,
                DiscardedRow.class
        },
        version = 6,
        exportSchema = true)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "household-splitter.db";

    public abstract HouseholdDao householdDao();

    public abstract MemberDao memberDao();

    public abstract OrderDao orderDao();

    public abstract OrderImageDao orderImageDao();

    public abstract ParticipantDao participantDao();

    public abstract LineItemDao lineItemDao();

    public abstract AssignmentDao assignmentDao();

    public abstract AssignmentMemoryDao assignmentMemoryDao();

    public abstract SettlementDao settlementDao();

    public abstract MemberRuleDao memberRuleDao();

    public abstract DiscardedRowDao discardedRowDao();

    /**
     * Adds the settlement payment history.
     *
     * <p>Written by hand rather than falling back to a destructive migration, because the
     * data this app holds cannot be recreated: nobody is going to re-photograph six months
     * of grocery orders. The statements mirror what Room generates for the entity, and the
     * schema exported to app/schemas is the check on that.
     */
    /**
     * Every migration, in one place, used by the app and by the migration tests alike.
     *
     * <p>Stated once because the alternative rots. The test that opens a version one
     * database and walks it all the way up used to repeat this list, and repeating it means
     * a migration added to the app but not to the test leaves the test passing against a
     * chain that no longer exists. Sharing the array makes forgetting impossible instead of
     * unlikely.
     */
    public static Migration[] migrations() {
        return new Migration[]{
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6
        };
    }

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `settlement_payments` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`householdId` INTEGER NOT NULL, "
                    + "`fromMemberId` INTEGER NOT NULL, "
                    + "`toMemberId` INTEGER NOT NULL, "
                    + "`amountCents` INTEGER NOT NULL, "
                    + "`paidAt` INTEGER NOT NULL, "
                    + "`note` TEXT, "
                    + "FOREIGN KEY(`householdId`) REFERENCES `households`(`id`) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE , "
                    + "FOREIGN KEY(`fromMemberId`) REFERENCES `members`(`id`) "
                    + "ON UPDATE NO ACTION ON DELETE NO ACTION , "
                    + "FOREIGN KEY(`toMemberId`) REFERENCES `members`(`id`) "
                    + "ON UPDATE NO ACTION ON DELETE NO ACTION )");
            database.execSQL("CREATE INDEX IF NOT EXISTS "
                    + "`index_settlement_payments_householdId` "
                    + "ON `settlement_payments` (`householdId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS "
                    + "`index_settlement_payments_fromMemberId` "
                    + "ON `settlement_payments` (`fromMemberId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS "
                    + "`index_settlement_payments_toMemberId` "
                    + "ON `settlement_payments` (`toMemberId`)");
        }
    };

    /**
     * Adds the delivered unit count and the standing rules table.
     *
     * <p>The count defaults to -1 rather than 0, because zero would read as "nothing was
     * delivered" on every order imported before the column existed, and the whole point of
     * the value is to be absent when it is unknown.
     */
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `orders` ADD COLUMN `deliveredUnitCount` "
                    + "INTEGER NOT NULL DEFAULT -1");
            database.execSQL("CREATE TABLE IF NOT EXISTS `member_rules` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`householdId` INTEGER NOT NULL, "
                    + "`memberId` INTEGER NOT NULL, "
                    + "`keyword` TEXT NOT NULL, "
                    + "`kind` TEXT NOT NULL DEFAULT 'EXCLUDE', "
                    + "`createdAt` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`householdId`) REFERENCES `households`(`id`) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE , "
                    + "FOREIGN KEY(`memberId`) REFERENCES `members`(`id`) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE )");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_member_rules_householdId` "
                    + "ON `member_rules` (`householdId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_member_rules_memberId` "
                    + "ON `member_rules` (`memberId`)");
        }
    };

    /**
     * Adds where each row was read from, so the app can show the user the screenshot region
     * a charge came from.
     *
     * <p>Every column has a default, so existing rows keep working: they simply report no
     * region, which is honest. Orders imported before this migration were never measured,
     * and inventing a box for them would put a red ring around the wrong part of the page.
     */
    /**
     * Records which store an order came from, so the orders list can say and a re-parse can
     * use the right vocabulary.
     *
     * <p>Every row that exists at this point was read from Walmart, because Walmart was the
     * only layout the app could read, so the default is not a guess: it is what those orders
     * are. Writing it as a column default rather than an UPDATE means an older row reports
     * its store correctly even if this migration is the last thing to touch it.
     */
    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `orders` ADD COLUMN `store` "
                    + "TEXT NOT NULL DEFAULT 'WALMART'");
        }
    };

    /**
     * Records how the reader actually did, judged by what the user changed afterwards.
     *
     * <p>Three columns and one table, and no stored percentages. The rate is computed from
     * these on demand, because a rate written into a row goes stale the moment anyone edits
     * an item and then disagrees with the data it claims to summarise.
     *
     * <p>{@code parsedName} and {@code parsedCents} hold what the reader said, so "did the
     * user change this?" stays a comparison. Existing rows get an empty snapshot and are
     * therefore not counted as read correctly: nothing is known about whether they were
     * edited, and inventing a favourable answer for the reader is the one direction this
     * must not round in.
     */
    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `line_items` ADD COLUMN `origin` "
                    + "TEXT NOT NULL DEFAULT 'PARSED'");
            database.execSQL("ALTER TABLE `line_items` ADD COLUMN `parsedName` TEXT");
            database.execSQL("ALTER TABLE `line_items` ADD COLUMN `parsedCents` "
                    + "INTEGER NOT NULL DEFAULT 0");
            database.execSQL("CREATE TABLE IF NOT EXISTS `discarded_rows` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`orderId` INTEGER NOT NULL, "
                    + "`store` TEXT NOT NULL DEFAULT 'WALMART', "
                    + "`name` TEXT NOT NULL, "
                    + "`lineTotalCents` INTEGER NOT NULL, "
                    + "`lineItemId` INTEGER NOT NULL, "
                    + "`discardedAt` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`orderId`) REFERENCES `orders`(`id`) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_discarded_rows_orderId` "
                    + "ON `discarded_rows` (`orderId`)");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `line_items` ADD COLUMN `sourceImageIndex` "
                    + "INTEGER NOT NULL DEFAULT -1");
            database.execSQL("ALTER TABLE `line_items` ADD COLUMN `boundsLeftPermille` "
                    + "INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `line_items` ADD COLUMN `boundsTopPermille` "
                    + "INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `line_items` ADD COLUMN `boundsRightPermille` "
                    + "INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `line_items` ADD COLUMN `boundsBottomPermille` "
                    + "INTEGER NOT NULL DEFAULT 0");
        }
    };


    public static AppDatabase build(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(),
                        AppDatabase.class, DATABASE_NAME)
                // SPEC 5.10 relies on the member foreign keys not cascading, so enforcement
                // has to be on for the constraint to mean anything.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                // No destructive fallback anywhere: losing a household's history to a
                // schema change is not an acceptable outcome for data nobody can recreate.
                .addMigrations(migrations())
                .build();
    }

    public static String databaseName() {
        return DATABASE_NAME;
    }
}
