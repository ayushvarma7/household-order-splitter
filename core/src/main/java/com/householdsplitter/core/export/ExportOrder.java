package com.householdsplitter.core.export;

import com.householdsplitter.core.calc.result.SplitResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The order-level context the exporters need beyond a {@link SplitResult}. */
public final class ExportOrder {

    private final String groupName;
    private final String orderLabel;
    private final List<NamedAmount> commonItems;
    private final SplitResult result;
    private final String payerName;

    public ExportOrder(String groupName, String orderLabel, List<NamedAmount> commonItems,
                       SplitResult result, String payerName) {
        this.groupName = groupName == null ? "" : groupName;
        this.orderLabel = orderLabel == null ? "" : orderLabel;
        this.commonItems = Collections.unmodifiableList(new ArrayList<>(
                commonItems == null ? Collections.<NamedAmount>emptyList() : commonItems));
        this.result = result;
        this.payerName = payerName;
    }

    public String groupName() {
        return groupName;
    }

    public String orderLabel() {
        return orderLabel;
    }

    /** The COMMON rows, which belong to nobody in particular (SPEC 10.1). */
    public List<NamedAmount> commonItems() {
        return commonItems;
    }

    public SplitResult result() {
        return result;
    }

    /** Null until the user picks one (SPEC 7.10.4). */
    public String payerName() {
        return payerName;
    }

    /** A name and an amount in cents. */
    public static final class NamedAmount {

        private final String name;
        private final long cents;

        public NamedAmount(String name, long cents) {
            this.name = name == null ? "" : name;
            this.cents = cents;
        }

        public String name() {
            return name;
        }

        public long cents() {
            return cents;
        }
    }
}
