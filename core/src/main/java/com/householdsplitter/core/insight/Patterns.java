package com.householdsplitter.core.insight;

import com.householdsplitter.core.suggest.NameNormalizer;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Three pictures of a household's shopping that a single month's bubbles cannot show: who
 * buys things together, which days the money goes out, and how the price of a regular item
 * moves between shops and over time.
 *
 * <p>Every figure is a sum of {@link Purchase} amounts, which are themselves the split
 * calculator's own shares, so nothing here can disagree with what anybody was asked to
 * pay. Money stays in long cents throughout.
 */
public final class Patterns {

    private Patterns() {
    }

    // ---------------------------------------------------------------- sharing web

    /** One person in the web, sized by everything they had a share of. */
    public static final class Node {
        public final long memberId;
        public final long cents;

        Node(long memberId, long cents) {
            this.memberId = memberId;
            this.cents = cents;
        }
    }

    /** Two people and what they bought together without the rest of the order. */
    public static final class Edge {
        public final long a;
        public final long b;
        /** Both of their shares of the items they had in common. */
        public final long cents;
        public final int items;

        Edge(long a, long b, long cents, int items) {
            this.a = a;
            this.b = b;
            this.cents = cents;
            this.items = items;
        }
    }

    public static final class Web {
        public final List<Node> nodes;
        /** Heaviest first. */
        public final List<Edge> edges;

        Web(List<Node> nodes, List<Edge> edges) {
            this.nodes = Collections.unmodifiableList(nodes);
            this.edges = Collections.unmodifiableList(edges);
        }

        public long heaviest() {
            return edges.isEmpty() ? 0L : edges.get(0).cents;
        }
    }

    /**
     * Who shares with whom.
     *
     * <p>An item everybody on its order was in on says nothing about any one pair: it would
     * thicken every line by the same amount and the picture would only show who was in the
     * house that week. So those are left out, and a line is drawn from the items a subset
     * of the order shared. The people on an order are taken as everyone with a share of
     * anything bought that day, which is what the purchases record.
     */
    public static Web sharingWeb(List<Purchase> purchases) {
        Map<Long, Set<Long>> peopleByDay = new HashMap<>();
        Map<Long, Long> totals = new LinkedHashMap<>();
        for (Purchase p : purchases) {
            peopleByDay.computeIfAbsent(p.dateMillis(), d -> new LinkedHashSet<>())
                    .addAll(p.centsByMember().keySet());
            for (Map.Entry<Long, Long> share : p.centsByMember().entrySet()) {
                totals.merge(share.getKey(), share.getValue(), Long::sum);
            }
        }
        Map<String, long[]> pairs = new LinkedHashMap<>();
        for (Purchase p : purchases) {
            if (p.isMissing() || p.centsByMember().size() < 2) {
                continue;
            }
            Set<Long> onOrder = peopleByDay.get(p.dateMillis());
            if (onOrder != null && p.centsByMember().keySet().containsAll(onOrder)) {
                continue;
            }
            List<Long> ids = new ArrayList<>(p.centsByMember().keySet());
            Collections.sort(ids);
            for (int i = 0; i < ids.size(); i++) {
                for (int j = i + 1; j < ids.size(); j++) {
                    long a = ids.get(i);
                    long b = ids.get(j);
                    long[] pair = pairs.computeIfAbsent(a + ":" + b, k -> new long[]{a, b, 0, 0});
                    pair[2] += p.centsByMember().get(a) + p.centsByMember().get(b);
                    pair[3] += 1;
                }
            }
        }
        List<Node> nodes = new ArrayList<>();
        for (Map.Entry<Long, Long> t : totals.entrySet()) {
            nodes.add(new Node(t.getKey(), t.getValue()));
        }
        List<Edge> edges = new ArrayList<>();
        for (long[] pair : pairs.values()) {
            edges.add(new Edge(pair[0], pair[1], pair[2], (int) pair[3]));
        }
        edges.sort((x, y) -> x.cents != y.cents ? Long.compare(y.cents, x.cents)
                : Integer.compare(y.items, x.items));
        return new Web(nodes, edges);
    }

    // ---------------------------------------------------------------- calendar

    public static final class Calendar {
        /** The first day shown, always a Monday. */
        public final LocalDate start;
        /** The last day shown. */
        public final LocalDate end;
        /** Spending per day; days with none are absent. */
        public final Map<LocalDate, Long> centsByDay;
        /** The busiest day of the week by money, or null with no spending. */
        public final DayOfWeek busiestWeekday;
        public final long busiestWeekdayCents;
        /** The longest run of days without a shop inside the window, in days. */
        public final int longestGap;
        public final int shoppingDays;

        Calendar(LocalDate start, LocalDate end, Map<LocalDate, Long> centsByDay,
                 DayOfWeek busiestWeekday, long busiestWeekdayCents, int longestGap,
                 int shoppingDays) {
            this.start = start;
            this.end = end;
            this.centsByDay = Collections.unmodifiableMap(centsByDay);
            this.busiestWeekday = busiestWeekday;
            this.busiestWeekdayCents = busiestWeekdayCents;
            this.longestGap = longestGap;
            this.shoppingDays = shoppingDays;
        }

        public long maxDay() {
            long max = 0L;
            for (long cents : centsByDay.values()) {
                max = Math.max(max, cents);
            }
            return max;
        }

        public int weeks() {
            return (int) ((end.toEpochDay() - start.toEpochDay()) / 7) + 1;
        }
    }

    /**
     * Spending per day over the {@code weeks} weeks ending with the latest shop. Ends at
     * the latest shop rather than today, so a household that paused for a month still sees
     * its shopping rather than an empty grid.
     */
    public static Calendar calendar(List<Purchase> purchases, ZoneId zone, int weeks) {
        Map<LocalDate, Long> all = new TreeMap<>();
        for (Purchase p : purchases) {
            LocalDate day = Instant.ofEpochMilli(p.dateMillis()).atZone(zone).toLocalDate();
            all.merge(day, p.cents(), Long::sum);
        }
        LocalDate end = all.isEmpty() ? LocalDate.now(zone)
                : ((TreeMap<LocalDate, Long>) all).lastKey();
        LocalDate start = end.minusWeeks(Math.max(1, weeks) - 1L)
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<LocalDate, Long> shown = new TreeMap<>();
        Map<DayOfWeek, Long> byWeekday = new HashMap<>();
        for (Map.Entry<LocalDate, Long> e : all.entrySet()) {
            if (!e.getKey().isBefore(start) && !e.getKey().isAfter(end)) {
                shown.put(e.getKey(), e.getValue());
                byWeekday.merge(e.getKey().getDayOfWeek(), e.getValue(), Long::sum);
            }
        }
        DayOfWeek busiest = null;
        long busiestCents = 0L;
        for (DayOfWeek day : DayOfWeek.values()) {
            long cents = byWeekday.getOrDefault(day, 0L);
            if (cents > busiestCents) {
                busiest = day;
                busiestCents = cents;
            }
        }
        int gap = 0;
        LocalDate previous = null;
        for (LocalDate day : shown.keySet()) {
            if (previous != null) {
                gap = Math.max(gap, (int) (day.toEpochDay() - previous.toEpochDay()) - 1);
            }
            previous = day;
        }
        return new Calendar(start, end, shown, busiest, busiestCents, gap, shown.size());
    }

    // ---------------------------------------------------------------- price tracker

    /** One time an item was bought. */
    public static final class PricePoint {
        public final long dateMillis;
        public final long cents;
        public final String store;

        PricePoint(long dateMillis, long cents, String store) {
            this.dateMillis = dateMillis;
            this.cents = cents;
            this.store = store;
        }
    }

    /** An item bought more than once, oldest purchase first. */
    public static final class PriceSeries {
        public final String name;
        public final List<PricePoint> points;

        PriceSeries(String name, List<PricePoint> points) {
            this.name = name;
            this.points = Collections.unmodifiableList(points);
        }

        public long lowest() {
            long low = Long.MAX_VALUE;
            for (PricePoint p : points) {
                low = Math.min(low, p.cents);
            }
            return low;
        }

        public long highest() {
            long high = 0L;
            for (PricePoint p : points) {
                high = Math.max(high, p.cents);
            }
            return high;
        }

        /** Latest minus first, in cents: positive means it got dearer. */
        public long change() {
            return points.get(points.size() - 1).cents - points.get(0).cents;
        }

        /** The store where it was cheapest, or null when bought at one store only. */
        public String cheapestStore() {
            Map<String, Long> low = new LinkedHashMap<>();
            for (PricePoint p : points) {
                low.merge(p.store, p.cents, Math::min);
            }
            if (low.size() < 2) {
                return null;
            }
            String best = null;
            long bestCents = Long.MAX_VALUE;
            for (Map.Entry<String, Long> e : low.entrySet()) {
                if (e.getValue() < bestCents) {
                    best = e.getKey();
                    bestCents = e.getValue();
                }
            }
            return best;
        }
    }

    /**
     * Items bought at least {@code minTimes} times, most often bought first, at most
     * {@code limit} of them. Names are grouped by the same normaliser assignment memory
     * uses, so "Great Value Whole Milk, 1 Gallon" and "GREAT VALUE WHOLE MILK 1 GALLON"
     * are one item. Prices are what the row cost, so a row bought in twos shows double.
     */
    public static List<PriceSeries> priceTracker(List<Purchase> purchases, int minTimes,
                                                 int limit) {
        Map<String, List<Purchase>> byKey = new LinkedHashMap<>();
        for (Purchase p : purchases) {
            if (p.isMissing() || p.cents() <= 0L) {
                continue;
            }
            String key = NameNormalizer.normalize(p.name());
            if (key.isEmpty()) {
                continue;
            }
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }
        List<PriceSeries> series = new ArrayList<>();
        for (List<Purchase> group : byKey.values()) {
            if (group.size() < Math.max(2, minTimes)) {
                continue;
            }
            group.sort((x, y) -> Long.compare(x.dateMillis(), y.dateMillis()));
            List<PricePoint> points = new ArrayList<>();
            for (Purchase p : group) {
                points.add(new PricePoint(p.dateMillis(), p.cents(), p.store()));
            }
            // The most recent spelling is the one the household will recognise.
            series.add(new PriceSeries(group.get(group.size() - 1).name(), points));
        }
        series.sort((x, y) -> x.points.size() != y.points.size()
                ? Integer.compare(y.points.size(), x.points.size())
                : x.name.compareToIgnoreCase(y.name));
        return series.size() > limit ? new ArrayList<>(series.subList(0, limit)) : series;
    }
}
