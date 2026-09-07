package com.householdsplitter.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.householdsplitter.data.entity.Member;

import java.util.List;

@Dao
public interface MemberDao {

    /** The list S2, S8 and S13 show: everyone still active, in display order. */
    @Query("SELECT * FROM members WHERE householdId = :householdId AND isArchived = 0 "
            + "ORDER BY sortOrder, id")
    LiveData<List<Member>> observeActive(long householdId);

    @Query("SELECT * FROM members WHERE householdId = :householdId AND isArchived = 0 "
            + "ORDER BY sortOrder, id")
    List<Member> getActiveSync(long householdId);

    /** SPEC 11.7: a settled order still renders an archived member's name. */
    @Query("SELECT * FROM members WHERE householdId = :householdId ORDER BY sortOrder, id")
    List<Member> getAllSync(long householdId);

    @Query("SELECT * FROM members WHERE id = :memberId")
    Member getByIdSync(long memberId);

    /** SPEC 7.2.5: duplicates are rejected case-insensitively, on the trimmed name. */
    @Query("SELECT COUNT(*) FROM members WHERE householdId = :householdId "
            + "AND isArchived = 0 AND LOWER(TRIM(name)) = LOWER(TRIM(:name)) AND id != :excludingId")
    int countByNameSync(long householdId, String name, long excludingId);

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM members WHERE householdId = :householdId")
    int maxSortOrderSync(long householdId);

    @Query("SELECT COUNT(*) FROM members WHERE householdId = :householdId AND isArchived = 0")
    int activeCountSync(long householdId);

    /**
     * SPEC 5.10 and 7.2.8: a member with any assignment or participation row is archived
     * rather than deleted, so historical orders keep rendering their name.
     */
    @Query("SELECT (SELECT COUNT(*) FROM item_assignments WHERE memberId = :memberId) "
            + "+ (SELECT COUNT(*) FROM order_participants WHERE memberId = :memberId) "
            + "+ (SELECT COUNT(*) FROM orders WHERE payerMemberId = :memberId)")
    int historyCountSync(long memberId);

    @Insert
    long insert(Member member);

    @Update
    void update(Member member);

    @Delete
    void delete(Member member);

    @Query("UPDATE members SET isArchived = 1 WHERE id = :memberId")
    void archive(long memberId);
}
