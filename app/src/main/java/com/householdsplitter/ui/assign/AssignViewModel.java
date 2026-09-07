package com.householdsplitter.ui.assign;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.money.MoneySplitter;
import com.householdsplitter.data.entity.DraftStep;
import com.householdsplitter.data.entity.ItemAssignment;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.relation.LineItemWithAssignments;
import com.householdsplitter.data.relation.OrderBundle;
import com.householdsplitter.data.repo.OrderRepository;
import com.householdsplitter.core.suggest.StandingRules;
import com.householdsplitter.suggest.AssignmentMemoryService;
import com.householdsplitter.suggest.RuleService;
import com.householdsplitter.data.entity.MemberRule;
import com.householdsplitter.util.Callback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S9, the core loop. SPEC 7.9.
 *
 * <p>One item at a time, answering "who is this for?". SPEC 1.4 says eliminating the manual
 * duplication of a subset-shared item is the core value of the product, and this is the
 * screen that does it.
 */
public class AssignViewModel extends ViewModel {

    private final OrderRepository repository;
    private final AssignmentMemoryService memory;
    private final RuleService rules;
    private final long orderId;
    private final long householdId;
    private final LiveData<OrderBundle> bundle;

    private final MutableLiveData<Integer> index = new MutableLiveData<>(0);
    /** memberId to share count. Order preserved so chips render predictably. */
    private final MutableLiveData<Map<Long, Integer>> selection =
            new MutableLiveData<>(new LinkedHashMap<>());
    private final MutableLiveData<Boolean> suggested = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> convertedToCommon = new MutableLiveData<>(false);
    /**
     * What a standing rule just did, in words, or null. A rule quietly changing who pays
     * for something would be worse than having no rules, so every application says so.
     */
    private final MutableLiveData<String> ruleNote = new MutableLiveData<>(null);

    private long loadedForItemId = -1L;

    public AssignViewModel(OrderRepository repository, AssignmentMemoryService memory,
                           RuleService rules, long orderId, long householdId) {
        this.repository = repository;
        this.memory = memory;
        this.rules = rules;
        this.orderId = orderId;
        this.householdId = householdId;
        this.bundle = repository.observeBundle(orderId);
        // Loaded once, up front, so applying rules while prefilling each item costs nothing.
        rules.refresh(householdId, loaded -> {
            if (loaded != null && !loaded.isEmpty()) {
                reapplyRulesToUntouchedSelection();
            }
        });
    }

    public LiveData<String> ruleNote() {
        return ruleNote;
    }

    public LiveData<OrderBundle> bundle() {
        return bundle;
    }

    public LiveData<Integer> index() {
        return index;
    }

    public LiveData<Map<Long, Integer>> selection() {
        return selection;
    }

    /** SPEC 7.9.12: shown as "suggested from your last order", and overridable. */
    public LiveData<Boolean> suggested() {
        return suggested;
    }

    /** SPEC 7.9.4: selecting everyone converts to COMMON, and the screen says so. */
    public LiveData<Boolean> convertedToCommon() {
        return convertedToCommon;
    }

    public List<LineItemWithAssignments> items() {
        OrderBundle value = bundle.getValue();
        return value == null ? new ArrayList<>() : value.items;
    }

    public List<Member> participants() {
        OrderBundle value = bundle.getValue();
        return value == null ? new ArrayList<>() : value.participants;
    }

    public LineItemWithAssignments currentItem() {
        List<LineItemWithAssignments> all = items();
        Integer at = index.getValue();
        if (all.isEmpty() || at == null || at < 0 || at >= all.size()) {
            return null;
        }
        return all.get(at);
    }

    /** SPEC 7.9.14: the draft resumes on the item it stopped at. */
    public void restorePosition(OrderBundle current) {
        if (current == null || loadedForItemId != -1L) {
            return;
        }
        int position = Math.max(0, Math.min(current.order.draftItemPosition,
                Math.max(0, current.items.size() - 1)));
        index.setValue(position);
        loadedForItemId = -2L;
        loadSelectionForCurrent();
    }

    /**
     * Loads the item's saved assignment, or, when it has none, a suggestion from this
     * household's own history (SPEC 7.9.12). A suggestion never advances the screen.
     */
    public void loadSelectionForCurrent() {
        LineItemWithAssignments item = currentItem();
        if (item == null) {
            return;
        }
        loadedForItemId = item.item.id;
        convertedToCommon.setValue(false);

        Map<Long, Integer> next = new LinkedHashMap<>();
        if (item.item.scope == Scope.COMMON) {
            for (Member member : participants()) {
                next.put(member.id, 1);
            }
            suggested.setValue(false);
            selection.setValue(next);
            return;
        }
        if (item.hasAssignments()) {
            for (ItemAssignment assignment : item.assignments) {
                next.put(assignment.memberId, assignment.shares);
            }
            suggested.setValue(false);
            selection.setValue(next);
            return;
        }
        if (item.item.scope == Scope.EXCLUDED) {
            suggested.setValue(false);
            selection.setValue(next);
            return;
        }

        selection.setValue(applyRules(next, item.item.name));
        suggested.setValue(false);
        long itemId = item.item.id;
        memory.suggestFor(householdId, item.item.name, suggestion -> {
            LineItemWithAssignments still = currentItem();
            if (suggestion == null || still == null || still.item.id != itemId) {
                return;
            }
            Map<Long, Integer> prefill = new LinkedHashMap<>();
            if (suggestion.scope == Scope.COMMON) {
                for (Member member : participants()) {
                    prefill.put(member.id, 1);
                }
            } else {
                for (Long memberId : suggestion.memberIds) {
                    if (isParticipant(memberId)) {
                        prefill.put(memberId, 1);
                    }
                }
            }
            if (!prefill.isEmpty()) {
                selection.setValue(applyRules(prefill, still.item.name));
                suggested.setValue(true);
            }
        });
    }

    private boolean isParticipant(long memberId) {
        for (Member member : participants()) {
            if (member.id == memberId) {
                return true;
            }
        }
        return false;
    }

    public void toggle(long memberId) {
        ruleNote.setValue(null);
        Map<Long, Integer> next = new LinkedHashMap<>(currentSelection());
        if (next.containsKey(memberId)) {
            next.remove(memberId);
        } else {
            next.put(memberId, 1);
        }
        suggested.setValue(false);
        selection.setValue(next);
        convertedToCommon.setValue(next.size() == participants().size() && !next.isEmpty());
    }

    /** SPEC 7.9.6: long-press a selected chip for a double share, then back to one. */
    public void cycleShares(long memberId) {
        Map<Long, Integer> next = new LinkedHashMap<>(currentSelection());
        Integer shares = next.get(memberId);
        if (shares == null) {
            return;
        }
        next.put(memberId, shares >= 2 ? 1 : shares + 1);
        suggested.setValue(false);
        selection.setValue(next);
    }

    /**
     * SPEC 7.9.2's "Everyone". This is where standing rules earn their keep: on a household
     * where one person is never on beer, tapping Everyone on a beer item should not put them
     * back on it every single order.
     */
    public void selectEveryone() {
        Map<Long, Integer> next = new LinkedHashMap<>();
        for (Member member : participants()) {
            next.put(member.id, 1);
        }
        suggested.setValue(false);
        LineItemWithAssignments item = currentItem();
        selection.setValue(applyRules(next, item == null ? null : item.item.name));
    }

    public void selectOnly(long memberId) {
        Map<Long, Integer> next = new LinkedHashMap<>();
        next.put(memberId, 1);
        suggested.setValue(false);
        selection.setValue(next);
    }

    /** SPEC 7.9.3: "Same as previous". Hidden on item 1 by the fragment. */
    public void copyPrevious() {
        Integer at = index.getValue();
        if (at == null || at <= 0) {
            return;
        }
        LineItemWithAssignments previous = items().get(at - 1);
        Map<Long, Integer> next = new LinkedHashMap<>();
        if (previous.item.scope == Scope.COMMON) {
            for (Member member : participants()) {
                next.put(member.id, 1);
            }
        } else {
            for (ItemAssignment assignment : previous.assignments) {
                next.put(assignment.memberId, assignment.shares);
            }
        }
        suggested.setValue(false);
        LineItemWithAssignments current = currentItem();
        selection.setValue(applyRules(next, current == null ? null : current.item.name));
    }

    /**
     * Runs the household's standing rules over a proposed selection.
     *
     * <p>Share counts are preserved for anybody the rules leave alone, so a rule never
     * silently turns a double share into a single one. Somebody a rule adds gets one share,
     * which is the only defensible default.
     *
     * <p>Not applied to an answer the user has already saved for this item, and not applied
     * when they are toggling chips by hand: a rule is a starting point, not a veto.
     */
    private Map<Long, Integer> applyRules(Map<Long, Integer> proposed, String itemName) {
        ruleNote.setValue(null);
        if (itemName == null || !rules.hasRules()) {
            return proposed;
        }
        List<Long> participantIds = new ArrayList<>();
        for (Member member : participants()) {
            participantIds.add(member.id);
        }
        StandingRules.Outcome outcome = rules.apply(
                itemName, new ArrayList<>(proposed.keySet()), participantIds);
        if (!outcome.changedAnything()) {
            return proposed;
        }
        Map<Long, Integer> next = new LinkedHashMap<>();
        for (Long memberId : outcome.members()) {
            Integer shares = proposed.get(memberId);
            next.put(memberId, shares == null ? 1 : shares);
        }
        ruleNote.setValue(describe(outcome, itemName));
        return next;
    }

    /** Re-runs the rules once they finish loading, in case they arrived after the first item. */
    private void reapplyRulesToUntouchedSelection() {
        LineItemWithAssignments item = currentItem();
        if (item == null || item.hasAssignments() || item.item.scope != Scope.UNASSIGNED) {
            return;
        }
        selection.setValue(applyRules(currentSelection(), item.item.name));
    }

    /**
     * Names the people and the keyword that moved them. "A rule did this" would leave the
     * user hunting through settings to find out which one.
     */
    private String describe(StandingRules.Outcome outcome, String itemName) {
        String off = namesWithKeyword(outcome.removed(), itemName, MemberRule.Kind.EXCLUDE);
        String on = namesWithKeyword(outcome.added(), itemName, MemberRule.Kind.INCLUDE);
        if (!off.isEmpty() && !on.isEmpty()) {
            return off + " taken off and " + on + " added by your standing rules";
        }
        if (!off.isEmpty()) {
            return off + " taken off by your standing rules";
        }
        if (!on.isEmpty()) {
            return on + " added by your standing rules";
        }
        return null;
    }

    private String namesWithKeyword(List<Long> memberIds, String itemName, MemberRule.Kind kind) {
        StringBuilder text = new StringBuilder();
        for (Long memberId : memberIds) {
            String name = memberName(memberId);
            if (name.isEmpty()) {
                continue;
            }
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(name);
            MemberRule matched = rules.firstMatch(memberId, itemName, kind);
            if (matched != null) {
                text.append(" (").append(matched.keyword).append(")");
            }
        }
        return text.toString();
    }

    private String memberName(long memberId) {
        for (Member member : participants()) {
            if (member.id == memberId) {
                return member.name;
            }
        }
        return "";
    }

    private Map<Long, Integer> currentSelection() {
        Map<Long, Integer> value = selection.getValue();
        return value == null ? new LinkedHashMap<>() : value;
    }

    /** SPEC 7.9.8: Next is disabled while nothing is selected, unless excluded. */
    public boolean canAdvance() {
        return !currentSelection().isEmpty();
    }

    /**
     * SPEC 7.9.5: with two chips on a $3.32 item the readout reads "$1.66 each". The figure
     * comes from the same splitter the totals do, so what is previewed is what is charged.
     */
    public long[] previewShares() {
        LineItemWithAssignments item = currentItem();
        Map<Long, Integer> current = currentSelection();
        if (item == null || current.isEmpty()) {
            return new long[0];
        }
        int[] weights = new int[current.size()];
        int i = 0;
        for (Integer shares : current.values()) {
            weights[i++] = shares;
        }
        return MoneySplitter.split(item.item.lineTotalCents, weights);
    }

    /** SPEC 7.9.4: exactly one is PERSONAL, everyone is COMMON, otherwise SUBSET. */
    public Scope resolvedScope() {
        Map<Long, Integer> current = currentSelection();
        if (current.isEmpty()) {
            return Scope.UNASSIGNED;
        }
        if (current.size() == participants().size()) {
            return Scope.COMMON;
        }
        return current.size() == 1 ? Scope.PERSONAL : Scope.SUBSET;
    }

    /** Writes the answer, remembers it, and moves on. */
    public void confirmAndAdvance(Callback<Boolean> onDone) {
        LineItemWithAssignments item = currentItem();
        if (item == null) {
            onDone.onResult(false);
            return;
        }
        Scope scope = resolvedScope();
        Map<Long, Integer> current = currentSelection();

        List<ItemAssignment> assignments = new ArrayList<>();
        if (scope == Scope.SUBSET || scope == Scope.PERSONAL) {
            for (Map.Entry<Long, Integer> entry : current.entrySet()) {
                assignments.add(new ItemAssignment(item.item.id, entry.getKey(), entry.getValue()));
            }
        }
        repository.assign(item.item.id, scope, assignments, result -> {
            // SPEC 9.2: remembered only once the user has confirmed it.
            memory.remember(householdId, item.item.name, scope,
                    new ArrayList<>(current.keySet()));
            advance(onDone);
        });
    }

    /** SPEC 7.9.7: Exclude marks the row EXCLUDED and advances. */
    public void excludeAndAdvance(Callback<Boolean> onDone) {
        LineItemWithAssignments item = currentItem();
        if (item == null) {
            onDone.onResult(false);
            return;
        }
        repository.assign(item.item.id, Scope.EXCLUDED, new ArrayList<>(),
                result -> advance(onDone));
    }

    private void advance(Callback<Boolean> onDone) {
        Integer at = index.getValue();
        int next = (at == null ? 0 : at) + 1;
        if (next >= items().size()) {
            // SPEC 7.9.14: the last answer is persisted before the summary opens.
            repository.updateProgress(orderId, DraftStep.SUMMARY, Math.max(0, items().size() - 1));
            onDone.onResult(true);
            return;
        }
        index.setValue(next);
        repository.updateProgress(orderId, DraftStep.ASSIGN, next);
        loadSelectionForCurrent();
        onDone.onResult(false);
    }

    /** SPEC 7.9.9: Back restores the previous item with its selection intact. */
    public void back() {
        Integer at = index.getValue();
        if (at == null || at <= 0) {
            return;
        }
        index.setValue(at - 1);
        repository.updateProgress(orderId, DraftStep.ASSIGN, at - 1);
        loadSelectionForCurrent();
    }

    /** SPEC 7.9.11: jump to any item out of order. */
    public void jumpTo(int position) {
        if (position < 0 || position >= items().size()) {
            return;
        }
        index.setValue(position);
        repository.updateProgress(orderId, DraftStep.ASSIGN, position);
        loadSelectionForCurrent();
    }

    /**
     * Applies every remembered answer to the rows still unanswered, in one action.
     *
     * <p>SPEC 7.9.12 forbids a suggestion advancing the screen by itself, and this does not
     * breach that: the user asked for the suggestions to be applied, which is a decision
     * rather than an assumption, and every row it touches stays editable afterwards. For a
     * weekly shop of largely the same things this is the difference between answering
     * eighteen questions and answering four.
     *
     * @param onDone how many rows a remembered answer was found for
     */
    public void acceptAllSuggestions(Callback<Integer> onDone) {
        List<LineItemWithAssignments> all = items();
        List<Long> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (LineItemWithAssignments row : all) {
            if (row.item.scope.isUnanswered()) {
                ids.add(row.item.id);
                names.add(row.item.name);
            }
        }
        if (ids.isEmpty()) {
            onDone.onResult(0);
            return;
        }
        memory.suggestForAll(householdId, names, suggestions -> {
            List<Scope> scopes = new ArrayList<>();
            List<List<Long>> members = new ArrayList<>();
            for (AssignmentMemoryService.Suggestion suggestion : suggestions) {
                if (suggestion == null) {
                    scopes.add(null);
                    members.add(null);
                    continue;
                }
                if (suggestion.scope == Scope.COMMON) {
                    scopes.add(Scope.COMMON);
                    members.add(new ArrayList<>());
                    continue;
                }
                // Only members who are actually in on this order can be assigned to it
                // (SPEC 7.8.5), so a remembered answer naming someone who is not is dropped.
                List<Long> eligible = new ArrayList<>();
                for (Long memberId : suggestion.memberIds) {
                    if (isParticipant(memberId)) {
                        eligible.add(memberId);
                    }
                }
                if (eligible.isEmpty()) {
                    scopes.add(null);
                    members.add(null);
                } else {
                    scopes.add(eligible.size() == participants().size()
                            ? Scope.COMMON
                            : (eligible.size() == 1 ? Scope.PERSONAL : Scope.SUBSET));
                    members.add(eligible.size() == participants().size()
                            ? new ArrayList<>() : eligible);
                }
            }
            repository.applyRememberedAnswers(ids, scopes, members, result -> {
                loadSelectionForCurrent();
                onDone.onResult(result.isOk() ? result.value() : 0);
            });
        });
    }

    /** SPEC 7.9.10: after a confirmation naming the count. */
    public void assignRemainingAsCommon(Callback<Integer> onDone) {
        LineItemWithAssignments item = currentItem();
        int from = item == null ? 0 : item.item.position;
        repository.assignRemainingAsCommon(orderId, from, result ->
                onDone.onResult(result.isOk() ? result.value() : 0));
    }

    public int remainingUnassignedFromHere() {
        Integer at = index.getValue();
        int start = at == null ? 0 : at;
        int count = 0;
        List<LineItemWithAssignments> all = items();
        for (int i = start; i < all.size(); i++) {
            if (all.get(i).item.scope.isUnanswered()) {
                count++;
            }
        }
        return count;
    }

    public LiveData<String> progressLabel() {
        MediatorLiveData<String> label = new MediatorLiveData<>();
        label.addSource(index, at -> label.setValue(render(at, items().size())));
        label.addSource(bundle, value -> label.setValue(render(index.getValue(), items().size())));
        return label;
    }

    private static String render(Integer at, int total) {
        int position = at == null ? 0 : at;
        return total == 0 ? "" : "Item " + (position + 1) + " of " + total;
    }
}
