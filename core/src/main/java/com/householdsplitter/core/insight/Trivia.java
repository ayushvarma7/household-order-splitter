package com.householdsplitter.core.insight;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Things about the household's shopping that are interesting rather than urgent, each one
 * answering a question somebody might actually ask: what do we buy most, what was the
 * priciest thing, which day do we shop.
 *
 * <p>These come from everything ever logged, not one month, so they change slowly and a new
 * order is what moves them. What changes daily is only which one is shown first, which gives
 * a reason to open the screen again without ever inventing a number.
 */
public final class Trivia {

    /** Which question a fact answers. */
    public enum Kind {
        MOST_BOUGHT_ITEM,
        PRICIEST_ITEM,
        BUSIEST_WEEKDAY,
        AVERAGE_SHOPPING_DAY,
        LOGGED_SO_FAR
    }

    /** One answer. Fields that do not apply to its kind are empty. */
    public static final class Fact {

        private final Kind kind;
        private final String name;
        private final long cents;
        private final int count;
        private final DayOfWeek day;
        private final LocalDate since;

        Fact(Kind kind, String name, long cents, int count, DayOfWeek day, LocalDate since) {
            this.kind = kind;
            this.name = name;
            this.cents = cents;
            this.count = count;
            this.day = day;
            this.since = since;
        }

        public Kind kind() {
            return kind;
        }

        /** An item name, for the item questions. */
        public String name() {
            return name;
        }

        public long cents() {
            return cents;
        }

        /** Times bought, shopping days, or rows logged, per the kind. */
        public int count() {
            return count;
        }

        public DayOfWeek day() {
            return day;
        }

        /** The first day anything was logged. */
        public LocalDate since() {
            return since;
        }
    }

    private Trivia() {
    }

    /**
     * @param daySeed the number of the day, which decides which fact comes first
     */
    public static List<Fact> facts(List<Purchase> purchases, ZoneId zone, long daySeed) {
        List<Purchase> real = new ArrayList<>();
        for (Purchase purchase : purchases) {
            if (!purchase.isMissing()) {
                real.add(purchase);
            }
        }
        if (real.isEmpty()) {
            return Collections.emptyList();
        }

        Map<LocalDate, Long> centsByDay = new LinkedHashMap<>();
        Map<String, Integer> timesBought = new HashMap<>();
        Map<String, Long> centsByItem = new HashMap<>();
        Map<String, String> shownAs = new HashMap<>();
        Purchase priciest = null;
        LocalDate first = null;
        Set<String> brands = InsightTree.brandsOf(real);
        for (Purchase purchase : real) {
            LocalDate day = Instant.ofEpochMilli(purchase.dateMillis()).atZone(zone)
                    .toLocalDate();
            centsByDay.merge(day, purchase.cents(), Long::sum);
            if (first == null || day.isBefore(first)) {
                first = day;
            }
            String item = InsightTree.keyOf(purchase.name(), brands);
            shownAs.putIfAbsent(item, InsightTree.displayName(purchase.name(), brands));
            timesBought.merge(item, 1, Integer::sum);
            centsByItem.merge(item, purchase.cents(), Long::sum);
            if (priciest == null || purchase.cents() > priciest.cents()) {
                priciest = purchase;
            }
        }

        List<Fact> out = new ArrayList<>();

        String mostName = null;
        int mostCount = 1;
        for (Map.Entry<String, Integer> entry : timesBought.entrySet()) {
            int count = entry.getValue();
            boolean better = count > mostCount || (count == mostCount && mostName != null
                    && (centsByItem.get(entry.getKey()) > centsByItem.get(mostName)
                    || (centsByItem.get(entry.getKey()).equals(centsByItem.get(mostName))
                    && entry.getKey().compareTo(mostName) < 0)));
            if (count >= 2 && (mostName == null || better)) {
                mostName = entry.getKey();
                mostCount = count;
            }
        }
        if (mostName != null) {
            out.add(new Fact(Kind.MOST_BOUGHT_ITEM, shownAs.get(mostName), centsByItem.get(mostName),
                    mostCount, null, null));
        }

        out.add(new Fact(Kind.PRICIEST_ITEM, InsightTree.displayName(priciest.name(), brands),
                priciest.cents(), 1, null, null));

        if (centsByDay.size() >= 3) {
            Map<DayOfWeek, Set<LocalDate>> daysByWeekday = new HashMap<>();
            for (LocalDate day : centsByDay.keySet()) {
                daysByWeekday.computeIfAbsent(day.getDayOfWeek(), k -> new HashSet<>()).add(day);
            }
            DayOfWeek busiest = null;
            for (DayOfWeek weekday : DayOfWeek.values()) {
                Set<LocalDate> days = daysByWeekday.get(weekday);
                if (days != null && (busiest == null
                        || days.size() > daysByWeekday.get(busiest).size())) {
                    busiest = weekday;
                }
            }
            out.add(new Fact(Kind.BUSIEST_WEEKDAY, null, 0L,
                    daysByWeekday.get(busiest).size(), busiest, null));
        }

        if (centsByDay.size() >= 2) {
            long total = 0L;
            for (long cents : centsByDay.values()) {
                total += cents;
            }
            out.add(new Fact(Kind.AVERAGE_SHOPPING_DAY, null, total / centsByDay.size(),
                    centsByDay.size(), null, null));
        }

        out.add(new Fact(Kind.LOGGED_SO_FAR, null, 0L, real.size(), null, first));

        Collections.rotate(out, (int) -Math.floorMod(daySeed, (long) out.size()));
        return out;
    }
}
