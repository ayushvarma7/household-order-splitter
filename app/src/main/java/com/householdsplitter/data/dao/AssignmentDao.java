package com.householdsplitter.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.householdsplitter.data.entity.ItemAssignment;

import java.util.List;

@Dao
public interface AssignmentDao {

    @Query("SELECT * FROM item_assignments WHERE lineItemId = :lineItemId")
    List<ItemAssignment> getForItemSync(long lineItemId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ItemAssignment> assignments);

    @Query("DELETE FROM item_assignments WHERE lineItemId = :lineItemId")
    void deleteForItem(long lineItemId);

    /** SPEC 7.8.5: a removed participant's assignments are dropped. */
    @Query("DELETE FROM item_assignments WHERE memberId = :memberId AND lineItemId IN "
            + "(SELECT id FROM line_items WHERE orderId = :orderId)")
    void deleteForMemberInOrder(long orderId, long memberId);

    /** The items that lost an assignee and need answering again (SPEC 7.8.5). */
    @Query("SELECT id FROM line_items WHERE orderId = :orderId "
            + "AND scope IN ('SUBSET', 'PERSONAL') "
            + "AND id NOT IN (SELECT lineItemId FROM item_assignments)")
    List<Long> itemsLeftWithoutAssigneesSync(long orderId);
}
