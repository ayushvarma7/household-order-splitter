package com.householdsplitter.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** SPEC 5.1. The group. Its name is typed by the user at first launch (SPEC 7.1). */
@Entity(tableName = "households")
public class Household {

    /** SPEC 7.1.4: the trimmed name must be 1 to 60 characters. */
    public static final int NAME_MIN = 1;
    public static final int NAME_MAX = 60;

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    public long createdAt;

    public Household() {
    }

    public Household(@NonNull String name, long createdAt) {
        this.name = name;
        this.createdAt = createdAt;
    }

    public static boolean isValidName(String candidate) {
        if (candidate == null) {
            return false;
        }
        int length = candidate.trim().length();
        return length >= NAME_MIN && length <= NAME_MAX;
    }
}
