package com.householdsplitter.suggest;

import androidx.lifecycle.LiveData;

import com.householdsplitter.core.suggest.StandingRules;
import com.householdsplitter.data.dao.MemberRuleDao;
import com.householdsplitter.data.entity.MemberRule;
import com.householdsplitter.util.AppExecutors;
import com.householdsplitter.util.Callback;
import com.householdsplitter.util.Result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The household's standing rules, and applying them.
 *
 * <p>The matching itself lives in {@link StandingRules} in :core, where it is tested without
 * a device. This class is the part that needs Room: loading the rules, keeping a snapshot the
 * assign screen can consult without a round trip per item, and validating what the user types.
 *
 * <p>Nothing is seeded. SPEC 7.9.13 and PROMPT hard rule 3 forbid the app shipping opinions
 * about which groceries are shared, so every rule here was written by this household.
 */
public class RuleService {

    /** A keyword long enough to mean something and short enough to read in a list. */
    public static final int MAX_KEYWORD = 40;

    private final MemberRuleDao dao;
    private final AppExecutors executors;

    /**
     * The rules as last loaded. Held so {@link #apply} can stay synchronous: the assign
     * screen applies rules while prefilling each item, and a database read per item would
     * make the core loop stutter for no reason. A household has a handful of rules.
     */
    private volatile List<MemberRule> snapshot = Collections.emptyList();

    public RuleService(MemberRuleDao dao, AppExecutors executors) {
        this.dao = dao;
        this.executors = executors;
    }

    public LiveData<List<MemberRule>> observe(long householdId) {
        return dao.observeForHousehold(householdId);
    }

    /** Reloads the snapshot. The callback, if given, runs on the main thread. */
    public void refresh(long householdId, Callback<List<MemberRule>> callback) {
        executors.diskIO().execute(() -> {
            List<MemberRule> loaded = dao.getForHouseholdSync(householdId);
            snapshot = loaded == null ? Collections.emptyList() : loaded;
            if (callback != null) {
                List<MemberRule> result = snapshot;
                executors.mainThread().execute(() -> callback.onResult(result));
            }
        });
    }

    public List<MemberRule> snapshot() {
        return snapshot;
    }

    public boolean hasRules() {
        return !snapshot.isEmpty();
    }

    /**
     * Applies the loaded rules to a proposed set of members.
     *
     * <p>Callers show {@link StandingRules.Outcome#removed()} and
     * {@link StandingRules.Outcome#added()} to the user. A rule quietly changing who pays for
     * something would be worse than having no rules, so applying one is always visible and
     * always overridable.
     */
    public StandingRules.Outcome apply(String itemName, List<Long> proposed,
                                       List<Long> participants) {
        List<StandingRules.Rule> rules = new ArrayList<>();
        for (MemberRule stored : snapshot) {
            rules.add(new StandingRules.Rule(stored.memberId, stored.keyword,
                    stored.kind == MemberRule.Kind.INCLUDE
                            ? StandingRules.Kind.INCLUDE
                            : StandingRules.Kind.EXCLUDE));
        }
        return StandingRules.apply(itemName, proposed, participants, rules);
    }

    /**
     * The first rule of the given member that fires on this item, so a screen can name the
     * keyword rather than say "a rule" and leave the user guessing which one.
     */
    public MemberRule firstMatch(long memberId, String itemName, MemberRule.Kind kind) {
        for (MemberRule rule : snapshot) {
            if (rule.memberId == memberId && rule.kind == kind && rule.matches(itemName)) {
                return rule;
            }
        }
        return null;
    }

    public void add(long householdId, long memberId, String keyword, MemberRule.Kind kind,
                    Callback<Result<Long>> callback) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            post(callback, Result.failure("Enter a word to look for in the item name"));
            return;
        }
        if (trimmed.length() > MAX_KEYWORD) {
            post(callback, Result.failure("Keep the word under " + MAX_KEYWORD + " characters"));
            return;
        }
        if (memberId <= 0) {
            post(callback, Result.failure("Pick who the rule is about"));
            return;
        }
        executors.diskIO().execute(() -> {
            // A duplicate would fire twice and read as though the app had misunderstood.
            for (MemberRule existing : dao.getForHouseholdSync(householdId)) {
                if (existing.memberId == memberId && existing.kind == kind
                        && existing.keyword.trim().toLowerCase(Locale.ROOT)
                        .equals(trimmed.toLowerCase(Locale.ROOT))) {
                    post(callback, Result.failure("That rule is already here"));
                    return;
                }
            }
            long id = dao.insert(new MemberRule(householdId, memberId, trimmed, kind));
            snapshot = dao.getForHouseholdSync(householdId);
            post(callback, Result.ok(id));
        });
    }

    public void delete(long householdId, long ruleId, Callback<Result<Void>> callback) {
        executors.diskIO().execute(() -> {
            dao.deleteById(ruleId);
            snapshot = dao.getForHouseholdSync(householdId);
            post(callback, Result.<Void>ok(null));
        });
    }

    private <T> void post(Callback<Result<T>> callback, Result<T> result) {
        if (callback == null) {
            return;
        }
        executors.mainThread().execute(() -> callback.onResult(result));
    }
}
