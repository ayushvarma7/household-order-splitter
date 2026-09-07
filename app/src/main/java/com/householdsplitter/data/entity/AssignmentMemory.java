package com.householdsplitter.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.householdsplitter.core.calc.Scope;

/**
 * SPEC 5.8. What this household usually does with an item.
 *
 * <p>SPEC 7.9.13, 9.4 and PROMPT hard rule 3: every row here comes from an assignment this
 * household confirmed itself. The app ships with no item knowledge whatsoever, so a fresh
 * install makes no suggestions at all until the user has answered for something once.
 */
@Entity(
        tableName = "assignment_memory",
        foreignKeys = @ForeignKey(
                entity = Household.class,
                parentColumns = "id",
                childColumns = "householdId",
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index("normalizedName"),
                @Index(value = {"householdId", "normalizedName"}, unique = true)
        })
public class AssignmentMemory {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long householdId;

    /** SPEC 9.1. */
    @NonNull
    public String normalizedName = "";

    @NonNull
    public Scope scope = Scope.COMMON;

    /** Empty for COMMON, which resolves against the participants instead (SPEC 5.9). */
    @NonNull
    public String memberIdsCsv = "";

    public long lastUsedAt;

    /** SPEC 9.3: a suggestion needs at least one prior use. */
    @ColumnInfo(defaultValue = "0")
    public int useCount;

    public AssignmentMemory() {
    }
}
