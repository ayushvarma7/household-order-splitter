package com.householdsplitter.core.insight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.insight.Trivia.Fact;
import com.householdsplitter.core.insight.Trivia.Kind;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TriviaTest {

    private static List<Purchase> history() {
        List<Purchase> rows = new ArrayList<>();
        // 2026-11-03, 2026-11-10 and 2026-11-17 are Tuesdays; 2026-11-06 is a Friday.
        rows.add(InsightTreeTest.buy(InsightTreeTest.day(2026, 11, 3), "Bananas", 300L, 1L));
        rows.add(InsightTreeTest.buy(InsightTreeTest.day(2026, 11, 3), "Salmon", 2400L, 1L));
        rows.add(InsightTreeTest.buy(InsightTreeTest.day(2026, 11, 6), "Bananas", 350L, 1L));
        rows.add(InsightTreeTest.buy(InsightTreeTest.day(2026, 11, 10), "Bananas", 320L, 1L));
        rows.add(InsightTreeTest.buy(InsightTreeTest.day(2026, 11, 17), "Rice", 900L, 1L));
        return rows;
    }

    private static Fact find(List<Fact> facts, Kind kind) {
        for (Fact fact : facts) {
            if (fact.kind() == kind) {
                return fact;
            }
        }
        return null;
    }

    @Test
    public void answersTheQuestionsFromWhatWasLogged() {
        List<Fact> facts = Trivia.facts(history(), InsightTreeTest.ZONE, 0L);

        Fact most = find(facts, Kind.MOST_BOUGHT_ITEM);
        assertEquals("Bananas", most.name());
        assertEquals(3, most.count());
        assertEquals(970L, most.cents());

        Fact priciest = find(facts, Kind.PRICIEST_ITEM);
        assertEquals("Salmon", priciest.name());
        assertEquals(2400L, priciest.cents());

        Fact weekday = find(facts, Kind.BUSIEST_WEEKDAY);
        assertEquals(DayOfWeek.TUESDAY, weekday.day());
        assertEquals(3, weekday.count());

        Fact average = find(facts, Kind.AVERAGE_SHOPPING_DAY);
        // 300+2400, 350, 320 and 900 over four shopping days.
        assertEquals(4270L / 4, average.cents());
        assertEquals(4, average.count());

        Fact logged = find(facts, Kind.LOGGED_SO_FAR);
        assertEquals(5, logged.count());
        assertEquals(LocalDate.of(2026, 11, 3), logged.since());
    }

    @Test
    public void staysQuietWhereThereIsTooLittleToSay() {
        List<Purchase> one = new ArrayList<>();
        one.add(InsightTreeTest.buy(InsightTreeTest.day(2026, 11, 3), "Rice", 900L, 1L));

        List<Fact> facts = Trivia.facts(one, InsightTreeTest.ZONE, 0L);

        assertEquals(null, find(facts, Kind.MOST_BOUGHT_ITEM));
        assertEquals(null, find(facts, Kind.BUSIEST_WEEKDAY));
        assertEquals(null, find(facts, Kind.AVERAGE_SHOPPING_DAY));
        assertNotNull(find(facts, Kind.PRICIEST_ITEM));
        assertTrue(Trivia.facts(new ArrayList<>(), InsightTreeTest.ZONE, 0L).isEmpty());
    }

    @Test
    public void theDayDecidesWhichFactComesFirstAndNothingIsLost() {
        List<Fact> today = Trivia.facts(history(), InsightTreeTest.ZONE, 0L);
        List<Fact> tomorrow = Trivia.facts(history(), InsightTreeTest.ZONE, 1L);

        assertEquals(today.size(), tomorrow.size());
        assertTrue(today.get(0).kind() != tomorrow.get(0).kind());
        assertEquals(today.get(1).kind(), tomorrow.get(0).kind());
    }

    @Test
    public void missingMoneyIsNotSomethingThatWasBought() {
        List<Purchase> rows = history();
        com.householdsplitter.core.calc.input.CalcOrder order =
                new com.householdsplitter.core.calc.input.CalcOrder(
                        java.util.Collections.singletonList(
                                new com.householdsplitter.core.calc.input.CalcMember(1L, "A", 0)),
                        new ArrayList<>(),
                        com.householdsplitter.core.calc.input.Adjustments.none(), 0L,
                        com.householdsplitter.core.calc.AllocationMode.PROPORTIONAL);
        rows.addAll(Purchase.missing(InsightTreeTest.day(2026, 11, 20), 99999L, order));

        assertEquals("Salmon", find(Trivia.facts(rows, InsightTreeTest.ZONE, 0L),
                Kind.PRICIEST_ITEM).name());
    }
}
