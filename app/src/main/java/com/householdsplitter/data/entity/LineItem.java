package com.householdsplitter.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.householdsplitter.core.calc.Scope;

/** SPEC 5.6. One row of the order. */
@Entity(
        tableName = "line_items",
        foreignKeys = @ForeignKey(
                entity = Order.class,
                parentColumns = "id",
                childColumns = "orderId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("orderId"), @Index({"orderId", "position"})})
public class LineItem {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long orderId;

    /** SPEC 7.6.5: editable. */
    @NonNull
    public String name = "";

    /** SPEC 5.6: preserved verbatim for debugging, shown read-only in the edit sheet. */
    @NonNull
    public String rawOcrText = "";

    @ColumnInfo(defaultValue = "1")
    public int quantity = 1;

    /** The charged amount for the row, in cents (SPEC 6.1). */
    public long lineTotalCents;

    /** SPEC 8.3.3: display only, never charged. */
    @Nullable
    public String unitPriceText;

    /** SPEC 2.6, plus UNASSIGNED for a row nobody has answered for yet. */
    @NonNull
    @ColumnInfo(defaultValue = "UNASSIGNED")
    public Scope scope = Scope.UNASSIGNED;

    /** SPEC 8.5.2, e.g. "shopped" or "substituted". */
    @Nullable
    public String sourceSection;

    /** SPEC 8.8. */
    @ColumnInfo(defaultValue = "0")
    public boolean needsReview;

    /**
     * SPEC 8.8.2 requires every flagged row to state its reason, and SPEC 5.6 stores only
     * the boolean, so the reasons are kept here as a comma-separated list of enum names.
     */
    @Nullable
    public String reviewReasonsCsv;

    /** SPEC 7.6.3: display order. */
    public int position;

    /**
     * Which screenshot this row was read from, and where on it, so the app can show the
     * user the page the money came from with the row ringed.
     *
     * <p>The index is into the order's own images, ordered as the user arranged them. -1
     * means unknown, which is the case for a row typed by hand or added as a difference.
     *
     * <p>The box is in permille of the image, matching the parser's geometry, because a
     * pixel box measured on a 1080-wide capture means nothing when the same picture is
     * displayed at another size.
     */
    @ColumnInfo(defaultValue = "-1")
    public int sourceImageIndex = -1;

    @ColumnInfo(defaultValue = "0")
    public int boundsLeftPermille;

    @ColumnInfo(defaultValue = "0")
    public int boundsTopPermille;

    @ColumnInfo(defaultValue = "0")
    public int boundsRightPermille;

    @ColumnInfo(defaultValue = "0")
    public int boundsBottomPermille;

    /** True when there is a screenshot region worth offering to show. */
    public boolean hasSourceRegion() {
        return sourceImageIndex >= 0
                && boundsRightPermille > boundsLeftPermille
                && boundsBottomPermille > boundsTopPermille;
    }

    public LineItem() {
    }

    /**
     * SPEC 7.6.9: Continue is blocked while a row has no name or no price.
     *
     * <p>A row the parser could not name carries a placeholder rather than an empty string,
     * because losing the charge entirely would leave the totals short with nothing to point
     * at. The placeholder is not a name, so it blocks here too: an unidentified charge is
     * worse to split than an unpriced one.
     */
    public boolean isReadyForSplitting() {
        String trimmed = name.trim();
        return !trimmed.isEmpty()
                && !trimmed.equals(com.householdsplitter.core.parse.model.ParsedItem.NAME_NOT_READ)
                && lineTotalCents != 0L;
    }
}
