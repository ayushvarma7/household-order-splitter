package com.householdsplitter.data.relation;

import androidx.room.Embedded;
import androidx.room.Relation;

import com.householdsplitter.data.entity.ItemAssignment;
import com.householdsplitter.data.entity.LineItem;

import java.util.ArrayList;
import java.util.List;

/** A row and whoever is on it. Empty for COMMON rows, by SPEC 5.9. */
public class LineItemWithAssignments {

    @Embedded
    public LineItem item;

    @Relation(parentColumn = "id", entityColumn = "lineItemId")
    public List<ItemAssignment> assignments = new ArrayList<>();

    public boolean hasAssignments() {
        return assignments != null && !assignments.isEmpty();
    }
}
