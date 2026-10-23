package com.householdsplitter.core.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Converting between the app's dates and the Material date picker's.
 *
 * <p>The two disagree, and quietly. An order date is local midnight, because "5 October"
 * means the fifth where the person was standing. {@link com.google.android.material.datepicker.MaterialDatePicker}
 * works in UTC midnight, and hands back a timestamp that is not midnight anywhere else.
 *
 * <p>Passing those straight through costs a day. West of UTC, local midnight on the 5th is
 * 04:00 or 08:00 UTC on the 5th, which rounds back to the 4th when the picker opens; east
 * of UTC the same arithmetic runs the other way. The failure is a day out, only for some
 * users, only in some months, which is the kind that survives a demo and arrives later as
 * "the app changed my date".
 *
 * <p>So the conversion is explicit, and it is here rather than in the Fragment because it
 * is arithmetic and can be tested without a device.
 */
public final class PickerDates {

    private PickerDates() {
    }

    /** Local-midnight millis to the UTC-midnight millis the picker wants to open on. */
    public static long toPickerUtc(long localMillis, ZoneId zone) {
        LocalDate date = Instant.ofEpochMilli(localMillis).atZone(zone).toLocalDate();
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    /** The picker's UTC-midnight selection back to local midnight on the day shown. */
    public static long fromPickerUtc(long utcMillis, ZoneId zone) {
        LocalDate date = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate();
        return date.atStartOfDay(zone).toInstant().toEpochMilli();
    }
}
