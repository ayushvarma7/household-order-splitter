package com.householdsplitter.data.repo;

import androidx.lifecycle.LiveData;

import com.householdsplitter.data.dao.AssignmentDao;
import com.householdsplitter.data.dao.HouseholdDao;
import com.householdsplitter.data.dao.MemberDao;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.ui.common.MemberPalette;
import com.householdsplitter.util.AppExecutors;
import com.householdsplitter.util.Callback;
import com.householdsplitter.util.Result;

import java.util.List;

/** The group and its members. SPEC 5.1, 5.2, 5.10, 7.1, 7.2, 7.13. */
public class HouseholdRepository {

    private final HouseholdDao householdDao;
    private final MemberDao memberDao;
    private final AssignmentDao assignmentDao;
    private final AppExecutors executors;

    public HouseholdRepository(HouseholdDao householdDao, MemberDao memberDao,
                               AssignmentDao assignmentDao, AppExecutors executors) {
        this.householdDao = householdDao;
        this.memberDao = memberDao;
        this.assignmentDao = assignmentDao;
        this.executors = executors;
    }

    /** Null until the user creates one, which is what routes first launch to S1. */
    /**
     * The first group that exists.
     *
     * <p>Only correct for asking whether <em>any</em> group exists, which is what the start
     * destination needs. Every screen that shows a group to the user wants
     * {@link #observeCurrent(long)} instead: this one answers "a group" where they are
     * asking "the group I am in".
     */
    public LiveData<Household> observeAnyHousehold() {
        return householdDao.observeHousehold();
    }

    /**
     * The group being looked at.
     *
     * <p>Added because the screens predate groups being switchable and all asked for the
     * first row. That was the same answer as the right one while there was only ever one
     * group, and silently the wrong answer afterwards: with two groups, the members screen
     * added people to whichever had the lower id, so making a second group and typing names
     * into it put them in the first one.
     *
     * <p>Zero means nothing has been chosen, which is every install that has never switched,
     * and falls back to the first group so those installs behave exactly as before.
     */
    public LiveData<Household> observeCurrent(long householdId) {
        return householdId == 0L
                ? householdDao.observeHousehold()
                : householdDao.observeById(householdId);
    }

    public LiveData<List<Member>> observeMembers(long householdId) {
        return memberDao.observeActive(householdId);
    }

    /** SPEC 7.1.5. */
    public void createHousehold(String name, Callback<Result<Long>> callback) {
        executors.diskIO().execute(() -> {
            String trimmed = name == null ? "" : name.trim();
            if (!Household.isValidName(trimmed)) {
                post(callback, Result.failure("Enter a name of 1 to 60 characters"));
                return;
            }
            long id = householdDao.insert(new Household(trimmed, System.currentTimeMillis()));
            post(callback, Result.ok(id));
        });
    }

    /** SPEC 7.13.1: "Rename group". */
    public void renameHousehold(long householdId, String name, Callback<Result<Void>> callback) {
        executors.diskIO().execute(() -> {
            String trimmed = name == null ? "" : name.trim();
            if (!Household.isValidName(trimmed)) {
                post(callback, Result.failure("Enter a name of 1 to 60 characters"));
                return;
            }
            // By the id it was given. It used to fetch the first group and rename that
            // whatever it was asked for, so renaming the second group renamed the first.
            Household household = householdDao.getByIdSync(householdId);
            if (household == null) {
                post(callback, Result.failure("No group to rename"));
                return;
            }
            household.name = trimmed;
            householdDao.update(household);
            post(callback, Result.ok(null));
        });
    }

    /**
     * SPEC 7.2.4 to 7.2.6: a trimmed name of 1 to 40 characters, rejected if it duplicates
     * an existing one case-insensitively, with the next palette colour assigned round-robin.
     */
    public void addMember(long householdId, String name, Callback<Result<Long>> callback) {
        executors.diskIO().execute(() -> {
            String trimmed = name == null ? "" : name.trim();
            if (!Member.isValidName(trimmed)) {
                post(callback, Result.failure("Enter a name of 1 to 40 characters"));
                return;
            }
            if (memberDao.countByNameSync(householdId, trimmed, -1L) > 0) {
                post(callback, Result.failure("Someone is already called that"));
                return;
            }
            int nextOrder = memberDao.maxSortOrderSync(householdId) + 1;
            Member member = new Member(householdId, trimmed,
                    MemberPalette.colorForIndex(nextOrder), nextOrder);
            long id = memberDao.insert(member);
            post(callback, Result.ok(id));
        });
    }

    /** SPEC 7.2.7. */
    public void renameMember(long memberId, String name, Callback<Result<Void>> callback) {
        executors.diskIO().execute(() -> {
            String trimmed = name == null ? "" : name.trim();
            if (!Member.isValidName(trimmed)) {
                post(callback, Result.failure("Enter a name of 1 to 40 characters"));
                return;
            }
            Member member = memberDao.getByIdSync(memberId);
            if (member == null) {
                post(callback, Result.failure("That person is no longer in the group"));
                return;
            }
            if (memberDao.countByNameSync(member.householdId, trimmed, memberId) > 0) {
                post(callback, Result.failure("Someone is already called that"));
                return;
            }
            member.name = trimmed;
            memberDao.update(member);
            post(callback, Result.ok(null));
        });
    }

    /** True when removing this member would archive rather than delete them (SPEC 5.10). */
    public void hasHistory(long memberId, Callback<Boolean> callback) {
        executors.diskIO().execute(() ->
                post(callback, memberDao.historyCountSync(memberId) > 0));
    }

    /**
     * SPEC 7.2.8 and 5.10: gone entirely if they have no history, archived if they do, so
     * that a settled order still renders their name and amount (SPEC 11.7).
     */
    public void removeMember(long memberId, Callback<Result<Boolean>> callback) {
        executors.diskIO().execute(() -> {
            Member member = memberDao.getByIdSync(memberId);
            if (member == null) {
                post(callback, Result.failure("That person is no longer in the group"));
                return;
            }
            boolean archived = memberDao.historyCountSync(memberId) > 0;
            if (archived) {
                memberDao.archive(memberId);
            } else {
                memberDao.delete(member);
            }
            post(callback, Result.ok(archived));
        });
    }

    private <T> void post(Callback<T> callback, T value) {
        if (callback != null) {
            executors.mainThread().execute(() -> callback.onResult(value));
        }
    }
}
