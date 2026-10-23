package com.householdsplitter.core.time;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** The day you picked is the day you get, wherever you are. */
public class PickerDatesTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final ZoneId UTC = ZoneId.of("UTC");

    private static long localMidnight(String date, ZoneId zone) {
        return LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli();
    }

    private static String dayIn(long millis, ZoneId zone) {
        return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString();
    }

    /** West of UTC, where a naive pass-through opens the picker a day early. */
    @Test
    public void aNewYorkDateOpensThePickerOnTheSameDay() {
        long stored = localMidnight("2026-10-05", NEW_YORK);
        assertEquals("2026-10-05", dayIn(PickerDates.toPickerUtc(stored, NEW_YORK), UTC));
    }

    /** East of UTC, where it runs the other way. */
    @Test
    public void aKolkataDateOpensThePickerOnTheSameDay() {
        long stored = localMidnight("2026-10-05", KOLKATA);
        assertEquals("2026-10-05", dayIn(PickerDates.toPickerUtc(stored, KOLKATA), UTC));
    }

    @Test
    public void aSelectionComesBackAsLocalMidnightOnTheDayShown() {
        long utcMidnight = localMidnight("2026-10-05", UTC);
        for (ZoneId zone : new ZoneId[] {NEW_YORK, KOLKATA, UTC}) {
            long local = PickerDates.fromPickerUtc(utcMidnight, zone);
            assertEquals("wrong day in " + zone, "2026-10-05", dayIn(local, zone));
            assertEquals("not midnight in " + zone, localMidnight("2026-10-05", zone), local);
        }
    }

    /** Round tripping a stored date leaves it alone, which is what editing one order does. */
    @Test
    public void aRoundTripChangesNothing() {
        for (ZoneId zone : new ZoneId[] {NEW_YORK, KOLKATA, UTC}) {
            for (String day : new String[] {"2026-01-01", "2026-03-08", "2026-10-05",
                    "2026-11-01", "2026-12-31"}) {
                long stored = localMidnight(day, zone);
                long back = PickerDates.fromPickerUtc(PickerDates.toPickerUtc(stored, zone), zone);
                assertEquals(day + " in " + zone, stored, back);
            }
        }
    }

    /** A time of day on the stored value does not drag the date to a neighbouring one. */
    @Test
    public void aStoredTimeOfDayStillPicksItsOwnDay() {
        long lateEvening = LocalDate.parse("2026-10-05").atStartOfDay(NEW_YORK)
                .plusHours(23).plusMinutes(59).toInstant().toEpochMilli();
        assertEquals("2026-10-05", dayIn(PickerDates.toPickerUtc(lateEvening, NEW_YORK), UTC));
    }
}
