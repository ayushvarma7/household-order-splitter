package com.householdsplitter.core.money;

/**
 * Working out a tip, in whole cents.
 *
 * <p>Two questions, and the interesting one is the first: a percentage of what? A bill
 * states a subtotal and a tax, and tipping on the tax means tipping the state. The
 * convention, and the arithmetic this uses, is that a percentage applies to the pre-tax
 * subtotal. It is also the smaller number, so anyone who disagrees can type the figure they
 * want and nothing here argues.
 *
 * <p>The second question is rounding up. "Make it sixty" is how a tip is usually decided at
 * a table, and it is the total that gets rounded, not the tip, so the tip is whatever is
 * left over once everything else is accounted for.
 *
 * <p>All of it in {@code long} cents, per SPEC 6.1. A percentage of an amount is the one
 * place a tip calculator would reach for a double, and rounding a double at two decimal
 * places is how a split ends up a penny short of the bill it came from.
 */
public final class TipCalculator {

    private TipCalculator() {
    }

    /**
     * A percentage of the pre-tax subtotal, rounded to the nearest cent.
     *
     * <p>Half rounds away from zero, which is the rounding a person does in their head. The
     * arithmetic is integer throughout: 18% of $41.51 is 41.51 * 18 / 100, computed as
     * (4151 * 18 + 50) / 100 = 747.18 rounded to 747, and never as a binary fraction that
     * cannot represent 0.18 in the first place.
     *
     * @param percent whole percentage points, 0 or more
     */
    public static long ofSubtotal(long subtotalCents, int percent) {
        if (subtotalCents <= 0L || percent <= 0) {
            return 0L;
        }
        return (subtotalCents * percent + 50L) / 100L;
    }

    /**
     * The tip that makes the bill come out at a round figure.
     *
     * <p>{@code runningTotalCents} is everything already settled: the items, the tax and any
     * fees, with the current tip excluded. The result is what has to be added on top to
     * reach the next multiple of {@code stepCents}.
     *
     * <p>A total that already sits exactly on a multiple goes up by a whole step rather than
     * staying put, because a button that does nothing when pressed is a broken button, and
     * somebody who taps "round up" on a bill of exactly $60 means $65 rather than nothing.
     *
     * @param stepCents what to round to, such as 100 for the next dollar or 500 for the
     *                  next five
     */
    public static long toRoundTotal(long runningTotalCents, long stepCents) {
        if (stepCents <= 0L || runningTotalCents < 0L) {
            return 0L;
        }
        long remainder = runningTotalCents % stepCents;
        return remainder == 0L ? stepCents : stepCents - remainder;
    }

    /**
     * The percentage a tip of this size works out at, for display beside a typed figure.
     *
     * <p>Rounded to one decimal place and returned in tenths of a percent, so the caller
     * formats it and no fraction is carried in a floating point number. 747 on 4151 is 180,
     * which is 18.0%.
     *
     * <p>Returns -1 when there is no subtotal to be a percentage of, which is a different
     * statement from 0% and is why it is not zero.
     */
    public static int percentTenthsOf(long tipCents, long subtotalCents) {
        if (subtotalCents <= 0L) {
            return -1;
        }
        return (int) ((tipCents * 1000L + subtotalCents / 2L) / subtotalCents);
    }
}
