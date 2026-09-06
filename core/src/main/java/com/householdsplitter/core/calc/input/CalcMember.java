package com.householdsplitter.core.calc.input;

/** A participant, as the calculator sees one. SPEC 2.4. */
public final class CalcMember {

    private final long id;
    private final String name;
    private final int sortOrder;

    public CalcMember(long id, String name, int sortOrder) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.sortOrder = sortOrder;
    }

    public long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int sortOrder() {
        return sortOrder;
    }
}
