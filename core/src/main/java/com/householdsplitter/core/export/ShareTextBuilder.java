package com.householdsplitter.core.export;

import com.householdsplitter.core.calc.result.MemberSplit;
import com.householdsplitter.core.calc.result.SplitResult;
import com.householdsplitter.core.money.CurrencyFormat;

/**
 * The message pasted into a group chat. SPEC 10.2.
 *
 * <p>Plain text only: no markdown, no emoji, no table characters (SPEC 10.2.1). The two
 * separator glyphs below are the ones the SPEC 10.2 template prints; they are constants so
 * the house style can be changed in one place without touching the layout.
 */
public final class ShareTextBuilder {

    /** The separator between the group name and the order label in SPEC 10.2. */
    public static final String TITLE_SEPARATOR = " — ";
    /** The separator between the three figures on a member's detail line. */
    public static final String DETAIL_SEPARATOR = " · ";
    /** SPEC 10.2.2. */
    public static final int SOFT_CHARACTER_LIMIT = 1200;

    private ShareTextBuilder() {
    }

    public static String build(ExportOrder order, CurrencyFormat money) {
        String withDetail = render(order, money, true);
        if (withDetail.length() <= SOFT_CHARACTER_LIMIT) {
            return withDetail;
        }
        // SPEC 10.2.2: a large household drops the per-member detail line rather than
        // arriving in the chat truncated.
        return render(order, money, false);
    }

    private static String render(ExportOrder order, CurrencyFormat money, boolean withDetail) {
        SplitResult result = order.result();
        StringBuilder out = new StringBuilder();

        out.append(order.groupName());
        if (!order.orderLabel().isEmpty()) {
            if (out.length() > 0) {
                out.append(TITLE_SEPARATOR);
            }
            out.append(order.orderLabel());
        }
        out.append('\n');

        out.append("Total: ").append(money.format(result.computedTotalCents()));
        if (order.payerName() != null && !order.payerName().isEmpty()) {
            out.append("  (paid by ").append(order.payerName()).append(')');
        }
        out.append('\n');

        if (!result.matchesStatedTotal()) {
            // SPEC 6.4.12 and 7.10.3: never quietly absorb a mismatch.
            out.append("Bill says ").append(money.format(result.statedTotalCents()))
                    .append(", off by ").append(money.format(result.deltaCents())).append('\n');
        }
        out.append('\n');

        for (MemberSplit member : result.members()) {
            out.append(member.memberName()).append(": ")
                    .append(money.format(member.finalCents())).append('\n');
            if (withDetail) {
                out.append("  common ").append(money.format(member.commonShareCents()))
                        .append(DETAIL_SEPARATOR)
                        .append("items ").append(money.format(member.itemsTotalCents()))
                        .append(DETAIL_SEPARATOR)
                        .append("tax & fees ").append(money.format(member.adjustmentsTotalCents()))
                        .append('\n');
            }
        }
        return out.toString();
    }

    /** SPEC 7.10.4: "<member> owes <payer> $X.XX", or "paid $Y.YY" on the payer's own row. */
    public static String owesLine(MemberSplit member, String payerName, boolean isPayer,
                                  CurrencyFormat money) {
        if (isPayer) {
            return "paid " + money.format(member.finalCents());
        }
        if (payerName == null || payerName.isEmpty()) {
            return money.format(member.finalCents());
        }
        return member.memberName() + " owes " + payerName + " " + money.format(member.finalCents());
    }
}
