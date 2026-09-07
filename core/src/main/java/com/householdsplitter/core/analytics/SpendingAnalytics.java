package com.householdsplitter.core.analytics;

import com.householdsplitter.core.calc.AdjustmentType;
import com.householdsplitter.core.calc.result.ItemShare;
import com.householdsplitter.core.calc.result.MemberSplit;
import com.householdsplitter.core.calc.result.SplitResult;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the household's shopping adds up to over time.
 *
 * <p>Plain Java over already-calculated orders, so it runs under JUnit and never re-derives
 * a total: every figure here is a sum of figures the split calculator produced, which means
 * the analytics can never disagree with what a person was actually asked to pay.
 *
 * <p>Shares are integer permille rather than percentages in floating point, for the same
 * reason the money is {@code long} cents: an exact number that always adds up.
 */
public final class SpendingAnalytics {

    private SpendingAnalytics() {
    }

    /** One calculated order, as the analytics sees it. */
    public static final class OrderPoint {

        private final long dateMillis;
        private final String label;
        private final SplitResult result;

        public OrderPoint(long dateMillis, String label, SplitResult result) {
            this.dateMillis = dateMillis;
            this.label = label == null ? "" : label;
            this.result = result;
        }

        public long dateMillis() {
            return dateMillis;
        }

        public String label() {
            return label;
        }

        public SplitResult result() {
            return result;
        }
    }

    /** One member's standing across every order. */
    public static final class MemberTotal {

        private final String name;
        private final long totalCents;
        private final long commonCents;
        private final long ownItemsCents;
        private final long adjustmentsCents;
        private final int orderCount;
        private final int sharePermille;

        MemberTotal(String name, long totalCents, long commonCents, long ownItemsCents,
                    long adjustmentsCents, int orderCount, int sharePermille) {
            this.name = name;
            this.totalCents = totalCents;
            this.commonCents = commonCents;
            this.ownItemsCents = ownItemsCents;
            this.adjustmentsCents = adjustmentsCents;
            this.orderCount = orderCount;
            this.sharePermille = sharePermille;
        }

        public String name() {
            return name;
        }

        public long totalCents() {
            return totalCents;
        }

        public long commonCents() {
            return commonCents;
        }

        public long ownItemsCents() {
            return ownItemsCents;
        }

        public long adjustmentsCents() {
            return adjustmentsCents;
        }

        public int orderCount() {
            return orderCount;
        }

        /** Their share of everything spent, in parts per thousand. */
        public int sharePermille() {
            return sharePermille;
        }
    }

    /** One calendar month of spending. */
    public static final class MonthPoint {

        private final String month;
        private final long totalCents;
        private final int orderCount;

        MonthPoint(String month, long totalCents, int orderCount) {
            this.month = month;
            this.totalCents = totalCents;
            this.orderCount = orderCount;
        }

        /** {@code yyyy-MM}. */
        public String month() {
            return month;
        }

        public long totalCents() {
            return totalCents;
        }

        public int orderCount() {
            return orderCount;
        }
    }

    /** The whole picture. */
    public static final class Report {

        private final int orderCount;
        private final long totalSpentCents;
        private final long averageOrderCents;
        private final long largestOrderCents;
        private final String largestOrderLabel;
        private final long commonSpendCents;
        private final long personalSpendCents;
        private final long adjustmentsCents;
        private final List<MemberTotal> members;
        private final List<MonthPoint> months;
        private final List<String> frequentItems;

        Report(int orderCount, long totalSpentCents, long averageOrderCents,
               long largestOrderCents, String largestOrderLabel, long commonSpendCents,
               long personalSpendCents, long adjustmentsCents, List<MemberTotal> members,
               List<MonthPoint> months, List<String> frequentItems) {
            this.orderCount = orderCount;
            this.totalSpentCents = totalSpentCents;
            this.averageOrderCents = averageOrderCents;
            this.largestOrderCents = largestOrderCents;
            this.largestOrderLabel = largestOrderLabel;
            this.commonSpendCents = commonSpendCents;
            this.personalSpendCents = personalSpendCents;
            this.adjustmentsCents = adjustmentsCents;
            this.members = members;
            this.months = months;
            this.frequentItems = frequentItems;
        }

        public boolean isEmpty() {
            return orderCount == 0;
        }

        public int orderCount() {
            return orderCount;
        }

        public long totalSpentCents() {
            return totalSpentCents;
        }

        public long averageOrderCents() {
            return averageOrderCents;
        }

        public long largestOrderCents() {
            return largestOrderCents;
        }

        public String largestOrderLabel() {
            return largestOrderLabel;
        }

        /** Spending on things the whole order shared. */
        public long commonSpendCents() {
            return commonSpendCents;
        }

        /** Spending on things assigned to particular people. */
        public long personalSpendCents() {
            return personalSpendCents;
        }

        public long adjustmentsCents() {
            return adjustmentsCents;
        }

        /** Ordered by amount owed, largest first. */
        public List<MemberTotal> members() {
            return members;
        }

        /** Oldest month first, so a chart reads left to right. */
        public List<MonthPoint> months() {
            return months;
        }

        /** The item names that turn up most often, most frequent first. */
        public List<String> frequentItems() {
            return frequentItems;
        }
    }

    public static Report analyse(List<OrderPoint> orders) {
        if (orders == null || orders.isEmpty()) {
            return new Report(0, 0L, 0L, 0L, "", 0L, 0L, 0L,
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        long total = 0L;
        long largest = Long.MIN_VALUE;
        String largestLabel = "";
        long common = 0L;
        long personal = 0L;
        long adjustments = 0L;

        Map<String, long[]> memberSums = new LinkedHashMap<>();
        Map<String, Integer> memberOrders = new LinkedHashMap<>();
        Map<String, long[]> monthSums = new LinkedHashMap<>();
        Map<String, Integer> itemCounts = new LinkedHashMap<>();

        List<OrderPoint> byDate = new ArrayList<>(orders);
        byDate.sort(Comparator.comparingLong(OrderPoint::dateMillis));

        for (OrderPoint point : byDate) {
            SplitResult result = point.result();
            total += result.computedTotalCents();
            common += result.commonBucketCents();
            for (AdjustmentType type : AdjustmentType.values()) {
                adjustments += result.adjustmentTotal(type);
            }
            if (result.computedTotalCents() > largest) {
                largest = result.computedTotalCents();
                largestLabel = point.label();
            }

            String month = new SimpleDateFormat("yyyy-MM", Locale.US)
                    .format(new Date(point.dateMillis()));
            long[] monthBucket = monthSums.get(month);
            if (monthBucket == null) {
                monthBucket = new long[]{0L, 0L};
                monthSums.put(month, monthBucket);
            }
            monthBucket[0] += result.computedTotalCents();
            monthBucket[1]++;

            for (MemberSplit member : result.members()) {
                long[] sums = memberSums.get(member.memberName());
                if (sums == null) {
                    sums = new long[]{0L, 0L, 0L, 0L};
                    memberSums.put(member.memberName(), sums);
                }
                sums[0] += member.finalCents();
                sums[1] += member.commonShareCents();
                sums[2] += member.itemsTotalCents();
                sums[3] += member.adjustmentsTotalCents();
                personal += member.itemsTotalCents();

                Integer seen = memberOrders.get(member.memberName());
                memberOrders.put(member.memberName(), seen == null ? 1 : seen + 1);

                for (ItemShare share : member.itemShares()) {
                    String name = share.itemName().trim().toLowerCase(Locale.US);
                    if (name.isEmpty()) {
                        continue;
                    }
                    Integer count = itemCounts.get(name);
                    itemCounts.put(name, count == null ? 1 : count + 1);
                }
            }
        }

        List<MemberTotal> members = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : memberSums.entrySet()) {
            long[] sums = entry.getValue();
            // Integer permille, so the shares are exact and always comparable.
            int share = total == 0L ? 0 : (int) ((sums[0] * 1000L) / total);
            members.add(new MemberTotal(entry.getKey(), sums[0], sums[1], sums[2], sums[3],
                    memberOrders.get(entry.getKey()), share));
        }
        members.sort(Comparator.comparingLong(MemberTotal::totalCents).reversed()
                .thenComparing(MemberTotal::name));

        List<MonthPoint> months = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : monthSums.entrySet()) {
            months.add(new MonthPoint(entry.getKey(), entry.getValue()[0],
                    (int) entry.getValue()[1]));
        }
        months.sort(Comparator.comparing(MonthPoint::month));

        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(itemCounts.entrySet());
        ranked.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()));
        List<String> frequent = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : ranked) {
            if (entry.getValue() < 2) {
                break;
            }
            frequent.add(entry.getKey());
            if (frequent.size() == 8) {
                break;
            }
        }

        long average = total / byDate.size();
        return new Report(byDate.size(), total, average, largest, largestLabel,
                common, personal, adjustments, members, months, frequent);
    }
}
