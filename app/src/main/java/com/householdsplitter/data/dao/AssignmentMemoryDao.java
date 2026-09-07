package com.householdsplitter.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.householdsplitter.data.entity.AssignmentMemory;

import java.util.List;

/** SPEC 9. Populated only from this household's own confirmed assignments. */
@Dao
public interface AssignmentMemoryDao {

    /** SPEC 9.3: an exact normalised match with at least one prior use. */
    @Query("SELECT * FROM assignment_memory WHERE householdId = :householdId "
            + "AND normalizedName = :normalizedName AND useCount > 0 "
            + "ORDER BY lastUsedAt DESC LIMIT 1")
    AssignmentMemory findSync(long householdId, String normalizedName);

    /** Feeds the brand-prefix inference of SPEC 9.1, from this household's history alone. */
    @Query("SELECT * FROM assignment_memory WHERE householdId = :householdId")
    List<AssignmentMemory> getAllSync(long householdId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(AssignmentMemory memory);

    @Update
    void update(AssignmentMemory memory);

    /** SPEC 9.5: "Clear suggestion history". */
    @Query("DELETE FROM assignment_memory WHERE householdId = :householdId")
    void clearForHousehold(long householdId);
}
