package com.householdsplitter.core.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.calc.AllocationMode;
import com.householdsplitter.core.calc.SplitCalculator;
import com.householdsplitter.core.calc.input.Adjustments;
import com.householdsplitter.core.calc.input.CalcLineItem;
import com.householdsplitter.core.calc.input.CalcMember;
import com.householdsplitter.core.calc.input.CalcOrder;
import com.householdsplitter.core.calc.result.SplitResult;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** What the breakdown screen is given. */
public class ItemisedBreakdownTest {

    private static final long A = 1L;
    private static final long B = 2L;

    /** Plain English, which on the device comes from strings.xml instead. */
    private static final ItemisedBreakdown.Labels LABELS = new ItemisedBreakdown.Labels() {
        @Override
        public String shareOfShared() {
            return "Share of shared items";
        }

        @Override
        public String ownItems() {
            return "Own items";
        }

        @Override
        public String taxAndFees() {
            return "Tax and fees";
        }

        @Override
        public String total() {
            return "Total";
        }

        @Override
        public String nothingShared() {
            return "Nothing shared";
        }

        @Override
        public String ways(int wayCount) {
            return wayCount + " ways";
        }

        @Override
        public String waysWithShares(int wayCount, int shares) {
            return wayCount + " ways, " + shares + " shares";
        }
    };

    private static SplitResult result() {
        List<CalcMember> members = Arrays.asList(
                new CalcMember(A, "Alpha", 0),
                new CalcMember(B, "Beta", 1));
        List<CalcLineItem> items = Arrays.asList(
                CalcLineItem.common(1L, "Milk", 342L),
                CalcLineItem.common(2L, "Bread", 250L),
                CalcLineItem.personal(3L, "Coffee", 899L, A),
                CalcLineItem.personal(4L, "Tea", 415L, B));
        return SplitCalculator.calculate(new CalcOrder(members, items,
                Adjustments.taxOnly(190L), 2096L, AllocationMode.PROPORTIONAL));
    }

    private static List<ItemisedBreakdown.Line> build() {
        List<ExportOrder.NamedAmount> common = new ArrayList<>();
        common.add(new ExportOrder.NamedAmount("Milk", 342L));
        common.add(new ExportOrder.NamedAmount("Bread", 250L));
        return ItemisedBreakdown.build(
                new ExportOrder("Flat 12", "Sep 03 Walmart", common, result(), "Alpha"),
                "Shared by everyone", LABELS);
    }

    /** The point of the screen: the shared pot is listed by name, once, at the top. */
    @Test
    public void theSharedItemsAreNamed() {
        List<ItemisedBreakdown.Line> lines = build();
        assertEquals(ItemisedBreakdown.Kind.SECTION, lines.get(0).kind());
        assertEquals("Shared by everyone", lines.get(0).label());
        assertEquals(Long.valueOf(592L), lines.get(0).cents());
        assertEquals("Milk", lines.get(1).label());
        assertEquals(Long.valueOf(342L), lines.get(1).cents());
        assertEquals("Bread", lines.get(2).label());
    }

    @Test
    public void everybodyGetsASectionEndingInTheirOwnTotal() {
        List<ItemisedBreakdown.Line> lines = build();
        List<String> sections = new ArrayList<>();
        for (ItemisedBreakdown.Line line : lines) {
            if (line.kind() == ItemisedBreakdown.Kind.SECTION) {
                sections.add(line.label());
            }
        }
        assertEquals(Arrays.asList("Shared by everyone", "Alpha", "Beta"), sections);

        ItemisedBreakdown.Line last = lines.get(lines.size() - 1);
        assertEquals(ItemisedBreakdown.Kind.TOTAL, last.kind());
        assertEquals(result().member(B).finalCents(), last.cents().longValue());
    }

    /** Every figure is the calculator's, not a second opinion arrived at here. */
    @Test
    public void thePerPersonFiguresAreTheCalculatorsOwn() {
        List<ItemisedBreakdown.Line> lines = build();
        long alphaShare = result().member(A).commonShareCents();
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            if ("Alpha".equals(lines.get(i).label())
                    && lines.get(i).kind() == ItemisedBreakdown.Kind.SECTION) {
                assertEquals("Share of shared items", lines.get(i + 1).label());
                assertEquals(alphaShare, lines.get(i + 1).cents().longValue());
                found = true;
            }
        }
        assertTrue("Alpha has no section", found);
    }

    @Test
    public void ownItemsAreListedUnderTheirOwner() {
        List<ItemisedBreakdown.Line> lines = build();
        assertTrue(labelsAfter("Alpha", lines).contains("Coffee"));
        assertTrue(labelsAfter("Beta", lines).contains("Tea"));
        assertTrue("Coffee is Alpha's, not Beta's",
                !labelsAfter("Beta", lines).contains("Coffee"));
    }

    /** An item split between some of the household says so, because the amount hides it. */
    @Test
    public void aSubsetItemCarriesItsWayCount() {
        List<CalcMember> members = Arrays.asList(
                new CalcMember(A, "Alpha", 0),
                new CalcMember(B, "Beta", 1));
        List<CalcLineItem> items = Arrays.asList(
                CalcLineItem.subset(1L, "Pizza", 1800L, A, B));
        SplitResult split = SplitCalculator.calculate(new CalcOrder(members, items,
                Adjustments.none(), 1800L, AllocationMode.PROPORTIONAL));

        List<ItemisedBreakdown.Line> lines = ItemisedBreakdown.build(
                new ExportOrder("Flat 12", "Pizza night",
                        new ArrayList<ExportOrder.NamedAmount>(), split, null),
                "Shared by everyone", LABELS);

        ItemisedBreakdown.Line pizza = null;
        for (ItemisedBreakdown.Line line : lines) {
            if ("Pizza".equals(line.label())) {
                pizza = line;
                break;
            }
        }
        assertNotNull("Pizza is missing from the breakdown", pizza);
        assertEquals("2 ways", pizza.note());
    }

    /** A bill where nobody shared anything says so rather than showing an empty heading. */
    @Test
    public void anEmptySharedPotSaysSo() {
        List<CalcMember> members = Arrays.asList(
                new CalcMember(A, "Alpha", 0),
                new CalcMember(B, "Beta", 1));
        List<CalcLineItem> items = Arrays.asList(
                CalcLineItem.personal(1L, "Coffee", 500L, A),
                CalcLineItem.personal(2L, "Tea", 500L, B));
        SplitResult split = SplitCalculator.calculate(new CalcOrder(members, items,
                Adjustments.none(), 1000L, AllocationMode.PROPORTIONAL));

        List<ItemisedBreakdown.Line> lines = ItemisedBreakdown.build(
                new ExportOrder("Flat 12", "Cafe", new ArrayList<ExportOrder.NamedAmount>(),
                        split, null),
                "Shared by everyone", LABELS);
        assertEquals("Nothing shared", lines.get(1).label());
    }

    @Test
    public void nothingToShowIsAnEmptyList() {
        assertTrue(ItemisedBreakdown.build(null, "Shared", LABELS).isEmpty());
    }

    private static List<String> labelsAfter(String section, List<ItemisedBreakdown.Line> lines) {
        List<String> out = new ArrayList<>();
        boolean inside = false;
        for (ItemisedBreakdown.Line line : lines) {
            if (line.kind() == ItemisedBreakdown.Kind.SECTION) {
                inside = section.equals(line.label());
                continue;
            }
            if (inside) {
                out.add(line.label());
            }
        }
        return out;
    }
}
