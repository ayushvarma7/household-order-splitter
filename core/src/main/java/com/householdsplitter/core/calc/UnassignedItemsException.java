package com.householdsplitter.core.calc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SPEC 7.10.6: the summary must not compute while any item is still unanswered. The
 * offending items travel with the exception so the blocking panel can list them and make
 * each one tappable.
 */
public class UnassignedItemsException extends RuntimeException {

    private final List<Long> lineItemIds;
    private final List<String> itemNames;

    public UnassignedItemsException(List<Long> lineItemIds, List<String> itemNames) {
        super(lineItemIds.size() + " item(s) still need an answer");
        this.lineItemIds = Collections.unmodifiableList(new ArrayList<>(lineItemIds));
        this.itemNames = Collections.unmodifiableList(new ArrayList<>(itemNames));
    }

    public List<Long> lineItemIds() {
        return lineItemIds;
    }

    public List<String> itemNames() {
        return itemNames;
    }
}
