package com.householdsplitter.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.householdsplitter.data.entity.MemberRule;

import java.util.List;

@Dao
public interface MemberRuleDao {

    @Query("SELECT * FROM member_rules WHERE householdId = :householdId ORDER BY keyword, id")
    LiveData<List<MemberRule>> observeForHousehold(long householdId);

    @Query("SELECT * FROM member_rules WHERE householdId = :householdId ORDER BY keyword, id")
    List<MemberRule> getForHouseholdSync(long householdId);

    @Insert
    long insert(MemberRule rule);

    @Query("DELETE FROM member_rules WHERE id = :ruleId")
    void deleteById(long ruleId);

    @Query("DELETE FROM member_rules WHERE householdId = :householdId")
    void clearForHousehold(long householdId);
}
