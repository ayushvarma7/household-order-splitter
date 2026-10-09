package com.householdsplitter.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.householdsplitter.data.entity.CorrectionEvent;

import java.util.List;

/** Append and read; nothing here edits an observation after the fact. */
@Dao
public interface CorrectionEventDao {

    @Insert
    long insert(CorrectionEvent event);

    /** Only for attaching a verdict once the images have been re-read. */
    @Update
    void update(CorrectionEvent event);

    @Query("SELECT * FROM correction_events WHERE id = :id")
    CorrectionEvent byIdSync(long id);

    @Query("SELECT * FROM correction_events WHERE householdId = :householdId "
            + "ORDER BY createdAt DESC")
    List<CorrectionEvent> forHouseholdSync(long householdId);

    @Query("SELECT * FROM correction_events WHERE householdId = :householdId "
            + "ORDER BY createdAt DESC")
    LiveData<List<CorrectionEvent>> observeForHousehold(long householdId);

    @Query("SELECT * FROM correction_events WHERE orderId = :orderId ORDER BY createdAt")
    List<CorrectionEvent> forOrderSync(long orderId);

    @Query("SELECT COUNT(*) FROM correction_events WHERE householdId = :householdId")
    int countForHousehold(long householdId);

    /** The most recent, for a report that has to stay readable. */
    @Query("SELECT * FROM correction_events WHERE householdId = :householdId "
            + "ORDER BY createdAt DESC LIMIT :limit")
    List<CorrectionEvent> recentSync(long householdId, int limit);

    @Query("DELETE FROM correction_events WHERE householdId = :householdId")
    void clearForHousehold(long householdId);
}
