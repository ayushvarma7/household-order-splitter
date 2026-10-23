package com.householdsplitter.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.relation.LineItemWithAssignments;
import com.householdsplitter.data.relation.OrderItemNames;
import com.householdsplitter.data.relation.OrderItemTally;

import java.util.List;

@Dao
public interface LineItemDao {

    @Transaction
    @Query("SELECT * FROM line_items WHERE orderId = :orderId ORDER BY position, id")
    LiveData<List<LineItemWithAssignments>> observeForOrder(long orderId);

    @Transaction
    @Query("SELECT * FROM line_items WHERE orderId = :orderId ORDER BY position, id")
    List<LineItemWithAssignments> getForOrderSync(long orderId);

    @Query("SELECT * FROM line_items WHERE id = :itemId")
    LineItem getByIdSync(long itemId);

    /**
     * Every item name per order, for searching inside orders from the home list. Names are
     * run together with a space; SQLite's GROUP_CONCAT does the joining, so this is one
     * query however many orders there are.
     */
    @Query("SELECT orderId AS orderId, GROUP_CONCAT(name, ' ') AS names FROM line_items "
            + "WHERE orderId IN (SELECT id FROM orders WHERE householdId = :householdId) "
            + "GROUP BY orderId")
    LiveData<List<OrderItemNames>> observeItemNames(long householdId);

    /**
     * What each order's rows add up to, in one grouped pass.
     *
     * <p>Same shape of argument as {@link #observeItemNames(long)}: the home list needs a
     * fact about every order's items, and asking per order would be one query per row on
     * screen. EXCLUDED rows charge nobody, so they are left out of the sum exactly as
     * {@code Scope.isChargeable} leaves them out everywhere else.
     */
    @Query("SELECT orderId AS orderId, "
            + "COALESCE(SUM(CASE WHEN scope <> 'EXCLUDED' THEN lineTotalCents ELSE 0 END), 0) "
            + "  AS chargeableCents, "
            + "COUNT(*) AS itemCount, "
            + "COALESCE(SUM(CASE WHEN scope = 'UNASSIGNED' THEN 1 ELSE 0 END), 0) "
            + "  AS unansweredCount "
            + "FROM line_items "
            + "WHERE orderId IN (SELECT id FROM orders WHERE householdId = :householdId) "
            + "GROUP BY orderId")
    LiveData<List<OrderItemTally>> observeItemTallies(long householdId);

    /** SPEC 7.10.6: the blocking panel needs the offenders, not just a count. */
    @Query("SELECT * FROM line_items WHERE orderId = :orderId AND scope = 'UNASSIGNED' "
            + "ORDER BY position, id")
    List<LineItem> getUnassignedSync(long orderId);

    @Query("SELECT COUNT(*) FROM line_items WHERE orderId = :orderId AND scope = 'UNASSIGNED'")
    int unassignedCountSync(long orderId);

    @Query("SELECT COALESCE(MAX(position), -1) FROM line_items WHERE orderId = :orderId")
    int maxPositionSync(long orderId);

    @Insert
    long insert(LineItem item);

    @Insert
    List<Long> insertAll(List<LineItem> items);

    @Update
    void update(LineItem item);

    @Delete
    void delete(LineItem item);

    @Query("DELETE FROM line_items WHERE orderId = :orderId")
    void deleteForOrder(long orderId);

    @Query("UPDATE line_items SET scope = :scope WHERE id = :itemId")
    void updateScope(long itemId, Scope scope);

    /** Every row in the household, for judging how the reader did. */
    @Query("SELECT * FROM line_items "
            + "WHERE orderId IN (SELECT id FROM orders WHERE householdId = :householdId)")
    List<LineItem> allForHouseholdSync(long householdId);
}
