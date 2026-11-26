package com.householdsplitter.insight;

import com.householdsplitter.core.calc.AllocationMode;
import com.householdsplitter.core.calc.input.Adjustments;
import com.householdsplitter.core.calc.input.CalcLineItem;
import com.householdsplitter.core.calc.input.CalcMember;
import com.householdsplitter.core.calc.input.CalcOrder;
import com.householdsplitter.core.insight.Purchase;
import com.householdsplitter.data.entity.Member;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Made-up orders, so the look-inside screen can be tried on a phone that has none yet.
 *
 * <p>Nothing here touches the database. The people are called "You" and "Partner" and carry
 * negative ids so they can never be mistaken for, or clash with, anyone in a real group, and
 * the screen says plainly that it is showing an example.
 */
public final class InsightSample {

    private static final long YOU = -1L;
    private static final long PARTNER = -2L;

    private InsightSample() {
    }

    /** Two months of shopping ending today, with one order that is short of its bill. */
    public static InsightService.Loaded create() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate first = LocalDate.now(zone).withDayOfMonth(1);
        Member you = member(YOU, "You", "#1F6FB2", 0);
        Member partner = member(PARTNER, "Partner", "#B3261E", 1);

        InsightService.Loaded loaded = new InsightService.Loaded();
        loaded.members.put(YOU, you);
        loaded.members.put(PARTNER, partner);

        // 'c' shared, 'y' for You, 'p' for Partner. The last figure is how much the bill
        // charged beyond the rows, which is what makes an order short.
        Object[][] orders = {
            {1, 5, 0L, new Object[][]{{"Tortilla Chips", 900L, 'c'}, {"Cookies", 800L, 'y'},
                {"Spinach", 1100L, 'c'}, {"Whole Milk", 450L, 'c'}, {"Coffee Beans", 1800L, 'y'},
                {"Rice", 1900L, 'c'}, {"Pasta", 1000L, 'c'}, {"Olive Oil", 2100L, 'c'},
                {"Salmon Fillet", 2300L, 'y'}, {"Diet Coke 12 pack", 3600L, 'p'},
                {"Bananas", 500L, 'c'}}},
            {1, 19, 0L, new Object[][]{{"Bananas", 500L, 'c'}, {"Ice Cream", 900L, 'p'},
                {"Eggs", 700L, 'c'}, {"Chicken Breast", 1700L, 'c'}, {"Spinach", 600L, 'c'},
                {"Rice", 1200L, 'c'}}},
            {0, 2, 0L, new Object[][]{{"Tortilla Chips", 1400L, 'c'}, {"Cookies", 1600L, 'y'},
                {"Spinach", 1400L, 'c'}, {"Whole Milk", 460L, 'c'},
                {"Diet Coke 12 pack", 3200L, 'p'}, {"Bananas", 320L, 'c'}}},
            {0, 4, 2400L, new Object[][]{{"Chicken Breast", 1900L, 'c'},
                {"Salmon Fillet", 2400L, 'y'}, {"Bananas", 300L, 'c'}, {"Rice", 1200L, 'c'}}},
            {0, 6, 0L, new Object[][]{{"Bananas", 350L, 'c'}, {"Ice Cream", 1100L, 'p'},
                {"Pasta", 400L, 'c'}, {"Olive Oil", 1300L, 'c'}, {"Coffee Beans", 1800L, 'y'}}},
        };

        List<CalcMember> people = Arrays.asList(
                new CalcMember(YOU, "You", 0), new CalcMember(PARTNER, "Partner", 1));
        for (Object[] order : orders) {
            int monthsAgo = (Integer) order[0];
            // Never later than today, so an example opened early in the month still has a
            // sensible set of dates rather than ones in the future.
            LocalDate date = first.minusMonths(monthsAgo).withDayOfMonth(
                    Math.min((Integer) order[1], first.minusMonths(monthsAgo).lengthOfMonth()));
            if (monthsAgo == 0 && date.isAfter(LocalDate.now(zone))) {
                date = LocalDate.now(zone);
            }
            long millis = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli();

            List<CalcLineItem> items = new ArrayList<>();
            long id = 1L;
            for (Object[] row : (Object[][]) order[3]) {
                String name = (String) row[0];
                long cents = (Long) row[1];
                char who = (Character) row[2];
                items.add(who == 'c' ? CalcLineItem.common(id++, name, cents)
                        : CalcLineItem.personal(id++, name, cents, who == 'y' ? YOU : PARTNER));
            }
            CalcOrder calc = new CalcOrder(people, items, Adjustments.none(), 0L,
                    AllocationMode.PROPORTIONAL);
            loaded.purchases.addAll(Purchase.fromOrder(millis, calc));
            long missing = (Long) order[2];
            if (missing > 0L) {
                loaded.purchases.addAll(Purchase.missing(millis, missing, calc));
            }
        }
        return loaded;
    }

    private static Member member(long id, String name, String color, int sortOrder) {
        Member member = new Member(0L, name, color, sortOrder);
        member.id = id;
        return member;
    }
}
