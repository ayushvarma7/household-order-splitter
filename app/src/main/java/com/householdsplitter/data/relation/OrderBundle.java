package com.householdsplitter.data.relation;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
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
}
