package com.householdsplitter.core.export.xlsx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a real {@code .xlsx} workbook, by hand.
 *
 * <p>An xlsx file is a zip of XML parts, and {@code java.util.zip} is in the JDK, so this
 * needs no spreadsheet library and stays inside the dependency budget of SPEC 4.11. Strings
 * are written inline rather than through a shared-strings table, which costs a little file
 * size and removes a whole class of index bugs.
 *
 * <p>Money is written as a number with a currency format, never as text, so a column of
 * amounts can be summed in the spreadsheet. The number is produced from {@code long} cents
 * by exact string construction, so the no-floating-point rule holds all the way out to the
 * file.
 */
public final class XlsxWriter {

    private static final String CONTENT_TYPES = "http://schemas.openxmlformats.org/package/2006/content-types";
    private static final String PACKAGE_RELS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String DOC_RELS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

    private XlsxWriter() {
    }

    public static byte[] toBytes(List<Sheet> sheets) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        write(sheets, buffer);
        return buffer.toByteArray();
    }

    public static void write(List<Sheet> sheets, OutputStream target) throws IOException {
        if (sheets == null || sheets.isEmpty()) {
            throw new IOException("A workbook needs at least one sheet");
        }
        ZipOutputStream zip = new ZipOutputStream(target);
        try {
            put(zip, "[Content_Types].xml", contentTypes(sheets.size()));
            put(zip, "_rels/.rels", packageRels());
            put(zip, "xl/workbook.xml", workbook(sheets));
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size()));
            put(zip, "xl/styles.xml", styles());
            for (int i = 0; i < sheets.size(); i++) {
                put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheet(sheets.get(i)));
            }
        } finally {
            zip.finish();
            zip.flush();
        }
    }

    private static void put(ZipOutputStream zip, String path, String content) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        // A fixed timestamp keeps the file byte-identical for identical data, which makes
        // "did anything actually change?" answerable.
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypes(int sheetCount) {
        StringBuilder out = new StringBuilder();
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<Types xmlns=\"").append(CONTENT_TYPES).append("\">")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
                .append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 1; i <= sheetCount; i++) {
            out.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return out.append("</Types>").toString();
    }

    private static String packageRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"" + PACKAGE_RELS + "\">"
                + "<Relationship Id=\"rId1\" Type=\"" + DOC_RELS + "/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private static String workbook(List<Sheet> sheets) {
        StringBuilder out = new StringBuilder();
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<workbook xmlns=\"").append(MAIN)
                .append("\" xmlns:r=\"").append(DOC_RELS).append("\"><sheets>");
        for (int i = 0; i < sheets.size(); i++) {
            out.append("<sheet name=\"").append(escape(uniqueName(sheets, i)))
                    .append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return out.append("</sheets></workbook>").toString();
    }

    /** Excel rejects a workbook with two sheets of the same name, so collisions get a suffix. */
    private static String uniqueName(List<Sheet> sheets, int index) {
        String name = sheets.get(index).name();
        int duplicates = 0;
        for (int i = 0; i < index; i++) {
            if (sheets.get(i).name().equalsIgnoreCase(name)) {
                duplicates++;
            }
        }
        if (duplicates == 0) {
            return name;
        }
        String suffix = " (" + (duplicates + 1) + ")";
        String trimmed = name.length() + suffix.length() > 31
                ? name.substring(0, 31 - suffix.length()) : name;
        return trimmed + suffix;
    }

    private static String workbookRels(int sheetCount) {
        StringBuilder out = new StringBuilder();
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<Relationships xmlns=\"").append(PACKAGE_RELS).append("\">");
        for (int i = 1; i <= sheetCount; i++) {
            out.append("<Relationship Id=\"rId").append(i).append("\" Type=\"").append(DOC_RELS)
                    .append("/worksheet\" Target=\"worksheets/sheet").append(i).append(".xml\"/>");
        }
        out.append("<Relationship Id=\"rId").append(sheetCount + 1).append("\" Type=\"")
                .append(DOC_RELS).append("/styles\" Target=\"styles.xml\"/>");
        return out.append("</Relationships>").toString();
    }

    /**
     * Style indices used by {@link #styleFor}:
     * 0 plain, 1 header, 2 money, 3 money total, 4 label, 5 number.
     */
    private static String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"" + MAIN + "\">"
                + "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"#,##0.00\"/></numFmts>"
                + "<fonts count=\"2\">"
                + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "</fonts>"
                + "<fills count=\"3\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\">"
                + "<fgColor rgb=\"FFDDE1FF\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "</fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"6\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
                + "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
                + "<xf numFmtId=\"164\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }

    private static int styleFor(Cell.Kind kind) {
        switch (kind) {
            case HEADER:
                return 1;
            case MONEY:
                return 2;
            case MONEY_TOTAL:
                return 3;
            case LABEL:
                return 4;
            case NUMBER:
                return 5;
            default:
                return 0;
        }
    }

    private static String sheet(Sheet sheet) {
        StringBuilder out = new StringBuilder();
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"").append(MAIN).append("\">");

        if (!sheet.columnWidths().isEmpty()) {
            out.append("<cols>");
            for (int i = 0; i < sheet.columnWidths().size(); i++) {
                out.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                        .append("\" width=\"").append(sheet.columnWidths().get(i))
                        .append("\" customWidth=\"1\"/>");
            }
            out.append("</cols>");
        }

        out.append("<sheetData>");
        List<List<Cell>> rows = sheet.rows();
        for (int r = 0; r < rows.size(); r++) {
            List<Cell> cells = rows.get(r);
            out.append("<row r=\"").append(r + 1).append("\">");
            for (int c = 0; c < cells.size(); c++) {
                Cell cell = cells.get(c);
                if (cell == null || cell.isBlank()) {
                    continue;
                }
                String reference = column(c) + (r + 1);
                out.append("<c r=\"").append(reference).append("\" s=\"")
                        .append(styleFor(cell.kind())).append('"');
                if (cell.isNumeric()) {
                    out.append("><v>").append(cell.numeric()).append("</v></c>");
                } else {
                    out.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(escape(cell.text())).append("</t></is></c>");
                }
            }
            out.append("</row>");
        }
        return out.append("</sheetData></worksheet>").toString();
    }

    /** 0 to A, 25 to Z, 26 to AA, and so on. */
    static String column(int index) {
        StringBuilder out = new StringBuilder();
        int value = index;
        while (value >= 0) {
            out.insert(0, (char) ('A' + value % 26));
            value = value / 26 - 1;
        }
        return out.toString();
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                case '\'':
                    out.append("&apos;");
                    break;
                default:
                    // XML 1.0 forbids most control characters outright.
                    if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                        out.append(' ');
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }
}
