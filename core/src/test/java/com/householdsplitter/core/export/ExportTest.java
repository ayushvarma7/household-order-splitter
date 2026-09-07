package com.householdsplitter.core.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.calc.AllocationMode;
import com.householdsplitter.core.calc.SplitCalculator;
import com.householdsplitter.core.calc.input.Adjustments;
import com.householdsplitter.core.calc.input.CalcLineItem;
import com.householdsplitter.core.calc.input.CalcMember;
import com.householdsplitter.core.calc.input.CalcOrder;
import com.householdsplitter.core.calc.result.SplitResult;
import com.householdsplitter.core.money.CurrencyFormat;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** SPEC 10.1 and 10.2. */
public class ExportTest {

    private static final long A = 1L;
    private static final long B = 2L;

    private static SplitResult twoMemberResult() {
        List<CalcMember> members = Arrays.asList(
                new CalcMember(A, "Alpha", 0),
                new CalcMember(B, "Beta", 1));
        List<CalcLineItem> items = Arrays.asList(
                CalcLineItem.common(1L, "Milk, 1 gal", 342L),
                CalcLineItem.common(2L, "Bread", 250L),
                CalcLineItem.personal(3L, "Coffee", 899L, A),
                CalcLineItem.personal(4L, "Tea", 415L, B));
        return SplitCalculator.calculate(new CalcOrder(members, items,
                Adjustments.taxOnly(190L), 2096L, AllocationMode.PROPORTIONAL));
    }

    private static ExportOrder exportOrder(String payer) {
        List<ExportOrder.NamedAmount> common = new ArrayList<>();
        common.add(new ExportOrder.NamedAmount("Milk, 1 gal", 342L));
        common.add(new ExportOrder.NamedAmount("Bread", 250L));
        return new ExportOrder("Flat 12", "Sep 03 Walmart", common, twoMemberResult(), payer);
    }

    /** SPEC 10.1: member columns are generated, and land where the spec's layout puts them. */
    @Test
    public void csvHeaderPlacesMemberColumns() {
        String[] lines = CsvExporter.export(exportOrder(null)).split("\n");
        assertEquals("Common,Cost,,,Alpha,,,Beta", lines[0]);
        assertEquals("Item,Cost,,,Item,Cost,,Item,Cost", lines[1]);
    }

    /** SPEC 10.1.2: RFC 4180 quoting of a comma inside an item name. */
    @Test
    public void csvQuotesCommasInNames() {
        String csv = CsvExporter.export(exportOrder(null));
        assertTrue(csv.contains("\"Milk, 1 gal\""));
    }

    /** SPEC 10.1.1: plain two-place decimals, no currency symbol. */
    @Test
    public void csvAmountsAreBareDecimals() {
        String csv = CsvExporter.export(exportOrder(null));
        assertFalse(csv.contains("$"));
        assertTrue(csv.contains("3.42"));
        assertTrue(csv.contains("Total Bill,20.96,Matches bill amount"));
    }

    @Test
    public void csvCarriesTheCommonBlockTotals() {
        String csv = CsvExporter.export(exportOrder(null));
        assertTrue(csv.contains("Total,5.92"));
        assertTrue(csv.contains("Split between 2,2.96"));
        assertTrue(csv.contains("Tax,1.90"));
    }

    @Test
    public void csvFooterCarriesEachMembersTotals() {
        String[] lines = CsvExporter.export(exportOrder(null)).split("\n");
        String preTax = find(lines, ",,,,Total,");
        String taxShare = find(lines, ",,,,Tax share,");
        String withTax = find(lines, ",,,,Total with tax,");
        assertTrue(preTax, preTax.startsWith(",,,,Total,"));
        assertTrue(taxShare, taxShare.contains("Tax share"));
        assertTrue(withTax, withTax.contains("Total with tax"));
    }

    /** A mismatch is spelled out rather than hidden (SPEC 6.4.12). */
    @Test
    public void csvVerdictReportsAMismatch() {
        List<CalcMember> members = Arrays.asList(new CalcMember(A, "Alpha", 0));
        SplitResult result = SplitCalculator.calculate(new CalcOrder(members,
                Arrays.asList(CalcLineItem.common(1L, "Milk", 500L)),
                Adjustments.none(), 550L, AllocationMode.PROPORTIONAL));
        ExportOrder order = new ExportOrder("Flat 12", "Sep 03 Walmart",
                Arrays.asList(new ExportOrder.NamedAmount("Milk", 500L)), result, null);

        assertTrue(CsvExporter.export(order).contains("Total Bill,5.00,Off by -0.50"));
    }

    /** SPEC 7.11.3: one file, every order, separated by a blank line and a header. */
    @Test
    public void exportAllSeparatesOrders() {
        String csv = CsvExporter.exportAll(Arrays.asList(exportOrder(null), exportOrder(null)));
        assertTrue(csv.contains("Flat 12 - Sep 03 Walmart"));
        assertTrue(csv.contains("\n\n"));
    }

    /** SPEC 10.2. */
    @Test
    public void shareTextFollowsTheTemplate() {
        String text = ShareTextBuilder.build(exportOrder("Alpha"), new CurrencyFormat("$", Locale.US));
        String[] lines = text.split("\n");

        assertEquals("Flat 12" + ShareTextBuilder.TITLE_SEPARATOR + "Sep 03 Walmart", lines[0]);
        assertEquals("Total: $20.96  (paid by Alpha)", lines[1]);
        assertEquals("", lines[2]);
        assertTrue(lines[3].startsWith("Alpha: $"));
        assertTrue(lines[4].trim().startsWith("common $"));
        assertTrue(lines[4].contains("items $"));
        assertTrue(lines[4].contains("tax & fees $"));
    }

    /** SPEC 10.2.1: nothing that renders as markup in a chat app. */
    @Test
    public void shareTextIsPlain() {
        String text = ShareTextBuilder.build(exportOrder("Alpha"), new CurrencyFormat("$", Locale.US));
        assertFalse(text.contains("*"));
        assertFalse(text.contains("|"));
        assertFalse(text.contains("#"));
        assertFalse(text.contains("`"));
    }

    /** SPEC 10.2.2: a large household drops the detail line rather than overflowing. */
    @Test
    public void shareTextDropsDetailForALargeHousehold() {
        List<CalcMember> members = new ArrayList<>();
        List<CalcLineItem> items = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            members.add(new CalcMember(i + 1L, "Housemate number " + (i + 1), i));
            items.add(CalcLineItem.personal(100L + i, "Item " + i, 500L + i, i + 1L));
        }
        SplitResult result = SplitCalculator.calculate(new CalcOrder(members, items,
                Adjustments.none(), 0L, AllocationMode.PROPORTIONAL));
        ExportOrder order = new ExportOrder("Big House", "Sep 03 Walmart",
                new ArrayList<ExportOrder.NamedAmount>(), result, null);

        String text = ShareTextBuilder.build(order, new CurrencyFormat("$", Locale.US));
        assertFalse("detail lines must be dropped", text.contains("common $"));
    }

    /** SPEC 7.10.4. */
    @Test
    public void owesLineReadsCorrectlyForPayerAndOthers() {
        SplitResult result = twoMemberResult();
        CurrencyFormat money = new CurrencyFormat("$", Locale.US);
        assertTrue(ShareTextBuilder.owesLine(result.member(A), "Alpha", true, money)
                .startsWith("paid $"));
        assertTrue(ShareTextBuilder.owesLine(result.member(B), "Alpha", false, money)
                .startsWith("Beta owes Alpha $"));
    }

    private static String find(String[] lines, String prefix) {
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                return line;
            }
        }
        return "";
    }
}
