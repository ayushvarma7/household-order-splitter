package com.householdsplitter.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One thing the reader got wrong, and what it should have said.
 *
 * <p>The app already counted corrections: the scorecard knows how many rows were kept, how
 * many were edited and how many were typed in by hand. A count tells you the reader is
 * wrong six percent of the time and nothing whatever about how to make it right.
 *
 * <p>This is the other half. Each row here is one observation with everything needed to
 * reproduce it: what was read, what it should have been, which image it was on, and, when
 * the user says so, which kind of mistake it was. That is the shape an experiment log
 * takes, and the reason for it is that every parser fix so far has come from a single real
 * receipt rather than from a percentage.
 *
 * <p>Append only. A correction is an observation and observations are not edited, so the
 * same row corrected twice leaves two entries and the order of them is the story.
 *
 * <p>No foreign key to the line item. A row deleted as a phantom is one of the most useful
 * observations here and must outlive what it refers to; the ids are kept as plain numbers
 * so nothing cascades. The order is kept the same way, so clearing an order's data does not
 * quietly erase what was learned from it.
 */
@Entity(
        tableName = "correction_events",
        indices = {@Index("householdId"), @Index("orderId"), @Index("createdAt")})
public class CorrectionEvent {

    /** What kind of mistake this was. */
    public static final String ADDED_BY_HAND = "ADDED_BY_HAND";
    public static final String NAME_CORRECTED = "NAME_CORRECTED";
    public static final String AMOUNT_CORRECTED = "AMOUNT_CORRECTED";
    public static final String QUANTITY_CORRECTED = "QUANTITY_CORRECTED";
    public static final String DELETED_AS_WRONG = "DELETED_AS_WRONG";

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long householdId;

    public long orderId;

    /** The row this was about, or 0 once it has been deleted. Not a foreign key. */
    public long lineItemId;

    /** Which store's reader produced it, so a pattern can be traced to one vocabulary. */
    public String store;

    @NonNull
    public String kind = ADDED_BY_HAND;

    /** What the reader said. Empty and zero for a row that was never read at all. */
    public String parsedName;

    public long parsedCents;

    /** What it should have said. */
    public String finalName;

    public long finalCents;

    public int parsedQuantity;

    public int finalQuantity;

    /**
     * Which image the user says this was on, or -1 when they did not say.
     *
     * <p>Asked rather than inferred. A row the reader never produced has no position on any
     * page to infer from, and that is exactly the row worth asking about: knowing which of
     * four screenshots a missed item was on turns "something was missed" into a page that
     * can be re-read and a stage that can be named.
     */
    public int imageIndex = -1;

    /** What the user said went wrong, in their words or from a short list. Optional. */
    public String reason;

    /** Filled in later by the diagnosis, once the images have been re-read. */
    public String missVerdict;

    public long createdAt;

    public CorrectionEvent() {
    }

    /** True when the figures say the reader had this row's money right. */
    public boolean moneyWasRight() {
        return parsedCents == finalCents;
    }

    /** True when the figures say the reader had this row's name right. */
    public boolean nameWasRight() {
        return parsedName != null && parsedName.equals(finalName);
    }
}
