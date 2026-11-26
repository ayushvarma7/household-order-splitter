package com.householdsplitter.core.insight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.insight.Insight.Kind;
import com.householdsplitter.core.insight.Insight.Tone;
import com.householdsplitter.core.insight.InsightTree.Node;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InsightGeneratorTest {

    private static List<Purchase> twoMonths() {
        List<Purchase> rows = new ArrayList<>();
        rows.add(InsightTreeTest.buy(InsightTreeTest.day(2026, 11, 3), "Tortilla Chips", 1400L, 1L));
        rows.add(InsightTreeTest.buy(InsightTreeTest.day(2026, 11, 3), "Cookies", 1600L, 1L));
        rows.add(InsightTreeTest.buy(InsightTreeTest.day(2026, 11, 9), "Cookies", 800L, 2L));
        rows.add(InsightTreeTest.buy(InsightTreeTest.day(2026, 10, 5), "Tortilla Chips", 900L, 1L));
        rows.add(InsightTreeTest.buy(InsightTreeTest.day(2026, 10, 5), "Cookies", 800L, 2L));
        return rows;
    }

    private static InsightTree tree() {
        return InsightTree.build(twoMonths(), InsightTreeTest.ZONE, InsightTreeTest.NOV);
    }

    private static Insight of(List<Insight> list, Kind kind) {
        for (Insight insight : list) {
            if (insight.kind() == kind) {
                return insight;
            }
        }
        return null;
    }

    @Test
    public void percentChangeRoundsHalfAwayFromZero() {
        assertEquals(50, InsightGenerator.percentChange(150L, 100L));
        assertEquals(-50, InsightGenerator.percentChange(50L, 100L));
        assertEquals(33, InsightGenerator.percentChange(4L, 3L));
        assertEquals(-33, InsightGenerator.percentChange(2L, 3L));
        assertEquals(-88, InsightGenerator.percentChange(1L, 8L));
        assertEquals(0, InsightGenerator.percentChange(500L, 0L));
    }

    @Test
    public void toneNeedsMoreThanATenthToLeaveSteady() {
        assertEquals(Tone.UP, InsightGenerator.toneOf(111L, 100L));
        assertEquals(Tone.STEADY, InsightGenerator.toneOf(110L, 100L));
        assertEquals(Tone.STEADY, InsightGenerator.toneOf(90L, 100L));
        assertEquals(Tone.DOWN, InsightGenerator.toneOf(89L, 100L));
        assertEquals(Tone.STEADY, InsightGenerator.toneOf(500L, 0L));
    }

    @Test
    public void aBubbleComparesItselfWithLastMonth() {
        InsightTree tree = tree();
        Node snacks = tree.find("Snacks");

        Insight versus = of(InsightGenerator.forNode(tree, snacks, 0L), Kind.VS_LAST_MONTH);

        assertNotNull(versus);
        assertEquals(Tone.UP, versus.tone());
        assertEquals(3800L, versus.cents());
        assertEquals(1700L, versus.otherCents());
        assertEquals(124, versus.percent());
    }

    @Test
    public void aBubbleBoughtForTheFirstTimeSaysSoInsteadOfInventingAComparison() {
        List<Purchase> rows = twoMonths();
        rows.add(InsightTreeTest.buy(InsightTreeTest.day(2026, 11, 20), "Salmon", 1800L, 1L));
        InsightTree tree = InsightTree.build(rows, InsightTreeTest.ZONE, InsightTreeTest.NOV);

        List<Insight> found = InsightGenerator.forNode(tree, tree.find("Meat and fish"), 0L);

        assertNotNull(of(found, Kind.NEW_THIS_MONTH));
        assertEquals(null, of(found, Kind.VS_LAST_MONTH));
    }

    @Test
    public void aBubbleWithItemsPointsAtTheOneThatMovedMost() {
        InsightTree tree = tree();

        Insight mover = of(InsightGenerator.forNode(tree, tree.find("Snacks"), 0L),
                Kind.TOP_MOVER);

        assertNotNull(mover);
        // Chips went from 900 to 1400 (+500); cookies from 800 to 2400 (+1600).
        assertEquals("Cookies", mover.target().name());
        assertEquals(1600L, mover.cents());
        assertEquals(Tone.UP, mover.tone());
    }

    @Test
    public void saysWhoBoughtMoreWhenTheLeadIsBigEnough() {
        // This month only, so the list is short enough that nothing is cut to make room.
        List<Purchase> rows = new ArrayList<>(twoMonths());
        rows.removeIf(row -> row.dateMillis() < InsightTreeTest.day(2026, 11, 1));
        InsightTree tree = InsightTree.build(rows, InsightTreeTest.ZONE, InsightTreeTest.NOV);

        Insight who = of(InsightGenerator.forNode(tree, tree.find("Snacks"), 0L),
                Kind.WHO_BOUGHT_MORE);

        assertNotNull(who);
        assertEquals(1L, who.memberId());
        assertEquals(2L, who.otherMemberId());
        assertEquals(3000L - 800L, who.cents());
    }

    @Test
    public void staysQuietAboutWhoBoughtMoreWhenItIsAboutEven() {
        List<Purchase> rows = new ArrayList<>();
        rows.add(InsightTreeTest.buy(InsightTreeTest.day(2026, 11, 3), "Rice", 1000L, 1L));
        rows.add(InsightTreeTest.buy(InsightTreeTest.day(2026, 11, 4), "Rice", 1050L, 2L));
        InsightTree tree = InsightTree.build(rows, InsightTreeTest.ZONE, InsightTreeTest.NOV);

        assertEquals(null, of(InsightGenerator.forNode(tree, tree.root(), 0L),
                Kind.WHO_BOUGHT_MORE));
    }

    @Test
    public void theTopBubbleNamesTheBiggestCategoryAndEveryOtherNamesItsShare() {
        InsightTree tree = tree();

        Insight slice = of(InsightGenerator.forNode(tree, tree.root(), 0L),
                Kind.BIGGEST_SLICE);
        Insight share = of(InsightGenerator.forNode(tree, tree.find("Snacks/Sweet"), 0L),
                Kind.SHARE);

        assertEquals("Snacks", slice.target().name());
        assertEquals(100, slice.percent());
        assertEquals(63, share.percent());
    }

    @Test
    public void withNoPreviousMonthNothingClaimsToCompare() {
        List<Purchase> rows = new ArrayList<>();
        rows.add(InsightTreeTest.buy(InsightTreeTest.day(2026, 11, 3), "Rice", 20000L, 1L));
        InsightTree tree = InsightTree.build(rows, InsightTreeTest.ZONE, InsightTreeTest.NOV);

        for (Node node : new Node[] {tree.root(), tree.find("Pantry")}) {
            for (long seed = 0; seed < 8; seed++) {
                for (Insight insight : InsightGenerator.forNode(tree, node, seed)) {
                    assertFalse(insight.kind() == Kind.VS_LAST_MONTH
                            || insight.kind() == Kind.NEW_THIS_MONTH
                            || insight.kind() == Kind.TOP_MOVER
                            || insight.kind() == Kind.COFFEES);
                }
            }
        }
    }

    @Test
    public void neverMoreThanFourAndTheFunOneChangesWithTheDay() {
        InsightTree tree = tree();
        Node snacks = tree.find("Snacks");
        Set<Kind> funSeen = new HashSet<>();

        for (long seed = 0; seed < 12; seed++) {
            List<Insight> found = InsightGenerator.forNode(tree, snacks, seed);
            assertTrue(found.size() <= 4);
            Kind last = found.get(found.size() - 1).kind();
            if (last == Kind.COFFEES || last == Kind.PER_DAY || last == Kind.BOUGHT_TIMES) {
                funSeen.add(last);
            }
        }
        assertTrue("fun facts should rotate, saw " + funSeen, funSeen.size() >= 2);
    }

    @Test
    public void theSameDayAlwaysGivesTheSameFact() {
        InsightTree tree = tree();
        Node snacks = tree.find("Snacks");

        List<Insight> first = InsightGenerator.forNode(tree, snacks, 20_000L);
        List<Insight> second = InsightGenerator.forNode(tree, snacks, 20_000L);

        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).id(), second.get(i).id());
        }
    }

    @Test
    public void coffeesCountWholeCupsOfTheChange() {
        InsightTree tree = tree();
        // Snacks changed by 2100 cents, which is four whole 500-cent coffees.
        Insight coffees = null;
        for (long seed = 0; seed < 12 && coffees == null; seed++) {
            coffees = of(InsightGenerator.forNode(tree, tree.find("Snacks"), seed),
                    Kind.COFFEES);
        }

        assertNotNull(coffees);
        assertEquals(4, coffees.count());
    }

    private static InsightTree treeWithMissingMoney() {
        List<Purchase> rows = twoMonths();
        com.householdsplitter.core.calc.input.CalcOrder order =
                new com.householdsplitter.core.calc.input.CalcOrder(
                        java.util.Arrays.asList(
                                new com.householdsplitter.core.calc.input.CalcMember(1L, "A", 0),
                                new com.householdsplitter.core.calc.input.CalcMember(2L, "B", 1)),
                        new ArrayList<>(),
                        com.householdsplitter.core.calc.input.Adjustments.none(), 0L,
                        com.householdsplitter.core.calc.AllocationMode.PROPORTIONAL);
        rows.addAll(Purchase.missing(InsightTreeTest.day(2026, 11, 9), 2501L, order));
        return InsightTree.build(rows, InsightTreeTest.ZONE, InsightTreeTest.NOV);
    }

    @Test
    public void moneyTheReaderMissedIsABubbleOfItsOwn() {
        InsightTree tree = treeWithMissingMoney();

        Node missing = tree.missing();

        assertNotNull(missing);
        assertEquals(2501L, missing.thisCents());
        assertEquals("Unread items", tree.find("Missing from the read/Not itemised")
                .children().get(0).name());
        // Two people, so the odd cent goes to one of them and nothing is lost.
        long shared = 0L;
        for (long part : missing.thisByMember().values()) {
            shared += part;
        }
        assertEquals(2501L, shared);
        // The picture adds up to everything charged: rows plus the missing money.
        assertEquals(3800L + 2501L, tree.root().thisCents());
    }

    @Test
    public void theTopBubbleSaysFirstThatMoneyIsMissing() {
        InsightTree tree = treeWithMissingMoney();

        List<Insight> found = InsightGenerator.forNode(tree, tree.root(), 0L);

        assertEquals(Kind.MISSING_MONEY, found.get(0).kind());
        assertEquals(2501L, found.get(0).cents());
        assertEquals(tree.missing(), found.get(0).target());
    }

    @Test
    public void nothingIsSaidAboutMissingMoneyWhenEverythingAddsUp() {
        InsightTree tree = tree();

        for (long seed = 0; seed < 6; seed++) {
            assertEquals(null, of(InsightGenerator.forNode(tree, tree.root(), seed),
                    Kind.MISSING_MONEY));
        }
    }

    @Test
    public void missingMoneyIsNotSaidOnUnrelatedBubbles() {
        InsightTree tree = treeWithMissingMoney();

        assertEquals(null, of(InsightGenerator.forNode(tree, tree.find("Snacks"), 0L),
                Kind.MISSING_MONEY));
    }

    @Test
    public void missingMoneyIsNeverComparedWithLastMonth() {
        List<Purchase> rows = twoMonths();
        com.householdsplitter.core.calc.input.CalcOrder order =
                new com.householdsplitter.core.calc.input.CalcOrder(
                        java.util.Collections.singletonList(
                                new com.householdsplitter.core.calc.input.CalcMember(1L, "A", 0)),
                        new ArrayList<>(),
                        com.householdsplitter.core.calc.input.Adjustments.none(), 0L,
                        com.householdsplitter.core.calc.AllocationMode.PROPORTIONAL);
        rows.addAll(Purchase.missing(InsightTreeTest.day(2026, 10, 9), 50L, order));
        rows.addAll(Purchase.missing(InsightTreeTest.day(2026, 11, 9), 4100L, order));
        InsightTree tree = InsightTree.build(rows, InsightTreeTest.ZONE, InsightTreeTest.NOV);

        for (long seed = 0; seed < 8; seed++) {
            for (Insight insight : InsightGenerator.forNode(tree, tree.missing(), seed)) {
                assertFalse(insight.kind() == Kind.VS_LAST_MONTH
                        || insight.kind() == Kind.NEW_THIS_MONTH
                        || insight.kind() == Kind.TOP_MOVER
                        || insight.kind() == Kind.COFFEES);
            }
        }
    }
}
