package com.householdsplitter.core.parse.layout;

import com.householdsplitter.core.parse.model.OrderField;
import com.householdsplitter.core.parse.model.ParsedAdjustments;
import com.householdsplitter.core.parse.model.ParsedItem;
import com.householdsplitter.core.parse.model.ParsedOrder;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Joins several screenshots into one order. SPEC 8.7.
 *
 * <p>The user scrolls a little and shoots again, so consecutive screenshots overlap on
 * purpose (SPEC 8.7.2). That makes two jobs necessary and one job forbidden: de-duplicate
 * the rows that genuinely repeat across a boundary, do not restart a section header that
 * repeats across the same boundary, and never de-duplicate two identical rows that are
 * separated by other items, because a household can buy the same thing twice.
 */
final class Stitcher {

    private Stitcher() {
    }

    static ParsedOrder merge(List<PageParse> pages, ParseTuning tuning) {
        List<ParsedItem> merged = new ArrayList<>();
        List<String> sections = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (PageParse page : pages) {
            warnings.addAll(page.warnings);

            // SPEC 8.7.6: a header that reappears at the top of the next screenshot is the
            // same section, not a new one.
            for (String section : page.sections) {
                if (sections.isEmpty() || !sections.get(sections.size() - 1).equals(section)) {
                    sections.add(section);
                }
            }

            for (ParsedItem item : page.items) {
                if (isCrossBoundaryDuplicate(merged, item, tuning)) {
                    continue;
                }
                merged.add(item);
            }
        }

        // SPEC 8.7.7: the summary comes from whichever screenshot holds it; a later
        // screenshot wins a disagreement, and the disagreement is flagged rather than
        // hidden.
        Map<OrderField, Long> amounts = new EnumMap<>(OrderField.class);
        String orderNo = null;
        Long dateMillis = null;
        String label = null;
        int deliveredUnits = -1;

        for (PageParse page : pages) {
            for (Map.Entry<OrderField, Long> entry : page.amounts.entrySet()) {
                Long previous = amounts.get(entry.getKey());
                if (previous != null && !previous.equals(entry.getValue())) {
                    warnings.add("Two screenshots disagree on " + friendly(entry.getKey())
                            + " (" + previous + " then " + entry.getValue()
                            + " in cents); the later one was used.");
                }
                amounts.put(entry.getKey(), entry.getValue());
            }
            if (page.externalOrderNo != null) {
                orderNo = page.externalOrderNo;
            }
            if (page.orderDateMillis != null) {
                dateMillis = page.orderDateMillis;
                label = page.label;
            }
            if (page.deliveredUnitCount > 0) {
                deliveredUnits = page.deliveredUnitCount;
            }
        }

        ParsedAdjustments adjustments = new ParsedAdjustments(
                value(amounts, OrderField.SUBTOTAL),
                value(amounts, OrderField.TAX),
                value(amounts, OrderField.DELIVERY_FEE),
                value(amounts, OrderField.TIP),
                value(amounts, OrderField.OTHER_FEE),
                value(amounts, OrderField.DISCOUNT),
                value(amounts, OrderField.TOTAL));

        ParsedOrder.Builder builder = ParsedOrder.builder()
                .items(merged)
                .sections(sections)
                .adjustments(adjustments)
                .externalOrderNo(orderNo)
                .orderDateMillis(dateMillis)
                .label(label)
                .deliveredUnitCount(deliveredUnits);

        for (OrderField field : amounts.keySet()) {
            builder.markParsed(field);
        }
        if (orderNo != null) {
            builder.markParsed(OrderField.EXTERNAL_ORDER_NO);
        }
        if (dateMillis != null) {
            builder.markParsed(OrderField.ORDER_DATE);
        }
        for (String warning : warnings) {
            builder.warn(warning);
        }
        return builder.build();
    }

    /**
     * SPEC 8.7.3 and 8.7.4. Two rows are the same row when the name and the price both
     * match, they came from different screenshots, and they sit within a short window of
     * each other. Requiring a different screenshot is what keeps two genuinely identical
     * purchases listed one after the other in the same image.
     */
    private static boolean isCrossBoundaryDuplicate(List<ParsedItem> merged, ParsedItem candidate,
                                                    ParseTuning tuning) {
        String key = dedupeKey(candidate);
        int from = Math.max(0, merged.size() - tuning.dedupeWindow);
        for (int i = merged.size() - 1; i >= from; i--) {
            ParsedItem existing = merged.get(i);
            if (existing.imageIndex() == candidate.imageIndex()) {
                continue;
            }
            if (dedupeKey(existing).equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static String dedupeKey(ParsedItem item) {
        return BlockAssembler.normaliseWhitespace(item.name()).toLowerCase()
                + "|" + item.lineTotalCents();
    }

    private static long value(Map<OrderField, Long> amounts, OrderField field) {
        Long value = amounts.get(field);
        return value == null ? 0L : value;
    }

    private static String friendly(OrderField field) {
        switch (field) {
            case SUBTOTAL:
                return "the subtotal";
            case TAX:
                return "tax";
            case DELIVERY_FEE:
                return "the delivery fee";
            case TIP:
                return "the tip";
            case OTHER_FEE:
                return "fees";
            case DISCOUNT:
                return "the discount";
            case TOTAL:
                return "the total";
            default:
                return field.name().toLowerCase();
        }
    }
}
