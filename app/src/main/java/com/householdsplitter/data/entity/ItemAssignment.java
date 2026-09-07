package com.householdsplitter.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * SPEC 5.7. One member's stake in one item.
 *
 * <p>SPEC 5.9: COMMON items must have no rows here at all. That is enforced in
 * {@code AssignmentRepository}, which clears an item's rows whenever its scope becomes
 * COMMON, so a later change to the participant list recalculates it automatically.
 */
@Entity(
        tableName = "item_assignments",
        foreignKeys = {
                @ForeignKey(
                        entity = LineItem.class,
                        parentColumns = "id",
                        childColumns = "lineItemId",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(
                        entity = Member.class,
                        parentColumns = "id",
                        childColumns = "memberId",
                        onDelete = ForeignKey.NO_ACTION)
        },
        indices = {
                @Index("memberId"),
                @Index(value = {"lineItemId", "memberId"}, unique = true)
        })
public class ItemAssignment {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long lineItemId;

    public long memberId;

    /** SPEC 7.9.6: long-press a chip to take a double share, shown as "x2". */
    @ColumnInfo(defaultValue = "1")
    public int shares = 1;

    public ItemAssignment() {
    }

    public ItemAssignment(long lineItemId, long memberId, int shares) {
        this.lineItemId = lineItemId;
        this.memberId = memberId;
        this.shares = shares;
    }
}
