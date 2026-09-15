package com.householdsplitter.core.parse.layout;

import com.householdsplitter.core.parse.model.OrderField;
import com.householdsplitter.core.parse.model.ParsedItem;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** What one screenshot yielded, before stitching (SPEC 8.7). */
final class PageParse {

    final int imageIndex;
    final List<ParsedItem> items = new ArrayList<>();
    final List<String> sections = new ArrayList<>();
    final Map<OrderField, Long> amounts = new EnumMap<>(OrderField.class);
    final List<String> warnings = new ArrayList<>();
    String externalOrderNo;
    Long orderDateMillis;
    String label;
    /** SPEC 8.5.4: a unit count, kept only as a hint. */
    int deliveredUnitCount = -1;

    PageParse(int imageIndex) {
        this.imageIndex = imageIndex;
    }

    boolean hasSummary() {
        return amounts.containsKey(OrderField.TOTAL) || amounts.containsKey(OrderField.SUBTOTAL);
    }
}
