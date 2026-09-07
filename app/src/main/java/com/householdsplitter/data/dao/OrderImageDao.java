package com.householdsplitter.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.householdsplitter.data.entity.OrderImage;

import java.util.List;

@Dao
public interface OrderImageDao {

    @Query("SELECT * FROM order_images WHERE orderId = :orderId ORDER BY position")
    LiveData<List<OrderImage>> observeForOrder(long orderId);

    @Query("SELECT * FROM order_images WHERE orderId = :orderId ORDER BY position")
    List<OrderImage> getForOrderSync(long orderId);

    @Insert
    void insertAll(List<OrderImage> images);

    @Query("DELETE FROM order_images WHERE orderId = :orderId")
    void deleteForOrder(long orderId);
}
