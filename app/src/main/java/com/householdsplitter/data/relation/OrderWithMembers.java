package com.householdsplitter.data.relation;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderParticipant;

import java.util.ArrayList;
import java.util.List;

/** An order plus its participants, for the overlapping avatars on Home (SPEC 7.3.3). */
public class OrderWithMembers {

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
}
