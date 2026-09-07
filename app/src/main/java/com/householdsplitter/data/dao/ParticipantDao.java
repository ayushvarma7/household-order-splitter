package com.householdsplitter.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.householdsplitter.data.entity.OrderParticipant;

import java.util.List;

@Dao
public abstract class ParticipantDao {

    @Query("SELECT memberId FROM order_participants WHERE orderId = :orderId")
    public abstract List<Long> memberIdsSync(long orderId);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public abstract void insertAll(List<OrderParticipant> participants);

    @Query("DELETE FROM order_participants WHERE orderId = :orderId")
    public abstract void deleteForOrder(long orderId);

    /**
     * SPEC 7.8.5: changing the participants later preserves every existing assignment
     * except those belonging to a removed participant, whose items are then unanswered
     * again. That second half is done by AssignmentRepository, which owns the scope column.
     */
    @Transaction
    public void replaceForOrder(long orderId, List<Long> memberIds) {
        deleteForOrder(orderId);
        List<OrderParticipant> rows = new java.util.ArrayList<>(memberIds.size());
        for (Long memberId : memberIds) {
            rows.add(new OrderParticipant(orderId, memberId));
        }
        insertAll(rows);
    }
}
