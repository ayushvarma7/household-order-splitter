package com.householdsplitter.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** SPEC 5.2. One person in the household. Their name is typed by the user (SPEC 7.2). */
@Entity(
        tableName = "members",
        foreignKeys = @ForeignKey(
                entity = Household.class,
                parentColumns = "id",
                childColumns = "householdId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("householdId"), @Index({"householdId", "isArchived"})})
public class Member {

    /** SPEC 7.2.4: the trimmed name must be 1 to 40 characters. */
    public static final int NAME_MIN = 1;
    public static final int NAME_MAX = 40;

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long householdId;

    @NonNull
    public String name = "";

    /** SPEC 7.2.6: assigned round-robin from the palette at creation. */
    @NonNull
    public String colorHex = "#1F77B4";

    public int sortOrder;

    /** SPEC 5.10: soft delete, so historical orders keep rendering their name. */
    @ColumnInfo(defaultValue = "0")
    public boolean isArchived;

    public Member() {
    }

    public Member(long householdId, @NonNull String name, @NonNull String colorHex, int sortOrder) {
        this.householdId = householdId;
        this.name = name;
        this.colorHex = colorHex;
        this.sortOrder = sortOrder;
    }

    public static boolean isValidName(String candidate) {
        if (candidate == null) {
            return false;
        }
        int length = candidate.trim().length();
        return length >= NAME_MIN && length <= NAME_MAX;
    }

    /** The avatar label of SPEC 7.2.3. Up to two initials, from a name of any script. */
    public String initials() {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return "?";
        }
        String[] words = trimmed.split("\\s+");
        StringBuilder out = new StringBuilder();
        out.append(Character.toUpperCase(words[0].charAt(0)));
        if (words.length > 1 && !words[words.length - 1].isEmpty()) {
            out.append(Character.toUpperCase(words[words.length - 1].charAt(0)));
        }
        return out.toString();
    }
}
