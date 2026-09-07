package com.householdsplitter.core.export.xlsx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One worksheet: a name, some column widths, and rows of cells. */
public final class Sheet {

    /** Excel refuses these in a sheet name, and caps the name at 31 characters. */
    private static final String ILLEGAL = "[]:*?/\\";
    private static final int NAME_LIMIT = 31;

    private final String name;
    private final List<List<Cell>> rows = new ArrayList<>();
    private final List<Integer> columnWidths = new ArrayList<>();

    public Sheet(String name) {
        this.name = sanitise(name);
    }

    /**
     * Excel rejects a workbook outright if a sheet name is too long or holds a reserved
     * character, so the name is cleaned here rather than trusted from an order label the
     * user typed.
     */
    public static String sanitise(String candidate) {
        String value = candidate == null ? "" : candidate.trim();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            out.append(ILLEGAL.indexOf(c) >= 0 ? ' ' : c);
        }
        String cleaned = out.toString().replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) {
            cleaned = "Sheet";
        }
        if (cleaned.length() > NAME_LIMIT) {
            cleaned = cleaned.substring(0, NAME_LIMIT).trim();
        }
        // A leading or trailing apostrophe is also rejected.
        while (cleaned.startsWith("'")) {
            cleaned = cleaned.substring(1);
        }
        while (cleaned.endsWith("'")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned.isEmpty() ? "Sheet" : cleaned;
    }

    public String name() {
        return name;
    }

    public Sheet widths(int... widths) {
        columnWidths.clear();
        for (int width : widths) {
            columnWidths.add(width);
        }
        return this;
    }

    public Sheet row(Cell... cells) {
        List<Cell> line = new ArrayList<>(cells.length);
        Collections.addAll(line, cells);
        rows.add(line);
        return this;
    }

    public Sheet blank() {
        rows.add(new ArrayList<>());
        return this;
    }

    public List<List<Cell>> rows() {
        return rows;
    }

    public List<Integer> columnWidths() {
        return columnWidths;
    }
}
