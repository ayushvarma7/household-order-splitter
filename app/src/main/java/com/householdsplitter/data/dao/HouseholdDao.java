package com.householdsplitter.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.householdsplitter.data.entity.Household;

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

    @Insert
    long insert(Household household);

    @Update
    void update(Household household);

    /** SPEC 7.14.5. */
    @Query("DELETE FROM households")
    void deleteAll();
}
