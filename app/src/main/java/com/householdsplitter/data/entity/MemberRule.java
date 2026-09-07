package com.householdsplitter.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A standing rule about one member and one kind of item, written by the user.
 *
 * <p>"Ben is never on alcohol" is a fact about the household that otherwise has to be
 * re-entered on every order that contains beer. Recording it once turns a recurring
 * correction into nothing.
 *
 * <p>This is not shipped item knowledge. SPEC 7.9.13 and PROMPT hard rule 3 forbid the app
 * knowing which groceries are usually shared, and nothing here is seeded: every keyword is
 * typed by the user about their own household.
 */
@Entity(
        tableName = "member_rules",
        foreignKeys = {
                @ForeignKey(
                        entity = Household.class,
                        parentColumns = "id",
                        childColumns = "householdId",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(
                        entity = Member.class,
                        parentColumns = "id",
                        childColumns = "memberId",
                        onDelete = ForeignKey.CASCADE)
        },
        indices = {@Index("householdId"), @Index("memberId")})
public class MemberRule {

    /** What the rule does when an item matches. */
    public enum Kind {
        /** Take this member off the item, however it was assigned. */
        EXCLUDE,
        /** Put this member on the item whenever anybody is. */
        INCLUDE
    }

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long householdId;

    public long memberId;

    /**
     * A word or phrase to look for in the item name, matched case-insensitively against the
     * normalised name. Deliberately a plain substring rather than a pattern: a household
     * writing "beer" should not have to think about regular expressions.
     */
    @NonNull
    public String keyword = "";

    @NonNull
    @ColumnInfo(defaultValue = "EXCLUDE")
    public Kind kind = Kind.EXCLUDE;

    public long createdAt;

    public MemberRule() {
    }

    public MemberRule(long householdId, long memberId, @NonNull String keyword, @NonNull Kind kind) {
        this.householdId = householdId;
        this.memberId = memberId;
        this.keyword = keyword;
        this.kind = kind;
        this.createdAt = System.currentTimeMillis();
    }

    /** True when this rule has something to say about the given item name. */
    public boolean matches(String itemName) {
        if (itemName == null || keyword.trim().isEmpty()) {
            return false;
        }
        return itemName.toLowerCase().contains(keyword.trim().toLowerCase());
    }
}
