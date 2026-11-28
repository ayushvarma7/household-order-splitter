package com.householdsplitter.core.export;

import com.householdsplitter.core.calc.result.SplitResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The order-level context the exporters need beyond a {@link SplitResult}. */
public final class ExportOrder {

    private final String groupName;
    private final String orderLabel;
    private final List<NamedAmount> commonItems;
    private final SplitResult result;
    private final String payerName;
    private final String orderNote;
    private final Map<Long, String> memberNotes;

    public ExportOrder(String groupName, String orderLabel, List<NamedAmount> commonItems,
                       SplitResult result, String payerName) {
        this(groupName, orderLabel, commonItems, result, payerName, null, null);
    }

    private ExportOrder(String groupName, String orderLabel, List<NamedAmount> commonItems,
                        SplitResult result, String payerName, String orderNote,
                        Map<Long, String> memberNotes) {
        this.groupName = groupName == null ? "" : groupName;
        this.orderLabel = orderLabel == null ? "" : orderLabel;
        this.commonItems = Collections.unmodifiableList(new ArrayList<>(
                commonItems == null ? Collections.<NamedAmount>emptyList() : commonItems));
        this.result = result;
        this.payerName = payerName;
        this.orderNote = clean(orderNote);
        Map<Long, String> notes = new HashMap<>();
        if (memberNotes != null) {
            for (Map.Entry<Long, String> entry : memberNotes.entrySet()) {
                String text = clean(entry.getValue());
                if (entry.getKey() != null && !text.isEmpty()) {
                    notes.put(entry.getKey(), text);
                }
            }
        }
        this.memberNotes = Collections.unmodifiableMap(notes);
    }

    /**
     * The same order carrying what people wrote on it: one note for the whole order and
     * one per member, keyed by member id. Blank notes are dropped, so an exporter only has
     * to ask whether a note is empty.
     */
    public ExportOrder withNotes(String orderNote, Map<Long, String> memberNotes) {
        return new ExportOrder(groupName, orderLabel, commonItems, result, payerName,
                orderNote, memberNotes);
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }

    /** The note on the whole order, or empty. */
    public String orderNote() {
        return orderNote;
    }

    /** The note left for one member, or empty. */
    public String memberNote(long memberId) {
        String note = memberNotes.get(memberId);
        return note == null ? "" : note;
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
