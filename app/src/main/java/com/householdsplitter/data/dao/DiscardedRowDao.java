package com.householdsplitter.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.householdsplitter.data.entity.DiscardedRow;

import java.util.List;

@Dao
public interface DiscardedRowDao {

    @Insert
    long insert(DiscardedRow row);

    /** Erases the record when the user takes a deletion back. */
    @Query("DELETE FROM discarded_rows WHERE orderId = :orderId AND lineItemId = :lineItemId")
    int deleteFor(long orderId, long lineItemId);

    @Query("SELECT * FROM discarded_rows WHERE orderId = :orderId")
    List<DiscardedRow> forOrderSync(long orderId);

    @Query("SELECT * FROM discarded_rows "
            + "WHERE orderId IN (SELECT id FROM orders WHERE householdId = :householdId) "
            + "ORDER BY discardedAt DESC")
    List<DiscardedRow> allForHouseholdSync(long householdId);

    @Query("SELECT COUNT(*) FROM discarded_rows")
    int countSync();
}
