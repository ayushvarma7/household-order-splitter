package com.householdsplitter.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.householdsplitter.core.parse.StoreKind;

/**
 * A row the reader produced and the user threw away.
 *
 * <p>The one thing about the reader's performance that cannot be recomputed from the live
 * data, which is the only reason this table exists. Whether a row was left alone, corrected
 * or typed by hand is all still sitting in {@code line_items} and is worked out on demand.
 * A deleted row is gone, so if it is not written down here the charge the reader invented
 * leaves no trace at all, and the score would quietly improve every time someone cleaned up
 * after it.
 *
 * <p>Deliberately not a soft delete on {@code line_items}. A hidden flag would mean every
 * existing query had to remember to exclude discarded rows, and the cost of one forgetting
 * is a deleted charge reappearing inside a split. A separate table cannot be joined into a
 * money calculation by accident.
 *
 * <p>Deleting a row is undoable, so a discard is erased again if the user takes it back.
 * Counting an undone deletion would be counting a mistake the user did not make.
 */
@Entity(
        tableName = "discarded_rows",
        foreignKeys = @ForeignKey(
                entity = Order.class,
                parentColumns = "id",
                childColumns = "orderId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("orderId")})
public class DiscardedRow {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long orderId;

    /** Held here as well as on the order, so a per-store rate survives the order's deletion. */
    @NonNull
    @ColumnInfo(defaultValue = "WALMART")
    public StoreKind store = StoreKind.WALMART;

    /** What the reader called it, which is what a diagnosis has to go looking for. */
    @NonNull
    public String name = "";

    public long lineTotalCents;

    /** The line_items row id it had, so an undo can find and erase this record. */
    public long lineItemId;

    public long discardedAt;

    public DiscardedRow() {
    }
}
