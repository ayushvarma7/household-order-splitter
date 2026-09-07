package com.householdsplitter.core.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.householdsplitter.core.export.xlsx.Cell;
import com.householdsplitter.core.export.xlsx.Sheet;
import com.householdsplitter.core.export.xlsx.XlsxWriter;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The workbook has to be a real xlsx, not something that merely has the extension, so these
 * tests unzip what was written and check the parts a spreadsheet actually reads.
 */
public class XlsxWriterTest {

    private static Map<String, String> unzip(byte[] bytes) throws Exception {
        Map<String, String> parts = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = zip.read(buffer)) > 0) {
                    out.write(buffer, 0, count);
                }
                parts.put(entry.getName(), out.toString("UTF-8"));
            }
        }
        return parts;
    }

    private static byte[] sample() throws Exception {
        Sheet one = new Sheet("All orders").widths(30, 12);
        one.row(Cell.header("Order"), Cell.header("Total"));
        one.row(Cell.text("Sep 03 Walmart"), Cell.money(5402L));
        one.row(Cell.label("Total"), Cell.moneyTotal(5402L));
        Sheet two = new Sheet("Sep 03 Walmart");
        two.row(Cell.text("Milk, 1 gal"), Cell.money(342L));
        return XlsxWriter.toBytes(Arrays.asList(one, two));
    }

    @Test
    public void writesEveryPartASpreadsheetNeeds() throws Exception {
        Map<String, String> parts = unzip(sample());
        assertNotNull(parts.get("[Content_Types].xml"));
        assertNotNull(parts.get("_rels/.rels"));
        assertNotNull(parts.get("xl/workbook.xml"));
        assertNotNull(parts.get("xl/_rels/workbook.xml.rels"));
        assertNotNull(parts.get("xl/styles.xml"));
        assertNotNull(parts.get("xl/worksheets/sheet1.xml"));
        assertNotNull(parts.get("xl/worksheets/sheet2.xml"));
    }

    /** One sheet per order is the whole point of the feature. */
    @Test
    public void oneSheetPerOrderIsDeclaredAndRelated() throws Exception {
        Map<String, String> parts = unzip(sample());
        String workbook = parts.get("xl/workbook.xml");
        assertTrue(workbook.contains("name=\"All orders\""));
        assertTrue(workbook.contains("name=\"Sep 03 Walmart\""));

        String rels = parts.get("xl/_rels/workbook.xml.rels");
        assertTrue(rels.contains("worksheets/sheet1.xml"));
        assertTrue(rels.contains("worksheets/sheet2.xml"));
        assertTrue(rels.contains("styles.xml"));
    }

    /** Money must be a number, so a column of amounts can be summed in the spreadsheet. */
    @Test
    public void moneyIsANumberNotText() throws Exception {
        String sheet = unzip(sample()).get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("<v>54.02</v>"));
        assertFalse("an amount written as text cannot be summed",
                sheet.contains("<t xml:space=\"preserve\">54.02</t>"));
    }

    @Test
    public void textIsEscaped() throws Exception {
        Sheet sheet = new Sheet("Escaping");
        sheet.row(Cell.text("Ben & Jerry's <\"half\">"));
        String xml = unzip(XlsxWriter.toBytes(Arrays.asList(sheet)))
                .get("xl/worksheets/sheet1.xml");
        assertTrue(xml.contains("Ben &amp; Jerry&apos;s &lt;&quot;half&quot;&gt;"));
    }

    /** Excel rejects the whole file for an illegal or over-long sheet name. */
    @Test
    public void sheetNamesAreMadeLegal() {
        assertEquals("Sep 03 Walmart", Sheet.sanitise("Sep 03 Walmart"));
        assertEquals("Sep 03 Walmart", Sheet.sanitise("Sep/03:Walmart"));
        // Truncated to the 31-character limit, then trimmed, so it can come back shorter.
        String truncated = Sheet.sanitise("A really very long order label that will not fit");
        assertTrue(truncated.length() <= 31);
        assertTrue(truncated.startsWith("A really very long order label"));
        assertEquals("Sheet", Sheet.sanitise("   "));
        assertEquals("Sheet", Sheet.sanitise(null));
    }

    /** Two orders can share a label, and Excel will not accept two identical sheet names. */
    @Test
    public void duplicateSheetNamesAreDisambiguated() throws Exception {
        List<Sheet> sheets = new ArrayList<>();
        sheets.add(new Sheet("Sep 03 Walmart").row(Cell.text("a")));
        sheets.add(new Sheet("Sep 03 Walmart").row(Cell.text("b")));
        sheets.add(new Sheet("Sep 03 Walmart").row(Cell.text("c")));

        String workbook = unzip(XlsxWriter.toBytes(sheets)).get("xl/workbook.xml");
        assertTrue(workbook.contains("name=\"Sep 03 Walmart\""));
        assertTrue(workbook.contains("name=\"Sep 03 Walmart (2)\""));
        assertTrue(workbook.contains("name=\"Sep 03 Walmart (3)\""));
    }

    @Test
    public void columnLettersRunPastZ() {
        assertEquals("A", callColumn(0));
        assertEquals("Z", callColumn(25));
        assertEquals("AA", callColumn(26));
        assertEquals("AB", callColumn(27));
        assertEquals("BA", callColumn(52));
    }

    /** Identical data produces an identical file, so "has anything changed?" is answerable. */
    @Test
    public void outputIsDeterministic() throws Exception {
        assertTrue(java.util.Arrays.equals(sample(), sample()));
    }

    private static String callColumn(int index) {
        try {
            java.lang.reflect.Method method =
                    XlsxWriter.class.getDeclaredMethod("column", int.class);
            method.setAccessible(true);
            return (String) method.invoke(null, index);
        } catch (Exception failed) {
            throw new AssertionError(failed);
        }
    }
}
