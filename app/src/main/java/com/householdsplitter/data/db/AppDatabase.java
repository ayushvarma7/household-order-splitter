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
                MemberRule.class
        },
        version = 3,
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

    /**
     * Adds the settlement payment history.
     *
     * <p>Written by hand rather than falling back to a destructive migration, because the
     * data this app holds cannot be recreated: nobody is going to re-photograph six months
     * of grocery orders. The statements mirror what Room generates for the entity, and the
     * schema exported to app/schemas is the check on that.
     */
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

    public static AppDatabase build(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(),
                        AppDatabase.class, DATABASE_NAME)
                // SPEC 5.10 relies on the member foreign keys not cascading, so enforcement
                // has to be on for the constraint to mean anything.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                // No destructive fallback anywhere: losing a household's history to a
                // schema change is not an acceptable outcome for data nobody can recreate.
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build();
    }

    public static String databaseName() {
        return DATABASE_NAME;
    }
}
