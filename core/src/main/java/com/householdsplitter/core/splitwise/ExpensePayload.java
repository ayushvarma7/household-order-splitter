package com.householdsplitter.core.splitwise;

import java.util.LinkedHashMap;
import java.util.Map;

/** A finished create or update request body, ready to send. */
public final class ExpensePayload {

    private final Map<String, String> form;

    ExpensePayload(Map<String, String> form) {
        this.form = new LinkedHashMap<>(form);
    }

    public Map<String, String> form() {
        return new LinkedHashMap<>(form);
    }
}
