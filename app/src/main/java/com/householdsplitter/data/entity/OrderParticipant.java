package com.householdsplitter.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/**
 * SPEC 5.5. Who is in on one order, as a composite primary key.
 *
 * <p>SPEC 5.9 is why this table matters: COMMON items carry no assignment rows at all and
 * resolve against this list at calculation time, so changing the participants
 * automatically recalculates the common bucket.
 *
 * <p>The member foreign key deliberately does not cascade. SPEC 5.10 requires a member with
 * history to be archived rather than deleted, and that rule is enforced in the repository.
 */
@Entity(
        tableName = "order_participants",
        primaryKeys = {"orderId", "memberId"},
        foreignKeys = {
                @ForeignKey(
                        entity = Order.class,
                        parentColumns = "id",
                        childColumns = "orderId",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(
                        entity = Member.class,
                        parentColumns = "id",
                        childColumns = "memberId",
                        onDelete = ForeignKey.NO_ACTION)
        },
        indices = {@Index("memberId")})
public class OrderParticipant {

    public long orderId;
    public long memberId;

    public OrderParticipant() {
    }

    public OrderParticipant(long orderId, long memberId) {
        this.orderId = orderId;
        this.memberId = memberId;
    }
}
