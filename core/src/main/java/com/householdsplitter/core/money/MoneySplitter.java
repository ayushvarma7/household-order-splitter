package com.householdsplitter.core.money;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Largest remainder ("Hare quota") allocation of an exact amount of cents across weights.
 *
 * <p>SPEC 6.3. Every step below is numbered against that section. All arithmetic is
 * {@code long} cents; there is deliberately no {@code float} or {@code double} token in
 * this file, its package, or its tests (SPEC 6.1, PROMPT hard rule 4).
 */
public final class MoneySplitter {

    private MoneySplitter() {
    }

    /** SPEC 6.3, the signature the spec names. */
    public static long[] split(long amount, int[] weights) {
        if (weights == null) {
            throw new IllegalArgumentException("weights must not be null");
        }
        long[] widened = new long[weights.length];
        for (int i = 0; i < weights.length; i++) {
            widened[i] = weights[i];
        }
        return split(amount, widened);
    }

    /**
     * Widened overload. SPEC 6.4.9 uses the members' pre-tax totals as the weights for a
     * proportional adjustment, and those are already {@code long} cents, so narrowing them
     * to {@code int} would be a lossy step in the money path.
     */
    public static long[] split(long amount, long[] weights) {
        if (weights == null) {
            throw new IllegalArgumentException("weights must not be null");
        }
        final int n = weights.length;

        // 6.3.1
        long totalWeight = 0L;
        for (long w : weights) {
            if (w < 0L) {
                throw new IllegalArgumentException("weights must not be negative: " + w);
            }
            totalWeight = Math.addExact(totalWeight, w);
        }
        if (totalWeight <= 0L) {
            throw new IllegalArgumentException(
                    "sum of weights must be positive, was " + totalWeight);
        }

        final long[] result = new long[n];
        final long[] remainder = new long[n];

        // 6.3.2, 6.3.3, 6.3.5. Java's integer division truncates toward zero and `%`
        // takes the sign of the dividend, which is exactly what the negative branch in
        // 6.3.8 relies on.
        long distributed = 0L;
        for (int i = 0; i < n; i++) {
            long product = Math.multiplyExact(amount, weights[i]);
            result[i] = product / totalWeight;
            remainder[i] = product % totalWeight;
            distributed = Math.addExact(distributed, result[i]);
        }

        // 6.3.4
        final long shortfall = amount - distributed;

        if (shortfall != 0L) {
            // 6.3.6: by remainder descending, ties broken by ascending index so the
            // output is deterministic for identical input (SPEC 12.1.9).
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) {
                order[i] = i;
            }
            Arrays.sort(order, Comparator
                    .comparingLong((Integer i) -> remainder[i]).reversed()
                    .thenComparingInt(i -> i));

            final long steps = Math.abs(shortfall);
            if (steps > n) {
                // Unreachable for well formed input; a loud failure beats a silent one.
                throw new IllegalStateException(
                        "shortfall " + shortfall + " exceeds bucket count " + n);
            }
            // 6.3.7 and 6.3.8
            final long delta = shortfall > 0L ? 1L : -1L;
            for (int k = 0; k < steps; k++) {
                result[order[k]] += delta;
            }
        }

        // 6.3.9: the postcondition is asserted in code, not left to the caller. Java's
        // `assert` is off by default at runtime, so this is an explicit check.
        long check = 0L;
        for (long part : result) {
            check = Math.addExact(check, part);
        }
        if (check != amount) {
            throw new IllegalStateException(
                    "split postcondition violated: parts sum to " + check + ", expected " + amount);
        }
        return result;
    }

    /** Convenience for the equal splits in SPEC 6.4.5 and 6.4.9. */
    public static int[] ones(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive, was " + count);
        }
        int[] weights = new int[count];
        Arrays.fill(weights, 1);
        return weights;
    }
}
