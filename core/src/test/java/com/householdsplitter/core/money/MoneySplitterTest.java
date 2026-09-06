package com.householdsplitter.core.money;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Random;

/** SPEC 12.1. No float or double appears in this file (PROMPT hard rule 4). */
public class MoneySplitterTest {

    /** 12.1.1 */
    @Test
    public void thousandThreeWays() {
        long[] result = MoneySplitter.split(1000L, new int[]{1, 1, 1});
        assertArrayEquals(new long[]{334L, 333L, 333L}, result);
        assertEquals(1000L, sum(result));
    }

    /** 12.1.2 */
    @Test
    public void thousandFourWaysIsExact() {
        long[] result = MoneySplitter.split(1000L, new int[]{1, 1, 1, 1});
        assertArrayEquals(new long[]{250L, 250L, 250L, 250L}, result);
        assertEquals(1000L, sum(result));
    }

    /** 12.1.3 */
    @Test
    public void twoSeventyTwoThreeWays() {
        long[] result = MoneySplitter.split(272L, new int[]{1, 1, 1});
        assertArrayEquals(new long[]{91L, 91L, 90L}, result);
        assertEquals(272L, sum(result));
    }

    /** 12.1.4 */
    @Test
    public void threeThirtyTwoTwoWays() {
        long[] result = MoneySplitter.split(332L, new int[]{1, 1});
        assertArrayEquals(new long[]{166L, 166L}, result);
        assertEquals(332L, sum(result));
    }

    /** 12.1.5: a doubled share, per SPEC 7.9.6. */
    @Test
    public void weightedTwoToOne() {
        long[] result = MoneySplitter.split(100L, new int[]{2, 1});
        assertArrayEquals(new long[]{67L, 33L}, result);
        assertEquals(100L, sum(result));
    }

    /** 12.1.6: the negative branch of SPEC 6.3.8, as a discount would hit it. */
    @Test
    public void negativeAmountStillSumsExactly() {
        long[] result = MoneySplitter.split(-127L, new int[]{1, 1, 1});
        assertEquals(-127L, sum(result));
        assertArrayEquals(new long[]{-43L, -42L, -42L}, result);
    }

    /** 12.1.7 */
    @Test
    public void zeroAmount() {
        assertArrayEquals(new long[]{0L, 0L}, MoneySplitter.split(0L, new int[]{1, 1}));
    }

    /** 12.1.8 */
    @Test
    public void emptyWeightsThrow() {
        try {
            MoneySplitter.split(500L, new int[]{});
            fail("expected IllegalArgumentException for empty weights");
        } catch (IllegalArgumentException expected) {
            // SPEC 6.3.1
        }
    }

    /** 12.1.8 */
    @Test
    public void allZeroWeightsThrow() {
        try {
            MoneySplitter.split(500L, new int[]{0, 0});
            fail("expected IllegalArgumentException for zero total weight");
        } catch (IllegalArgumentException expected) {
            // SPEC 6.3.1
        }
    }

    @Test
    public void negativeWeightThrows() {
        try {
            MoneySplitter.split(500L, new int[]{2, -1});
            fail("expected IllegalArgumentException for a negative weight");
        } catch (IllegalArgumentException expected) {
            // A negative weight is never meaningful for a share count.
        }
    }

    /** 12.1.9: identical input, identical output, a thousand times over. */
    @Test
    public void deterministicAcrossRuns() {
        long[] first = MoneySplitter.split(1001L, new int[]{1, 1, 1, 1, 1, 1, 1});
        for (int run = 0; run < 1000; run++) {
            assertArrayEquals(first, MoneySplitter.split(1001L, new int[]{1, 1, 1, 1, 1, 1, 1}));
        }
    }

    /** 12.1.10: the invariant that matters, over random input. */
    @Test
    public void propertySumAlwaysEqualsAmount() {
        Random random = new Random(20260906L);
        for (int trial = 0; trial < 20000; trial++) {
            long amount = random.nextInt(4_000_001) - 2_000_000;
            int count = 1 + random.nextInt(12);
            int[] weights = new int[count];
            boolean anyPositive = false;
            for (int i = 0; i < count; i++) {
                weights[i] = random.nextInt(6);
                anyPositive |= weights[i] > 0;
            }
            if (!anyPositive) {
                weights[random.nextInt(count)] = 1;
            }
            long[] parts = MoneySplitter.split(amount, weights);
            assertEquals("amount " + amount, amount, sum(parts));
            assertEquals(count, parts.length);
        }
    }

    /** A zero weight must receive nothing at all, not a rounding crumb. */
    @Test
    public void zeroWeightGetsNothing() {
        long[] result = MoneySplitter.split(1000L, new int[]{1, 0, 1});
        assertEquals(0L, result[1]);
        assertEquals(1000L, sum(result));
    }

    @Test
    public void onesBuildsEqualWeights() {
        assertArrayEquals(new int[]{1, 1, 1}, MoneySplitter.ones(3));
    }

    private static long sum(long[] values) {
        long total = 0L;
        for (long value : values) {
            total += value;
        }
        return total;
    }
}
