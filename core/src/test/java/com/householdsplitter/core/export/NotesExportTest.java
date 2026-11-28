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
import com.householdsplitter.core.export.xlsx.Cell;
import com.householdsplitter.core.export.xlsx.Sheet;
import com.householdsplitter.core.money.CurrencyFormat;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Notes travel with the split into the chat message and the workbook. */
public class NotesExportTest {

    private static final long A = 1L;
    private static final long B = 2L;
    private static final CurrencyFormat MONEY = new CurrencyFormat("$", Locale.US);

    private static ExportOrder order() {
        List<CalcMember> members = Arrays.asList(
                new CalcMember(A, "Alpha", 0), new CalcMember(B, "Beta", 1));
        List<CalcLineItem> items = Arrays.asList(
                CalcLineItem.common(1L, "Bread", 250L),
                CalcLineItem.personal(2L, "Coffee", 899L, A));
        List<ExportOrder.NamedAmount> common = new ArrayList<>();
        common.add(new ExportOrder.NamedAmount("Bread", 250L));
        return new ExportOrder("Flat 12", "Sep 03 Costco", common,
                SplitCalculator.calculate(new CalcOrder(members, items,
                        Adjustments.taxOnly(0L), 1149L, AllocationMode.PROPORTIONAL)),
                "Alpha");
    }

    private static Map<Long, String> notes(long id, String text) {
        Map<Long, String> notes = new HashMap<>();
        notes.put(id, text);
        return notes;
    }

    @Test
    public void anOrderWithNoNotesSharesExactlyAsBefore() {
        assertEquals(ShareTextBuilder.build(order(), MONEY),
                ShareTextBuilder.build(order().withNotes("  ", notes(A, "")), MONEY));
    }

    @Test
    public void aMemberNoteSitsUnderThatMember() {
        String text = ShareTextBuilder.build(
                order().withNotes(null, notes(B, "paying Friday")), MONEY);
        String[] lines = text.split("\n");
        int beta = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("Beta: ")) {
                beta = i;
            }
        }
        assertTrue("Beta has a row", beta >= 0);
        assertEquals("  note: paying Friday", lines[lines.length - 1]);
        assertTrue("the note comes after Beta's row", lines.length - 1 > beta);
        assertFalse("Alpha has no note line", text.contains("note: \n"));
    }

    @Test
    public void aLineBreakInAMemberNoteCannotLookLikeANewRow() {
        String text = ShareTextBuilder.build(
                order().withNotes(null, notes(A, "cash\nnot card")), MONEY);
        assertTrue(text.contains("  note: cash not card\n"));
    }

    @Test
    public void theOrderNoteClosesTheMessage() {
        String text = ShareTextBuilder.build(
                order().withNotes("Ben took the extra chips", Collections.emptyMap()), MONEY);
        assertTrue(text.endsWith("\nNote: Ben took the extra chips\n"));
    }

    @Test
    public void theWorkbookCarriesBothKindsOfNote() {
        ExportOrder noted = order().withNotes("Split the deposit later", notes(B, "owes cash"));
        Sheet sheet = OrderWorkbook.build("Flat 12", Collections.singletonList(
                new OrderWorkbook.Entry(noted, 1788436800000L, "Settled"))).get(1);

        boolean orderNote = false;
        boolean noteHeader = false;
        boolean memberNote = false;
        for (List<Cell> row : sheet.rows()) {
            if (row.size() >= 2 && "Note".equals(row.get(0).text())
                    && "Split the deposit later".equals(row.get(1).text())) {
                orderNote = true;
            }
            if (row.size() == 6 && "Person".equals(row.get(0).text())
                    && "Note".equals(row.get(5).text())) {
                noteHeader = true;
            }
            if (row.size() == 6 && "Beta".equals(row.get(0).text())
                    && "owes cash".equals(row.get(5).text())) {
                memberNote = true;
            }
        }
        assertTrue("the order note is in the header block", orderNote);
        assertTrue("the person table grows a Note column", noteHeader);
        assertTrue("Beta's note is on Beta's row", memberNote);
    }

    @Test
    public void theWorkbookHasNoNoteColumnWhenNobodyWroteOne() {
        Sheet sheet = OrderWorkbook.build("Flat 12", Collections.singletonList(
                new OrderWorkbook.Entry(order(), 1788436800000L, "Settled"))).get(1);
        for (List<Cell> row : sheet.rows()) {
            assertTrue("no row is six cells wide", row.size() < 6);
        }
    }
}
