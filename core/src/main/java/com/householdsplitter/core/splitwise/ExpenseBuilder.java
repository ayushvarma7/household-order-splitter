package com.householdsplitter.core.splitwise;

import com.householdsplitter.core.money.Cents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns one split order into one Splitwise expense: the order total, paid in full by one
 * person, owed in per-person shares.
 *
 * <p>Splitwise rejects an expense whose paid shares or owed shares do not add up to the
 * cost, and it takes money as decimal strings. Working in long cents and printing the
 * strings only here means there is no rounding step in which a cent could go missing: the
 * cost is defined as the sum of the owed shares, so the two cannot disagree.
 */
public final class ExpenseBuilder {

    /** What one Splitwise user owes, in cents. */
    public static final class Share {
        public final long userId;
        public final long owedCents;

        public Share(long userId, long owedCents) {
            this.userId = userId;
            this.owedCents = owedCents;
        }
    }

    private ExpenseBuilder() {
    }

    /**
     * @param payerUserId who fronted the money; added with a zero share if not in {@code shares}
     * @param dateIso     an ISO 8601 timestamp, as Splitwise expects
     * @throws IllegalArgumentException when the shares cannot make a valid expense
     */
    public static ExpensePayload build(String description, String details, String dateIso,
                                       String currencyCode, long groupId, long payerUserId,
                                       List<Share> shares) {
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("An expense needs a description.");
        }
        // Two household members mapped to one Splitwise person become one line, because
        // Splitwise allows a user to appear once per expense.
        Map<Long, Long> owedByUser = new LinkedHashMap<>();
        long total = 0L;
        for (Share share : shares) {
            if (share.owedCents < 0) {
                throw new IllegalArgumentException("A share cannot be negative.");
            }
            owedByUser.merge(share.userId, share.owedCents, Long::sum);
            total += share.owedCents;
        }
        if (total <= 0) {
            throw new IllegalArgumentException("The order has nothing to send: its total is zero.");
        }
        if (!owedByUser.containsKey(payerUserId)) {
            owedByUser.put(payerUserId, 0L);
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("cost", Cents.toPlainString(total));
        form.put("description", description.trim());
        if (details != null && !details.trim().isEmpty()) {
            form.put("details", details.trim());
        }
        form.put("date", dateIso);
        if (currencyCode != null && !currencyCode.isEmpty()) {
            form.put("currency_code", currencyCode);
        }
        form.put("group_id", Long.toString(groupId));
        int index = 0;
        for (Map.Entry<Long, Long> entry : owedByUser.entrySet()) {
            long user = entry.getKey();
            form.put("users__" + index + "__user_id", Long.toString(user));
            form.put("users__" + index + "__paid_share",
                    Cents.toPlainString(user == payerUserId ? total : 0L));
            form.put("users__" + index + "__owed_share", Cents.toPlainString(entry.getValue()));
            index++;
        }
        return new ExpensePayload(form);
    }

    /** Convenience for callers holding parallel lists. */
    public static List<Share> shares(long[] userIds, long[] owedCents) {
        List<Share> out = new ArrayList<>();
        for (int i = 0; i < userIds.length; i++) {
            out.add(new Share(userIds[i], owedCents[i]));
        }
        return out;
    }
}
