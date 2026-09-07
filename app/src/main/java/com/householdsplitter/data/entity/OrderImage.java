package com.householdsplitter.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * SPEC 5.4. One row per screenshot.
 *
 * <p>SPEC 3.4: the image itself is never copied into the app, only its URI, held with a
 * persistable permission (SPEC 7.4.6) so it survives a restart and can be shown again in
 * the read-only order view (SPEC 7.12.2).
 */
@Entity(
        tableName = "order_images",
        foreignKeys = @ForeignKey(
                entity = Order.class,
                parentColumns = "id",
                childColumns = "orderId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("orderId"), @Index(value = {"orderId", "position"}, unique = true)})
public class OrderImage {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long orderId;

    @NonNull
    public String uri = "";

    /** SPEC 7.4.4: the order the user chose, which drives stitching (SPEC 8.7.1). */
    public int position;

    public OrderImage() {
    }

    public OrderImage(long orderId, @NonNull String uri, int position) {
        this.orderId = orderId;
        this.uri = uri;
        this.position = position;
    }
}
