package com.householdsplitter.core.suggest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Standing rules about who is and is not on certain kinds of item.
 *
 * <p>"Ben is never on alcohol" is a fact about a household that otherwise has to be
 * re-entered on every order containing beer. Recording it once turns a recurring correction
 * into nothing.
 *
 * <p>This is not shipped item knowledge, which SPEC 7.9.13 and PROMPT hard rule 3 forbid.
 * Every keyword here was typed by the user about their own household; the app knows nothing
 * about groceries and never guesses a rule.
 *
 * <p>Applying a rule must never be silent. {@link Outcome} carries who was added and who was
 * removed precisely so the screen can say so: a rule quietly changing who pays for something
 * would be worse than no rule at all.
 */
public final class StandingRules {

    private StandingRules() {
    }

    public enum Kind {
        /** Take this member off a matching item, however it was assigned. */
        EXCLUDE,
        /** Put this member on a matching item whenever anybody is. */
        INCLUDE
    }

    /** One rule: a member, a keyword to look for, and what to do about it. */
    public static final class Rule {

        private final long memberId;
        private final String keyword;
        private final Kind kind;

        public Rule(long memberId, String keyword, Kind kind) {
            this.memberId = memberId;
            this.keyword = keyword == null ? "" : keyword.trim();
            this.kind = kind;
        }

        public long memberId() {
            return memberId;
        }

        public String keyword() {
            return keyword;
        }

        public Kind kind() {
            return kind;
        }

        /**
         * A plain case-insensitive substring, deliberately. A household writing "beer"
         * should not have to think about regular expressions, and a pattern language would
         * turn a mistyped rule into a silently wrong split.
         */
        public boolean matches(String itemName) {
            if (itemName == null || keyword.isEmpty()) {
                return false;
            }
            return itemName.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
        }
    }

    /** What the rules did, so the screen can say so rather than change things silently. */
    public static final class Outcome {

        private final List<Long> members;
        private final List<Long> removed;
        private final List<Long> added;

        Outcome(List<Long> members, List<Long> removed, List<Long> added) {
            this.members = members;
            this.removed = removed;
            this.added = added;
        }

        /** Who is on the item after the rules were applied. */
        public List<Long> members() {
            return members;
        }

        public List<Long> removed() {
            return removed;
        }

        public List<Long> added() {
            return added;
        }

        public boolean changedAnything() {
            return !removed.isEmpty() || !added.isEmpty();
        }
    }

    /**
     * Applies every rule that matches this item to a proposed set of members.
     *
     * @param proposed      who the user picked, or everybody for a common item
     * @param participants  who is in on the order at all; a rule cannot add anybody else
     *                      (SPEC 7.8.5)
     */
    public static Outcome apply(String itemName, List<Long> proposed, List<Long> participants,
                                List<Rule> rules) {
        Set<Long> result = new LinkedHashSet<>(proposed == null ? new ArrayList<>() : proposed);
        List<Long> removed = new ArrayList<>();
        List<Long> added = new ArrayList<>();

        if (rules != null) {
            // Includes first, then excludes, so an exclude always wins a direct conflict.
            // A household that has said somebody is never on a thing means it.
            for (Rule rule : rules) {
                if (rule.kind() != Kind.INCLUDE || !rule.matches(itemName)) {
                    continue;
                }
                if (participants != null && !participants.contains(rule.memberId())) {
                    continue;
                }
                if (result.add(rule.memberId())) {
                    added.add(rule.memberId());
                }
            }
            for (Rule rule : rules) {
                if (rule.kind() != Kind.EXCLUDE || !rule.matches(itemName)) {
                    continue;
                }
                if (result.remove(rule.memberId())) {
                    removed.add(rule.memberId());
                    added.remove(rule.memberId());
                }
            }
        }

        // A rule must never empty an item: that would turn a charge somebody owes into a
        // charge nobody owes, and the totals would stop adding up. Better to leave the
        // user's own choice standing and say nothing was changed.
        if (result.isEmpty()) {
            return new Outcome(new ArrayList<>(proposed == null ? new ArrayList<>() : proposed),
                    new ArrayList<>(), new ArrayList<>());
        }
        return new Outcome(new ArrayList<>(result), removed, added);
    }
}
