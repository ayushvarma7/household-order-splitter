package com.householdsplitter.ui.insights;

import com.householdsplitter.core.insight.InsightTree;
import com.householdsplitter.core.insight.Trivia;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.insight.InsightService;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/** Everything the look-inside screen shows for one chosen month. */
public final class InsightData {

    /** Null when nothing has been bought yet. */
    public final InsightTree tree;
    public final YearMonth month;
    /** Months that have anything in them, newest first, for stepping back and forward. */
    public final List<YearMonth> months;
    public final Map<Long, Member> members;
    /** The orders behind the missing-money bubble, for this month only. */
    public final List<InsightService.MissingOrder> missingOrders;
    public final List<Trivia.Fact> trivia;
    /** True when this is made-up data shown so the screen can be seen before there is any. */
    public final boolean example;

    InsightData(InsightTree tree, YearMonth month, List<YearMonth> months,
                Map<Long, Member> members, List<InsightService.MissingOrder> missingOrders,
                List<Trivia.Fact> trivia, boolean example) {
        this.example = example;
        this.tree = tree;
        this.month = month;
        this.months = months;
        this.members = members;
        this.missingOrders = missingOrders;
        this.trivia = trivia;
    }

    public boolean isEmpty() {
        return tree == null || tree.root().thisCents() <= 0L;
    }
}
