package com.householdsplitter.core.money;

/**
 * How a multi-quantity row divides, said out loud.
 *
 * <p>A row bought two of prints one price for both, and "x2" beside $5.05 leaves the reader
 * to do the division. Saying "2 x $2.52" does it for them.
 *
 * <p>The awkward case is the one worth being careful about: $5.05 over two is not $2.52
 * each, it is $2.53 and $2.52. Printing "2 x $2.53" would overstate the row by a penny and
 * printing "2 x $2.52" would understate it, and either invites someone to check the
 * arithmetic and find the app wrong. So when the division is not exact this says how many
 * units there are and leaves the total to speak for itself.
 */
public final class QuantityBreakdown {

    private QuantityBreakdown() {
    }

    /** True when the row divides into equal whole pennies. */
    public static boolean dividesEvenly(int quantity, long lineTotalCents) {
        return quantity > 1 && lineTotalCents % quantity == 0;
    }

    /** The exact per-unit amount, or -1 when the row does not divide evenly. */
    public static long unitCents(int quantity, long lineTotalCents) {
        return dividesEvenly(quantity, lineTotalCents) ? lineTotalCents / quantity : -1L;
    }

    /**
     * A line such as "2 x $2.52", or null for a single unit.
     *
     * @param uneven what to say when the amount does not divide into equal pennies, with
     *               one {@code %d} for the unit count; the caller supplies it so the
     *               wording stays in the string resources
     */
    public static String describe(int quantity, long lineTotalCents, CurrencyFormat money,
                                  String uneven) {
        if (quantity <= 1) {
            return null;
        }
        long unit = unitCents(quantity, lineTotalCents);
        if (unit < 0) {
            return uneven == null ? null : String.format(uneven, quantity);
        }
        return quantity + " × " + money.format(unit);
    }
}
