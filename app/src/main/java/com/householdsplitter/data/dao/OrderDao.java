package com.householdsplitter.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.householdsplitter.data.entity.DraftStep;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderStatus;
import com.householdsplitter.data.relation.OrderBundle;
import com.householdsplitter.data.relation.OrderWithMembers;

import java.util.List;

@Dao
public interface OrderDao {

    /** SPEC 7.3.2: newest first. */
    @Transaction
    @Query("SELECT * FROM orders WHERE householdId = :householdId ORDER BY orderDate DESC, id DESC")
    LiveData<List<OrderWithMembers>> observeOrders(long householdId);

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId")
    LiveData<OrderBundle> observeBundle(long orderId);

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId")
    OrderBundle getBundleSync(long orderId);

    @Transaction
    @Query("SELECT * FROM orders WHERE householdId = :householdId ORDER BY orderDate DESC, id DESC")
    List<OrderBundle> getAllBundlesSync(long householdId);

    @Query("SELECT * FROM orders WHERE id = :orderId")
    Order getByIdSync(long orderId);

    /** SPEC 8.6.7: the duplicate-import guard. */
    @Query("SELECT * FROM orders WHERE externalOrderNo = :externalOrderNo LIMIT 1")
    Order findByExternalOrderNoSync(String externalOrderNo);

    /** SPEC 8.1.3: shared into an existing draft, offer to add to it. */
    @Query("SELECT * FROM orders WHERE householdId = :householdId AND status = 'DRAFT' "
            + "ORDER BY createdAt DESC LIMIT 1")
    Order latestDraftSync(long householdId);

    @Insert
    long insert(Order order);

    @Update
    void update(Order order);

    @Query("DELETE FROM orders WHERE id = :orderId")
    void deleteById(long orderId);

    /** SPEC 7.9.14: written after every item so a relaunch resumes in place. */
    @Query("UPDATE orders SET draftStep = :step, draftItemPosition = :position WHERE id = :orderId")
    void updateProgress(long orderId, DraftStep step, int position);

    @Query("UPDATE orders SET status = :status WHERE id = :orderId")
    void updateStatus(long orderId, OrderStatus status);

    @Query("UPDATE orders SET payerMemberId = :payerMemberId WHERE id = :orderId")
    void updatePayer(long orderId, Long payerMemberId);

    @Query("UPDATE orders SET label = :label WHERE id = :orderId")
    void rename(long orderId, String label);
}
