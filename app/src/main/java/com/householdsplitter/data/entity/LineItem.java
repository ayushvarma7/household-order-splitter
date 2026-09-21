package com.householdsplitter.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;

import com.householdsplitter.core.quality.ItemOrigin;
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
    /**
     * Where this row came from, so the reader is only ever judged on its own output.
     *
     * <p>Defaults to PARSED because every row that existed before this column did was
     * produced by the reader: hand-added rows were not distinguishable then, which is
     * precisely the gap this closes.
     */
    @NonNull
    @ColumnInfo(defaultValue = "PARSED")
    public ItemOrigin origin = ItemOrigin.PARSED;

    /**
     * What the reader originally called this row, kept so that "did the user change it?"
     * stays a comparison rather than a sticky flag.
     *
     * <p>A flag set on first edit would count a row the user changed and changed back as a
     * parser failure forever. Holding the original means the answer is recomputed from
     * what is actually there, and a row edited back to what the reader said counts as
     * correct again, because it is.
     */
    @Nullable
    public String parsedName;

    @ColumnInfo(defaultValue = "0")
    public long parsedCents;

    /**
     * Why the reader missed this row, worked out by re-reading the screenshots when the
     * user typed it in. Null until diagnosed, and null forever on a row the reader found.
     *
     * <p>Recorded at the moment of the manual add rather than derived later, because the
     * screenshots are referenced by URI rather than copied, and a URI dies when the user
     * deletes the picture from their gallery. The one moment it is certainly readable is
     * while the user is still standing in front of the order.
     */
    @Nullable
    public String missVerdict;

    /** True when the reader produced this row and it still says what the reader said. */
    /**
     * A detached duplicate of this row, for a screen that is about to edit it.
     *
     * <p>The edit sheet used to write straight into the object the list adapter was
     * holding, which looked correct and was not. The list is diffed by content, so once
     * the object in the old list had already been given the new name, the row that came
     * back from the database compared equal to it and the view was never rebound: the name
     * was saved and the screen went on showing the old one until something else forced a
     * redraw. Editing a copy leaves the old list genuinely old, so the diff sees a change.
     *
     * <p>Every field, including the ones no editor touches. A partial copy would save a
     * row and quietly blank whatever it forgot, and there is a test that walks the fields
     * by reflection so that adding one to this class fails loudly here.
     */
    public LineItem copy() {
        LineItem copy = new LineItem();
        copy.id = id;
        copy.orderId = orderId;
        copy.name = name;
        copy.rawOcrText = rawOcrText;
        copy.quantity = quantity;
        copy.lineTotalCents = lineTotalCents;
        copy.unitPriceText = unitPriceText;
        copy.scope = scope;
        copy.sourceSection = sourceSection;
        copy.needsReview = needsReview;
        copy.reviewReasonsCsv = reviewReasonsCsv;
        copy.position = position;
        copy.sourceImageIndex = sourceImageIndex;
        copy.boundsLeftPermille = boundsLeftPermille;
        copy.boundsTopPermille = boundsTopPermille;
        copy.boundsRightPermille = boundsRightPermille;
        copy.boundsBottomPermille = boundsBottomPermille;
        copy.origin = origin;
        copy.parsedName = parsedName;
        copy.parsedCents = parsedCents;
        copy.missVerdict = missVerdict;
        return copy;
    }

    public boolean matchesWhatWasRead() {
        if (origin != ItemOrigin.PARSED) {
            return false;
        }
        String original = parsedName == null ? "" : parsedName;
        return lineTotalCents == parsedCents && original.equals(name == null ? "" : name);
    }

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
