package com.householdsplitter.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.householdsplitter.data.converter.Converters;
import com.householdsplitter.data.dao.AssignmentDao;
import com.householdsplitter.data.dao.AssignmentMemoryDao;
import com.householdsplitter.data.dao.HouseholdDao;
import com.householdsplitter.data.dao.LineItemDao;
import com.householdsplitter.data.dao.MemberDao;
import com.householdsplitter.data.dao.OrderDao;
import com.householdsplitter.data.dao.OrderImageDao;
import com.householdsplitter.data.dao.ParticipantDao;
import com.householdsplitter.data.entity.AssignmentMemory;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.ItemAssignment;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderImage;
import com.householdsplitter.data.entity.OrderParticipant;

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
                AssignmentMemory.class
        },
        version = 1,
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

    public static AppDatabase build(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(),
                        AppDatabase.class, DATABASE_NAME)
                // SPEC 5.10 relies on the member foreign keys not cascading, so enforcement
                // has to be on for the constraint to mean anything.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build();
    }

    public static String databaseName() {
        return DATABASE_NAME;
    }
}
