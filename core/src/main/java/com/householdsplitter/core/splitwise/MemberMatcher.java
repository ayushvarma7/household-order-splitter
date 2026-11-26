package com.householdsplitter.core.splitwise;

import java.util.List;
import java.util.Locale;

/**
 * Suggests which Splitwise person a household member is, from names alone.
 *
 * <p>Only a unique match is suggested. Guessing between two people called "Sam" would put
 * someone's share on the wrong account, which is worse than asking.
 */
public final class MemberMatcher {

    private MemberMatcher() {
    }

    /** The matching user's id, or -1 when there is no single clear match. */
    public static long suggest(String memberName, List<SplitwiseUser> candidates) {
        String wanted = normalise(memberName);
        if (wanted.isEmpty()) {
            return -1L;
        }
        long full = uniqueMatch(candidates, wanted, true);
        if (full != -1L) {
            return full;
        }
        return uniqueMatch(candidates, wanted, false);
    }

    private static long uniqueMatch(List<SplitwiseUser> candidates, String wanted, boolean fullName) {
        long found = -1L;
        int count = 0;
        for (SplitwiseUser user : candidates) {
            String name = normalise(fullName ? user.firstName() + " " + user.lastName()
                    : user.firstName());
            if (!name.isEmpty() && name.equals(wanted)) {
                found = user.id();
                count++;
            }
        }
        return count == 1 ? found : -1L;
    }

    private static String normalise(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
