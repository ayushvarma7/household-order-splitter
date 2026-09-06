package com.householdsplitter.core.calc.input;

import com.householdsplitter.core.calc.Scope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One row of the order. SPEC 2.5, SPEC 5.6. */
public final class CalcLineItem {

    private final long id;
    private final String name;
    private final long lineTotalCents;
    private final Scope scope;
    private final List<CalcAssignment> assignments;

    public CalcLineItem(long id, String name, long lineTotalCents, Scope scope,
                        List<CalcAssignment> assignments) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.lineTotalCents = lineTotalCents;
        this.scope = scope == null ? Scope.UNASSIGNED : scope;
        this.assignments = assignments == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(assignments));
    }

    public static CalcLineItem common(long id, String name, long cents) {
        return new CalcLineItem(id, name, cents, Scope.COMMON, Collections.emptyList());
    }

    public static CalcLineItem personal(long id, String name, long cents, long memberId) {
        return new CalcLineItem(id, name, cents, Scope.PERSONAL,
                Collections.singletonList(CalcAssignment.of(memberId)));
    }

    public static CalcLineItem subset(long id, String name, long cents, long... memberIds) {
        List<CalcAssignment> parts = new ArrayList<>(memberIds.length);
        for (long memberId : memberIds) {
            parts.add(CalcAssignment.of(memberId));
        }
        return new CalcLineItem(id, name, cents, Scope.SUBSET, parts);
    }

    public static CalcLineItem excluded(long id, String name, long cents) {
        return new CalcLineItem(id, name, cents, Scope.EXCLUDED, Collections.emptyList());
    }

    public long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public long lineTotalCents() {
        return lineTotalCents;
    }

    public Scope scope() {
        return scope;
    }

    /** Empty for COMMON items: they resolve against the participant list (SPEC 5.9). */
    public List<CalcAssignment> assignments() {
        return assignments;
    }
}
