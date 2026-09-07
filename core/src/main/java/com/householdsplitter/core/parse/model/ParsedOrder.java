package com.householdsplitter.core.parse.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Everything one import produced. The output of every {@code ReceiptParser}, on-device or
 * cloud alike (SPEC 8.10.4), and the input to the review screen (SPEC 7.6).
 */
public final class ParsedOrder {

    private final String label;
    private final Long orderDateMillis;
    private final String externalOrderNo;
    private final List<ParsedItem> items;
    private final ParsedAdjustments adjustments;
    private final Set<OrderField> parsedFields;
    private final List<String> warnings;
    private final List<String> sections;

    private ParsedOrder(Builder builder) {
        this.label = builder.label;
        this.orderDateMillis = builder.orderDateMillis;
        this.externalOrderNo = builder.externalOrderNo;
        this.items = Collections.unmodifiableList(new ArrayList<>(builder.items));
        this.adjustments = builder.adjustments == null
                ? ParsedAdjustments.empty() : builder.adjustments;
        this.parsedFields = Collections.unmodifiableSet(EnumSet.copyOf(
                builder.parsedFields.isEmpty()
                        ? EnumSet.noneOf(OrderField.class) : builder.parsedFields));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(builder.warnings));
        this.sections = Collections.unmodifiableList(new ArrayList<>(builder.sections));
    }

    /** SPEC 8.6.2, e.g. "Sep 03 Walmart". Null when no app-bar date was found. */
    public String label() {
        return label;
    }

    public Long orderDateMillis() {
        return orderDateMillis;
    }

    /** SPEC 8.6.6, feeding the duplicate guard of SPEC 8.6.7. */
    public String externalOrderNo() {
        return externalOrderNo;
    }

    public List<ParsedItem> items() {
        return items;
    }

    public ParsedAdjustments adjustments() {
        return adjustments;
    }

    /** SPEC 7.7.2: which fields genuinely came off a screenshot. */
    public Set<OrderField> parsedFields() {
        return parsedFields;
    }

    /** Advisory notes, e.g. the conflicting-summary case of SPEC 8.7.7. */
    public List<String> warnings() {
        return warnings;
    }

    /** Section names in the order they were opened (SPEC 8.5.2, SPEC 8.7.6). */
    public List<String> sections() {
        return sections;
    }

    /** SPEC 8.9.1: the sum of everything that is not excluded. */
    public long itemsSubtotalCents() {
        long sum = 0L;
        for (ParsedItem item : items) {
            if (item.scope().isChargeable() || item.scope().isUnanswered()) {
                sum += item.lineTotalCents();
            }
        }
        return sum;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ParsedOrder empty() {
        return builder().build();
    }

    public static final class Builder {

        private String label;
        private Long orderDateMillis;
        private String externalOrderNo;
        private final List<ParsedItem> items = new ArrayList<>();
        private ParsedAdjustments adjustments = ParsedAdjustments.empty();
        private final Set<OrderField> parsedFields = EnumSet.noneOf(OrderField.class);
        private final List<String> warnings = new ArrayList<>();
        private final List<String> sections = new ArrayList<>();

        public Builder label(String value) {
            this.label = value;
            return this;
        }

        public Builder orderDateMillis(Long value) {
            this.orderDateMillis = value;
            return this;
        }

        public Builder externalOrderNo(String value) {
            this.externalOrderNo = value;
            return this;
        }

        public Builder items(List<ParsedItem> value) {
            this.items.clear();
            if (value != null) {
                this.items.addAll(value);
            }
            return this;
        }

        public Builder addItem(ParsedItem value) {
            this.items.add(value);
            return this;
        }

        public Builder adjustments(ParsedAdjustments value) {
            this.adjustments = value;
            return this;
        }

        public Builder markParsed(OrderField field) {
            this.parsedFields.add(field);
            return this;
        }

        public Builder markParsed(Set<OrderField> fields) {
            this.parsedFields.addAll(fields);
            return this;
        }

        public Builder warn(String message) {
            if (message != null && !warnings.contains(message)) {
                warnings.add(message);
            }
            return this;
        }

        public Builder sections(List<String> value) {
            this.sections.clear();
            if (value != null) {
                this.sections.addAll(value);
            }
            return this;
        }

        public ParsedOrder build() {
            return new ParsedOrder(this);
        }
    }
}
