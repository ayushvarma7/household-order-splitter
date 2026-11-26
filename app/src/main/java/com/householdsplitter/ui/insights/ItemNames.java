package com.householdsplitter.ui.insights;

import com.householdsplitter.core.insight.InsightTree.Node;
import com.householdsplitter.core.insight.ItemCategorizer;

/** Small naming rules for bubbles, kept out of the view so they can be read in one place. */
final class ItemNames {

    private ItemNames() {
    }

    /** True for the missing-money category and everything inside it. */
    static boolean isMissing(Node node) {
        return node.isMissing();
    }

    /** The name written on a bubble, where "Missing from the read" would not fit. */
    static String shortName(Node node) {
        if (node.depth() == 1 && ItemCategorizer.MISSING.equals(node.name())) {
            return "Missing";
        }
        return node.name();
    }
}
