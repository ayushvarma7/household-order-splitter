package com.householdsplitter.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.householdsplitter.data.entity.SettlementPayment;

import java.util.List;

@Dao
public interface SettlementDao {

    /** Newest first, which is the order a history list wants. */
    @Query("SELECT * FROM settlement_payments WHERE householdId = :householdId "
            + "ORDER BY paidAt DESC, id DESC")
    LiveData<List<SettlementPayment>> observeForHousehold(long householdId);

    @Query("SELECT * FROM settlement_payments WHERE householdId = :householdId "
            + "ORDER BY paidAt DESC, id DESC")
    List<SettlementPayment> getForHouseholdSync(long householdId);

    @Insert
    long insert(SettlementPayment payment);

    @Query("DELETE FROM settlement_payments WHERE id = :paymentId")
    void deleteById(long paymentId);

    /** SPEC 7.14.5: a wipe takes the payment history with it. */
    @Query("DELETE FROM settlement_payments WHERE householdId = :householdId")
    void clearForHousehold(long householdId);
}
