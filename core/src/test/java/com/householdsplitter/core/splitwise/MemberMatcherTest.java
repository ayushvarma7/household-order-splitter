package com.householdsplitter.core.splitwise;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class MemberMatcherTest {

    private static SplitwiseUser user(long id, String first, String last) {
        return new SplitwiseUser(id, first, last, "", "");
    }

    @Test
    public void matchesFullNameIgnoringCase() {
        List<SplitwiseUser> all = Arrays.asList(user(1, "Sam", "Lee"), user(2, "Sam", "Ng"));
        assertEquals(2L, MemberMatcher.suggest("  sam NG ", all));
    }

    @Test
    public void matchesFirstNameWhenUnique() {
        List<SplitwiseUser> all = Arrays.asList(user(1, "Priya", "Rao"), user(2, "Sam", "Ng"));
        assertEquals(1L, MemberMatcher.suggest("Priya", all));
    }

    @Test
    public void ambiguousFirstNameSuggestsNothing() {
        List<SplitwiseUser> all = Arrays.asList(user(1, "Sam", "Lee"), user(2, "Sam", "Ng"));
        assertEquals(-1L, MemberMatcher.suggest("Sam", all));
    }

    @Test
    public void noMatchSuggestsNothing() {
        assertEquals(-1L, MemberMatcher.suggest("Zed", Arrays.asList(user(1, "Sam", "Lee"))));
        assertEquals(-1L, MemberMatcher.suggest("", Arrays.asList(user(1, "Sam", "Lee"))));
    }
}
