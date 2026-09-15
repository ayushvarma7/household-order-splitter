package com.householdsplitter.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.householdsplitter.data.entity.Household;

import java.util.List;

/** SPEC 4.6: reads return LiveData, writes run on the disk executor. */
@Dao
public interface HouseholdDao {

    /** SPEC 7.1.1: first launch is "no Household row exists". */
    @Query("SELECT * FROM households ORDER BY id LIMIT 1")
    LiveData<Household> observeHousehold();

    @Query("SELECT * FROM households ORDER BY id LIMIT 1")
    Household getHouseholdSync();

    @Query("SELECT COUNT(*) FROM households")
    int countSync();

    // ---- more than one group -----------------------------------------------------------
    // Added rather than changed. observeHousehold above still returns the first row, so
    // every existing caller behaves exactly as it did, and a household that never makes a
    // second group cannot tell any of this was added.

    /** Every group, oldest first, for the switcher in the drawer. */
    @Query("SELECT * FROM households ORDER BY id")
    LiveData<List<Household>> observeAll();

    @Query("SELECT * FROM households ORDER BY id")
    List<Household> getAllSync();

    /** One group by id, for when the user has chosen which one they are looking at. */
    @Query("SELECT * FROM households WHERE id = :householdId")
    LiveData<Household> observeById(long householdId);

    @Query("SELECT * FROM households WHERE id = :householdId")
    Household getByIdSync(long householdId);

    @Query("DELETE FROM households WHERE id = :householdId")
    void deleteById(long householdId);

    @Insert
    long insert(Household household);

    @Update
    void update(Household household);

    /** SPEC 7.14.5. */
    @Query("DELETE FROM households")
    void deleteAll();
}
