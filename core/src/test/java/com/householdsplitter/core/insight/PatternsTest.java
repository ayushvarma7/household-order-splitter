package com.householdsplitter.core.insight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PatternsTest {

    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final long ANA = 1L;
    private static final long BEN = 2L;
    private static final long CHEN = 3L;

    private static long day(int year, int month, int dayOfMonth) {
        return LocalDate.of(year, month, dayOfMonth).atStartOfDay(UTC).toInstant().toEpochMilli();
    }

    /** An item split evenly between the given people, with any remainder on the first. */
    private static Purchase item(long date, String name, long cents, long... people) {
        Map<Long, Long> shares = new LinkedHashMap<>();
        long each = cents / people.length;
        long rest = cents - each * people.length;
        for (int i = 0; i < people.length; i++) {
            shares.put(people[i], each + (i == 0 ? rest : 0L));
        }
        return new Purchase(date, name, cents, shares);
    }

    @Test
    public void itemsForTheWholeOrderDrawNoLine() {
        long d = day(2026, 9, 3);
        Patterns.Web web = Patterns.sharingWeb(Arrays.asList(
                item(d, "Milk", 300L, ANA, BEN, CHEN),
                item(d, "Bread", 250L, ANA, BEN, CHEN),
                item(d, "Coffee", 900L, ANA)));
        assertTrue("nobody shared anything without the third person", web.edges.isEmpty());
        assertEquals("but all three are in the picture", 3, web.nodes.size());
    }

    @Test
    public void aPairThatBuysTogetherGetsTheHeaviestLine() {
        long d = day(2026, 9, 3);
        Patterns.Web web = Patterns.sharingWeb(Arrays.asList(
                item(d, "Milk", 300L, ANA, BEN, CHEN),
                item(d, "Eggs", 400L, ANA, CHEN),
                item(d, "Spinach", 200L, ANA, CHEN),
                item(d, "Beer", 1000L, BEN, CHEN),
                item(day(2026, 9, 10), "Oat milk", 500L, ANA, CHEN),
                item(day(2026, 9, 10), "Razors", 800L, BEN)));
        Patterns.Edge top = web.edges.get(0);
        assertEquals(ANA, top.a);
        assertEquals(CHEN, top.b);
        assertEquals("eggs, spinach and oat milk", 1100L, top.cents);
        assertEquals(3, top.items);
        assertEquals("Ben and Chen come second", 1000L, web.edges.get(1).cents);
        assertEquals(web.heaviest(), top.cents);
    }

    @Test
    public void wholeOrderMeansEveryoneOnThatDayNotEveryoneEver() {
        // A two-person order: what they share is the whole order, so it draws no line, even
        // though a third person exists in the household.
        Patterns.Web web = Patterns.sharingWeb(Arrays.asList(
                item(day(2026, 9, 3), "Pizza", 2000L, ANA, BEN),
                item(day(2026, 9, 4), "Milk", 300L, ANA, BEN, CHEN),
                item(day(2026, 9, 4), "Wine", 1500L, ANA, BEN)));
        assertEquals(1, web.edges.size());
        assertEquals("only the wine, on the day Chen was in", 1500L, web.edges.get(0).cents);
    }

    @Test
    public void theCalendarAddsUpEachDayAndEndsAtTheLatestShop() {
        List<Purchase> rows = new ArrayList<>();
        rows.add(item(day(2026, 9, 5), "Milk", 300L, ANA));      // Saturday
        rows.add(item(day(2026, 9, 5), "Eggs", 400L, BEN));
        rows.add(item(day(2026, 9, 12), "Bread", 250L, ANA));    // Saturday
        rows.add(item(day(2026, 9, 15), "Coffee", 900L, CHEN));  // Tuesday
        Patterns.Calendar cal = Patterns.calendar(rows, UTC, 4);

        assertEquals(LocalDate.of(2026, 9, 15), cal.end);
        assertEquals(DayOfWeek.MONDAY, cal.start.getDayOfWeek());
        assertEquals(Long.valueOf(700L), cal.centsByDay.get(LocalDate.of(2026, 9, 5)));
        assertEquals("the Tuesday coffee is the biggest single day", 900L, cal.maxDay());
        assertEquals(3, cal.shoppingDays);
        assertEquals("Saturdays carry 9.50", DayOfWeek.SATURDAY, cal.busiestWeekday);
        assertEquals(950L, cal.busiestWeekdayCents);
        assertEquals("the 5th to the 12th leaves six empty days", 6, cal.longestGap);
    }

    @Test
    public void theCalendarLeavesOutShopsBeforeItsWindow() {
        List<Purchase> rows = Arrays.asList(
                item(day(2026, 1, 1), "Old", 5000L, ANA),
                item(day(2026, 9, 15), "New", 100L, ANA));
        Patterns.Calendar cal = Patterns.calendar(rows, UTC, 2);
        assertEquals(1, cal.centsByDay.size());
        assertEquals(2, cal.weeks());
    }

    @Test
    public void anEmptyHistoryGivesAnEmptyCalendar() {
        Patterns.Calendar cal = Patterns.calendar(new ArrayList<>(), UTC, 8);
        assertEquals(0, cal.shoppingDays);
        assertNull(cal.busiestWeekday);
        assertEquals(0L, cal.maxDay());
    }

    @Test
    public void theTrackerGroupsSpellingsAndComparesStores() {
        List<Purchase> rows = Arrays.asList(
                item(day(2026, 9, 3), "Great Value Whole Milk, 1 Gallon", 348L, ANA)
                        .atStore("Walmart"),
                item(day(2026, 9, 10), "GREAT VALUE WHOLE MILK, 1 GALLON", 299L, ANA)
                        .atStore("Costco"),
                item(day(2026, 9, 17), "Great Value  Whole Milk, 1 Gallon ", 362L, ANA)
                        .atStore("Walmart"),
                item(day(2026, 9, 3), "Bananas", 99L, ANA).atStore("Walmart"));
        List<Patterns.PriceSeries> series = Patterns.priceTracker(rows, 2, 5);

        assertEquals("bananas were bought once, so only milk", 1, series.size());
        Patterns.PriceSeries milk = series.get(0);
        assertEquals(3, milk.points.size());
        assertEquals("oldest first", 348L, milk.points.get(0).cents);
        assertEquals(299L, milk.lowest());
        assertEquals(362L, milk.highest());
        assertEquals("up 14 cents since the first", 14L, milk.change());
        assertEquals("Costco", milk.cheapestStore());
    }

    @Test
    public void oneStoreHasNoCheapestStore() {
        List<Purchase> rows = Arrays.asList(
                item(day(2026, 9, 3), "Eggs", 400L, ANA).atStore("Aldi"),
                item(day(2026, 9, 10), "Eggs", 380L, ANA).atStore("Aldi"));
        assertNull(Patterns.priceTracker(rows, 2, 5).get(0).cheapestStore());
    }

    @Test
    public void theTrackerKeepsTheMostBoughtItemsWithinItsLimit() {
        List<Purchase> rows = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            rows.add(item(day(2026, 9, i), "Milk", 300L, ANA));
        }
        for (int i = 1; i <= 2; i++) {
            rows.add(item(day(2026, 9, i), "Eggs", 400L, ANA));
            rows.add(item(day(2026, 9, i), "Bread", 250L, ANA));
        }
        List<Patterns.PriceSeries> series = Patterns.priceTracker(rows, 2, 2);
        assertEquals(2, series.size());
        assertEquals("Milk", series.get(0).name);
        assertEquals("ties go alphabetically", "Bread", series.get(1).name);
    }
}
