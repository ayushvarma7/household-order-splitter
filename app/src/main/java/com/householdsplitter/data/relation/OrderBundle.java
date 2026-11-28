package com.householdsplitter.data.relation;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.MemberNote;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderImage;
import com.householdsplitter.data.entity.OrderParticipant;

import java.util.ArrayList;
import java.util.List;

/** Everything one order needs to be calculated, exported or displayed. */
public class OrderBundle {

    @Embedded
    public Order order;

    @Relation(
            parentColumn = "id",
            entityColumn = "id",
            associateBy = @Junction(
                    value = OrderParticipant.class,
                    parentColumn = "orderId",
                    entityColumn = "memberId"))
    public List<Member> participants = new ArrayList<>();

    @Relation(parentColumn = "id", entityColumn = "orderId", entity = LineItem.class)
    public List<LineItemWithAssignments> items = new ArrayList<>();

    @Relation(parentColumn = "id", entityColumn = "orderId")
    public List<OrderImage> images = new ArrayList<>();

    @Relation(parentColumn = "id", entityColumn = "orderId")
    public List<MemberNote> memberNotes = new ArrayList<>();

    /**
     * Each current participant's note, by member id. A note left for somebody who has since
     * been taken off the order is kept in the table but not returned here.
     */
    public java.util.Map<Long, String> memberNotesById() {
        java.util.Set<Long> onOrder = new java.util.HashSet<>();
        for (Member member : participants) {
            onOrder.add(member.id);
        }
        java.util.Map<Long, String> notes = new java.util.HashMap<>();
        for (MemberNote note : memberNotes) {
            if (onOrder.contains(note.memberId) && !note.text.trim().isEmpty()) {
                notes.put(note.memberId, note.text);
            }
        }
        return notes;
    }
}
