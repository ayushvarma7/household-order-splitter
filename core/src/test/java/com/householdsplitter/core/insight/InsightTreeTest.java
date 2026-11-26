package com.householdsplitter.core.insight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.insight.InsightTree.Node;

import org.junit.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InsightTreeTest {

    static final ZoneId ZONE = ZoneOffset.UTC;
    static final YearMonth NOV = YearMonth.of(2026, 11);

    static long day(int year, int month, int dayOfMonth) {
        return LocalDate.of(year, month, dayOfMonth).atStartOfDay(ZONE).toInstant()
                .toEpochMilli();
    }

    static Purchase buy(long date, String name, long cents, long memberId) {
        return new Purchase(date, name, cents, Collections.singletonMap(memberId, cents));
    }

    private static List<Purchase> sample() {
        List<Purchase> rows = new ArrayList<>();
        rows.add(buy(day(2026, 11, 3), "Tortilla Chips", 1400L, 1L));
        rows.add(buy(day(2026, 11, 3), "Cookies", 1600L, 2L));
        rows.add(buy(day(2026, 11, 9), "Spinach", 1400L, 1L));
        rows.add(buy(day(2026, 10, 5), "Tortilla Chips", 900L, 1L));
        rows.add(buy(day(2026, 10, 5), "Popcorn", 400L, 1L));
        rows.add(buy(day(2026, 9, 1), "Cookies", 9999L, 1L));
        return rows;
    }

    @Test
    public void aBubbleIsTheSumOfTheBubblesInsideIt() {
        InsightTree tree = InsightTree.build(sample(), ZONE, NOV);

        Node root = tree.root();
        long sum = 0L;
        for (Node category : root.children()) {
            sum += category.thisCents();
        }
        assertEquals(4400L, root.thisCents());
        assertEquals(root.thisCents(), sum);
    }

    @Test
    public void lastMonthIsCountedInFullEvenForThingsNotBoughtThisMonth() {
        InsightTree tree = InsightTree.build(sample(), ZONE, NOV);

        Node snacks = tree.find("Snacks");
        // Chips 900 and popcorn 400 last month, although popcorn was not bought now.
        assertEquals(1300L, snacks.lastCents());
        assertEquals(3000L, snacks.thisCents());
        assertNull(findChild(tree.find("Snacks/Salty"), "Popcorn"));
        assertEquals(1, tree.find("Snacks/Salty").children().size());
    }

    @Test
    public void monthsOutsideTheTwoComparedAreIgnored() {
        InsightTree tree = InsightTree.build(sample(), ZONE, NOV);

        Node cookies = tree.find("Snacks/Sweet/Cookies");
        assertEquals(1600L, cookies.thisCents());
        assertEquals(0L, cookies.lastCents());
    }

    @Test
    public void biggestBubblesComeFirst() {
        InsightTree tree = InsightTree.build(sample(), ZONE, NOV);

        assertEquals("Snacks", tree.root().children().get(0).name());
    }

    @Test
    public void everyoneSharesAreKeptPerPerson() {
        InsightTree tree = InsightTree.build(sample(), ZONE, NOV);

        Node snacks = tree.find("Snacks");
        assertEquals(Long.valueOf(1400L), snacks.thisByMember().get(1L));
        assertEquals(Long.valueOf(1600L), snacks.thisByMember().get(2L));
    }

    @Test
    public void namesThatTidyToTheSameThingBecomeOneBubble() {
        List<Purchase> rows = new ArrayList<>();
        rows.add(buy(day(2026, 11, 1), "Whole Milk, 1 Gallon", 400L, 1L));
        rows.add(buy(day(2026, 11, 8), "WHOLE MILK,   1 GALLON", 410L, 1L));

        InsightTree tree = InsightTree.build(rows, ZONE, NOV);

        Node milk = tree.find("Dairy/Milk and cream");
        assertEquals(1, milk.children().size());
        assertEquals(810L, milk.children().get(0).thisCents());
        assertEquals(2, milk.children().get(0).purchaseCount());
    }

    @Test
    public void thereIsNothingToCompareWithoutThePreviousMonth() {
        List<Purchase> rows = new ArrayList<>();
        rows.add(buy(day(2026, 11, 1), "Rice", 400L, 1L));

        assertFalse(InsightTree.build(rows, ZONE, NOV).hasPreviousMonth());
        assertTrue(InsightTree.build(sample(), ZONE, NOV).hasPreviousMonth());
    }

    @Test
    public void aPathFindsTheSameNodeAndFallsBackToTheNearestOneThatExists() {
        InsightTree tree = InsightTree.build(sample(), ZONE, NOV);

        Node chips = tree.find("Snacks/Salty/Tortilla Chips");
        assertSame(chips, tree.find(chips.path()));
        assertSame(tree.find("Snacks"), tree.find("Snacks/Nonexistent"));
        assertSame(tree.root(), tree.find(""));
    }

    @Test
    public void theLatestMonthIsTheOneMostRecentlyShoppedIn() {
        assertEquals(NOV, InsightTree.latestMonth(sample(), ZONE));
        assertNull(InsightTree.latestMonth(new ArrayList<>(), ZONE));
    }

    @Test
    public void aStoreBrandTheHouseholdKeepsBuyingIsLeftOffTheBubble() {
        List<Purchase> rows = new ArrayList<>();
        rows.add(buy(day(2026, 11, 3), "Great Value Long Grain Rice, 5 lb", 400L, 1L));
        rows.add(buy(day(2026, 11, 3), "Great Value Whole Milk, 1 Gallon", 300L, 1L));
        rows.add(buy(day(2026, 11, 4), "Great Value Pinto Beans", 200L, 1L));
        rows.add(buy(day(2026, 11, 4), "Whole Milk", 300L, 1L));
        rows.add(buy(day(2026, 11, 5), "Great Value", 100L, 1L));

        List<String> names = new ArrayList<>();
        collectLeaves(InsightTree.build(rows, ZONE, NOV).root(), names);

        assertTrue(names.toString(), names.contains("Long Grain Rice"));
        assertTrue(names.toString(), names.contains("Pinto Beans"));
        // The brand is not what was bought, so the two milks are one bubble.
        assertEquals(names.toString(), 1, Collections.frequency(names, "Whole Milk"));
        // Nothing but the brand: keep it rather than show an empty name.
        assertTrue(names.toString(), names.contains("Great Value"));
        assertFalse(names.toString(), names.stream().anyMatch(n -> n.startsWith("Great Value ")));
    }

    @Test
    public void aWordOnlyOneProductStartsWithIsNotTakenForABrand() {
        List<Purchase> rows = new ArrayList<>();
        rows.add(buy(day(2026, 11, 3), "Great Value Long Grain Rice", 400L, 1L));
        rows.add(buy(day(2026, 11, 3), "Tortilla Chips", 300L, 1L));

        List<String> names = new ArrayList<>();
        collectLeaves(InsightTree.build(rows, ZONE, NOV).root(), names);

        assertTrue(names.toString(), names.contains("Great Value Long Grain Rice"));
    }

    private static void collectLeaves(Node node, List<String> out) {
        if (node.children().isEmpty()) {
            out.add(node.name());
        }
        for (Node child : node.children()) {
            collectLeaves(child, out);
        }
    }

    private static Node findChild(Node parent, String name) {
        for (Node child : parent.children()) {
            if (child.name().equals(name)) {
                return child;
            }
        }
        return null;
    }
}
