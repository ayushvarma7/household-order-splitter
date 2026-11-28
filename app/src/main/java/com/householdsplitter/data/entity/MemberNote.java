package com.householdsplitter.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/**
 * A short note left for one member on one order: "paying Friday", "took the extra chips".
 *
 * <p>Its own table rather than a column on {@link OrderParticipant}, because the participant
 * rows are rewritten whenever the people on an order change, and a note should not vanish
 * because somebody was taken off and put back. A note for a member who is no longer on the
 * order is kept and simply not shown, so putting them back brings it back.
 *
 * <p>The order foreign key cascades, so deleting an order takes its notes with it. The
 * member key does not, for the same reason as everywhere else: members with history are
 * archived, never deleted.
 */
@Entity(
        tableName = "order_member_notes",
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
public class MemberNote {

    public long orderId;
    public long memberId;

    @NonNull
    public String text = "";

    public MemberNote() {
    }

    public MemberNote(long orderId, long memberId, @NonNull String text) {
        this.orderId = orderId;
        this.memberId = memberId;
        this.text = text;
    }
}
