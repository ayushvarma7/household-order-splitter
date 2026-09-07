package com.householdsplitter.core.backup;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Naming and pruning dated backup files.
 *
 * <p>A backup that overwrites itself is not a backup: the failure it has to survive is a bad
 * write or a file that turns out not to restore. So each one is its own dated file and the
 * oldest are pruned once there are enough.
 *
 * <p>Pure, and tested, because the cost of a mistake here is deleting the wrong file. In
 * particular a name that does not match the app's own pattern is never a candidate for
 * deletion, whatever else is in the folder the user picked.
 */
public final class BackupRotation {

    /** Enough history to notice a bad backup and still have a good one behind it. */
    public static final int DEFAULT_KEEP = 7;

    private static final String PREFIX = "household-splitter-";
    private static final String SUFFIX = ".json";
    private static final Pattern NAME = Pattern.compile(
            "^" + Pattern.quote(PREFIX) + "(\\d{4}-\\d{2}-\\d{2})(?:-(\\d+))?"
                    + Pattern.quote(SUFFIX) + "$");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    private BackupRotation() {
    }

    /**
     * The file name for a backup taken at the given moment.
     *
     * <p>Dated rather than timestamped so a day's backups collapse into one file, which is
     * what makes "keep 7" mean a week rather than an arbitrary stretch of an afternoon.
     */
    public static String nameFor(long epochMillis, ZoneId zone) {
        return PREFIX + LocalDate.ofInstant(Instant.ofEpochMilli(epochMillis), zone).format(DATE)
                + SUFFIX;
    }

    /** True when a file name is one of ours, and so eligible to be counted or pruned. */
    public static boolean isBackupName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /**
     * Which of {@code existing} to delete so that at most {@code keep} of the app's own
     * backups remain, counting a backup about to be written under {@code newName}.
     *
     * <p>Anything that is not one of the app's own names is left alone. The user chose a
     * folder, not a scratch space, and deleting a file the app did not write would be
     * indefensible.
     */
    public static List<String> toDelete(List<String> existing, String newName, int keep) {
        if (existing == null || keep < 1) {
            return Collections.emptyList();
        }
        List<String> ours = new ArrayList<>();
        for (String name : existing) {
            if (isBackupName(name) && !name.equals(newName)) {
                ours.add(name);
            }
        }
        // The names sort chronologically because the date is fixed-width and leading, so
        // sorting them as strings is sorting them by date. Oldest first.
        Collections.sort(ours);

        // The new file takes one of the slots, whether or not it is already there.
        int allowed = Math.max(0, keep - 1);
        List<String> doomed = new ArrayList<>();
        for (int i = 0; i < ours.size() - allowed; i++) {
            doomed.add(ours.get(i));
        }
        return doomed;
    }

    /**
     * True when a backup taken at {@code lastAt} is old enough to take another, which is
     * the next calendar day in the device's own zone rather than a fixed number of hours.
     * A household that settles up on Sunday evenings should get a Sunday backup, not one
     * skipped because the last was 23 hours ago.
     */
    public static boolean isDue(long lastAt, long now, ZoneId zone) {
        if (lastAt <= 0L) {
            return true;
        }
        if (now < lastAt) {
            // The clock moved backwards. Taking one is the safe answer.
            return true;
        }
        LocalDate last = LocalDate.ofInstant(Instant.ofEpochMilli(lastAt), zone);
        LocalDate today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone);
        return today.isAfter(last);
    }
}
