package com.householdsplitter.core.splitwise;

import java.util.Collections;
import java.util.List;

/** A Splitwise group and the people in it. */
public final class SplitwiseGroup {

    private final long id;
    private final String name;
    private final List<SplitwiseUser> members;

    public SplitwiseGroup(long id, String name, List<SplitwiseUser> members) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.members = Collections.unmodifiableList(members);
    }

    public long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public List<SplitwiseUser> members() {
        return members;
    }
}
