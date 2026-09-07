package com.householdsplitter.core.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BackupRotationTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private static long at(int year, int month, int day, int hour) {
        return LocalDateTime.of(year, month, day, hour, 0)
                .atZone(NEW_YORK).toInstant().toEpochMilli();
    }

    @Test
    public void theNameCarriesTheDate() {
        assertEquals("household-splitter-2026-09-07.json",
                BackupRotation.nameFor(at(2026, 9, 7, 13), NEW_YORK));
    }

    /** In the device's own zone: a late-evening backup belongs to that evening's date. */
    @Test
    public void theNameUsesTheLocalDate() {
        assertEquals("household-splitter-2026-09-07.json",
                BackupRotation.nameFor(at(2026, 9, 7, 21), NEW_YORK));
        assertEquals("household-splitter-2026-09-08.json",
                BackupRotation.nameFor(at(2026, 9, 7, 21), ZoneId.of("UTC")));
    }

    @Test
    public void ourOwnNamesAreRecognised() {
        assertTrue(BackupRotation.isBackupName("household-splitter-2026-09-07.json"));
        assertFalse(BackupRotation.isBackupName("household-splitter-2026-9-7.json"));
        assertFalse(BackupRotation.isBackupName("taxes-2026.json"));
        assertFalse(BackupRotation.isBackupName("household-splitter-2026-09-07.txt"));
        assertFalse(BackupRotation.isBackupName(null));
    }

    @Test
    public void nothingIsDeletedBelowTheLimit() {
        List<String> existing = Arrays.asList(
                "household-splitter-2026-09-01.json",
                "household-splitter-2026-09-02.json");

        assertTrue(BackupRotation.toDelete(existing, "household-splitter-2026-09-03.json", 7)
                .isEmpty());
    }

    /**
     * Exactly enough goes to leave {@code keep} files including the new one, oldest first.
     * The input order does not matter: the date is fixed-width and leading, so sorting the
     * names sorts them by date.
     */
    @Test
    public void theOldestGoesSoTheLimitIsMetExactly() {
        List<String> existing = Arrays.asList(
                "household-splitter-2026-09-03.json",
                "household-splitter-2026-09-01.json",
                "household-splitter-2026-09-02.json");

        List<String> doomed =
                BackupRotation.toDelete(existing, "household-splitter-2026-09-04.json", 3);

        assertEquals("09-01 goes, leaving 09-02, 09-03 and the new 09-04",
                Arrays.asList("household-splitter-2026-09-01.json"), doomed);
    }

    /** Several at once when the folder is further over the limit. */
    @Test
    public void aBacklogIsPrunedDownInOneGo() {
        List<String> existing = Arrays.asList(
                "household-splitter-2026-09-01.json",
                "household-splitter-2026-09-02.json",
                "household-splitter-2026-09-03.json",
                "household-splitter-2026-09-04.json");

        List<String> doomed =
                BackupRotation.toDelete(existing, "household-splitter-2026-09-05.json", 2);

        assertEquals(Arrays.asList(
                "household-splitter-2026-09-01.json",
                "household-splitter-2026-09-02.json",
                "household-splitter-2026-09-03.json"), doomed);
    }

    /** The one about to be written counts towards the limit, or the folder grows forever. */
    @Test
    public void theNewFileTakesOneOfTheSlots() {
        List<String> existing = Arrays.asList(
                "household-splitter-2026-09-01.json",
                "household-splitter-2026-09-02.json");

        List<String> doomed =
                BackupRotation.toDelete(existing, "household-splitter-2026-09-03.json", 2);

        assertEquals(Arrays.asList("household-splitter-2026-09-01.json"), doomed);
    }

    /** Rewriting the same day's file is not a new slot, so nothing needs to go. */
    @Test
    public void rewritingTodaysFileDoesNotPruneAnything() {
        List<String> existing = Arrays.asList(
                "household-splitter-2026-09-01.json",
                "household-splitter-2026-09-02.json");

        List<String> doomed =
                BackupRotation.toDelete(existing, "household-splitter-2026-09-02.json", 2);

        assertTrue(doomed.isEmpty());
    }

    /**
     * The user chose a folder, not a scratch space. Deleting a file the app did not write
     * would be indefensible.
     */
    @Test
    public void filesWeDidNotWriteAreNeverTouched() {
        List<String> existing = Arrays.asList(
                "taxes-2019.json",
                "holiday-photos.zip",
                "household-splitter-notes.json",
                "household-splitter-2026-09-01.json");

        List<String> doomed =
                BackupRotation.toDelete(existing, "household-splitter-2026-09-02.json", 1);

        assertEquals(Arrays.asList("household-splitter-2026-09-01.json"), doomed);
    }

    @Test
    public void anEmptyFolderYieldsNothingToDelete() {
        assertTrue(BackupRotation.toDelete(Collections.emptyList(), "any.json", 7).isEmpty());
        assertTrue(BackupRotation.toDelete(null, "any.json", 7).isEmpty());
    }

    @Test
    public void theFirstBackupIsAlwaysDue() {
        assertTrue(BackupRotation.isDue(0L, at(2026, 9, 7, 13), NEW_YORK));
    }

    @Test
    public void aSecondBackupOnTheSameDayIsNotDue() {
        assertFalse(BackupRotation.isDue(
                at(2026, 9, 7, 9), at(2026, 9, 7, 23), NEW_YORK));
    }

    /**
     * The next calendar day, not a fixed number of hours. A household that settles up on
     * Sunday evenings should get a Sunday backup rather than one skipped because the last
     * was 23 hours ago.
     */
    @Test
    public void theNextDayIsDueEvenIfLessThanADayHasPassed() {
        assertTrue(BackupRotation.isDue(
                at(2026, 9, 7, 23), at(2026, 9, 8, 8), NEW_YORK));
    }

    /** A clock that moved backwards should not lock backups out indefinitely. */
    @Test
    public void aBackwardsClockStillAllowsABackup() {
        assertTrue(BackupRotation.isDue(
                at(2026, 9, 7, 13), at(2026, 9, 1, 13), NEW_YORK));
    }
}
