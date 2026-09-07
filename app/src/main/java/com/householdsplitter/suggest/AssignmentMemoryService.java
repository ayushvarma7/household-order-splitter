package com.householdsplitter.suggest;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.suggest.NameNormalizer;
import com.householdsplitter.data.dao.AssignmentMemoryDao;
import com.householdsplitter.data.entity.AssignmentMemory;
import com.householdsplitter.util.AppExecutors;
import com.householdsplitter.util.Callback;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * SPEC 9. What this household usually does with an item.
 *
 * <p>PROMPT hard rule 3 and SPEC 7.9.13: there is no shipped list of which groceries are
 * "usually shared". Every row this reads was written by a previous confirmed assignment in
 * this same household, and a fresh install therefore suggests nothing at all.
 */
public class AssignmentMemoryService {

    /** SPEC 9.1: a prefix counts as a brand once several different products carry it. */
    private static final int BRAND_MIN_DISTINCT_USES = 3;

    private final AssignmentMemoryDao dao;
    private final AppExecutors executors;

    public AssignmentMemoryService(AssignmentMemoryDao dao, AppExecutors executors) {
        this.dao = dao;
        this.executors = executors;
    }

    /** One member's remembered stake, as the assignment screen wants it. */
    public static final class Suggestion {

        public final Scope scope;
        public final List<Long> memberIds;

        Suggestion(Scope scope, List<Long> memberIds) {
            this.scope = scope;
            this.memberIds = memberIds;
        }
    }

    /**
     * SPEC 9.3: an exact normalised match with at least one prior use. SPEC 7.9.12: the
     * result is a suggestion, shown as one, and never advances the screen by itself.
     */
    public void suggestFor(long householdId, String itemName, Callback<Suggestion> callback) {
        executors.diskIO().execute(() -> {
            String normalised = normalise(householdId, itemName);
            AssignmentMemory memory = dao.findSync(householdId, normalised);
            if (memory == null) {
                post(callback, null);
                return;
            }
            post(callback, new Suggestion(memory.scope, parseIds(memory.memberIdsCsv)));
        });
    }

    /** SPEC 9.2: upsert on confirmation, with an incremented use count. */
    public void remember(long householdId, String itemName, Scope scope, List<Long> memberIds) {
        executors.diskIO().execute(() -> {
            if (scope == Scope.UNASSIGNED || scope == Scope.EXCLUDED) {
                return;
            }
            String normalised = normalise(householdId, itemName);
            if (normalised.isEmpty()) {
                return;
            }
            AssignmentMemory existing = dao.findSync(householdId, normalised);
            if (existing == null) {
                existing = new AssignmentMemory();
                existing.householdId = householdId;
                existing.normalizedName = normalised;
                existing.scope = scope;
                existing.memberIdsCsv = joinIds(memberIds);
                existing.useCount = 1;
                existing.lastUsedAt = System.currentTimeMillis();
                try {
                    dao.insert(existing);
                } catch (RuntimeException raced) {
                    // Another write got there first; fall through to the update path.
                    AssignmentMemory reread = dao.findSync(householdId, normalised);
                    if (reread != null) {
                        applyUse(reread, scope, memberIds);
                        dao.update(reread);
                    }
                }
            } else {
                applyUse(existing, scope, memberIds);
                dao.update(existing);
            }
        });
    }

    /**
     * Suggestions for a whole list at once.
     *
     * <p>The one-at-a-time lookup is right for the assignment loop, which shows one item at
     * a time. Applying a shop's worth of remembered answers in one action needs them all,
     * and doing that as one pass over the history rather than one lookup per item matters:
     * the brand-prefix inference of SPEC 9.1 reads the household's whole history, so per
     * item it would re-read it once per row.
     *
     * @return a suggestion per index of {@code names}, with nulls where nothing is known
     */
    public void suggestForAll(long householdId, List<String> names,
                              Callback<List<Suggestion>> callback) {
        executors.diskIO().execute(() -> {
            List<String> history = new ArrayList<>();
            for (AssignmentMemory memory : dao.getAllSync(householdId)) {
                history.add(memory.normalizedName);
            }
            Set<String> brands = NameNormalizer.inferBrandPrefixes(history,
                    BRAND_MIN_DISTINCT_USES);

            List<Suggestion> suggestions = new ArrayList<>(names.size());
            for (String name : names) {
                String normalised = NameNormalizer.normalize(name, brands);
                AssignmentMemory memory = normalised.isEmpty()
                        ? null : dao.findSync(householdId, normalised);
                suggestions.add(memory == null
                        ? null : new Suggestion(memory.scope, parseIds(memory.memberIdsCsv)));
            }
            post(callback, suggestions);
        });
    }

    /** SPEC 9.5: "Clear suggestion history". */
    public void clear(long householdId, Callback<Void> callback) {
        executors.diskIO().execute(() -> {
            dao.clearForHousehold(householdId);
            post(callback, null);
        });
    }

    private static void applyUse(AssignmentMemory memory, Scope scope, List<Long> memberIds) {
        // SPEC 9.3: prefer the most recent when scopes conflict.
        memory.scope = scope;
        memory.memberIdsCsv = joinIds(memberIds);
        memory.useCount = memory.useCount + 1;
        memory.lastUsedAt = System.currentTimeMillis();
    }

    /**
     * SPEC 9.1, including the brand prefix, which is inferred from this household's own
     * history and from no shipped list.
     */
    private String normalise(long householdId, String itemName) {
        List<String> history = new ArrayList<>();
        for (AssignmentMemory memory : dao.getAllSync(householdId)) {
            history.add(memory.normalizedName);
        }
        Set<String> brands = NameNormalizer.inferBrandPrefixes(history, BRAND_MIN_DISTINCT_USES);
        return NameNormalizer.normalize(itemName, brands);
    }

    private static String joinIds(List<Long> memberIds) {
        StringBuilder out = new StringBuilder();
        if (memberIds != null) {
            for (Long id : memberIds) {
                if (out.length() > 0) {
                    out.append(',');
                }
                out.append(id);
            }
        }
        return out.toString();
    }

    private static List<Long> parseIds(String csv) {
        List<Long> ids = new ArrayList<>();
        if (csv == null || csv.trim().isEmpty()) {
            return ids;
        }
        for (String part : csv.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException skip) {
                // A malformed row is ignored rather than crashing a suggestion.
            }
        }
        return ids;
    }

    private <T> void post(Callback<T> callback, T value) {
        if (callback != null) {
            executors.mainThread().execute(() -> callback.onResult(value));
        }
    }
}
