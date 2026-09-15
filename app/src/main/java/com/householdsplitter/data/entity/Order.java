package com.householdsplitter.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;

import com.householdsplitter.core.parse.StoreKind;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * SPEC 5.3. One Walmart shop.
 *
 * <p>The table is called {@code orders} because {@code order} is reserved in SQL.
 *
 * <p>Every money column is {@code long} cents (SPEC 6.1). The unique index on
 * {@code externalOrderNo} is exactly the "unique where non-null" the spec asks for, because
 * SQLite permits any number of NULLs in a unique index; it backs the duplicate-import
 * guard of SPEC 8.6.7.
 */
@Entity(
        tableName = "orders",
        foreignKeys = {
                @ForeignKey(
                        entity = Household.class,
                        parentColumns = "id",
                        childColumns = "householdId",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(
                        entity = Member.class,
                        parentColumns = "id",
                        childColumns = "payerMemberId",
                        onDelete = ForeignKey.SET_NULL)
        },
        indices = {
                @Index("householdId"),
                @Index("payerMemberId"),
                @Index(value = "externalOrderNo", unique = true)
        })
public class Order {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long householdId;

    /** SPEC 8.6.2 gives the default; SPEC 7.7.1 makes it editable. */
    @NonNull
    public String label = "";

    public long orderDate;

    /** SPEC 8.6.6. */
    @Nullable
    public String externalOrderNo;

    @NonNull
    public OrderStatus status = OrderStatus.DRAFT;

    /**
     * Which store's pages this order was read from.
     *
     * <p>Recorded rather than inferred, and it defaults to Walmart because every order that
     * existed before this column did was a Walmart order. Two things need it: the orders
     * list, which shows the store so a household can tell two same-day orders apart, and a
     * re-parse, which must use the vocabulary the order was read with rather than whichever
     * store happens to be the default later.
     */
    @NonNull
    @ColumnInfo(defaultValue = "WALMART")
    public StoreKind store = StoreKind.WALMART;

    /** SPEC 7.10.4: who fronted the money. */
    @Nullable
    public Long payerMemberId;

    @ColumnInfo(defaultValue = "0")
    public long taxCents;

    @ColumnInfo(defaultValue = "0")
    public long deliveryFeeCents;

    @ColumnInfo(defaultValue = "0")
    public long tipCents;

    @ColumnInfo(defaultValue = "0")
    public long otherFeeCents;

    /** SPEC 5.3: stored positive, applied negative (SPEC 6.4.4). */
    @ColumnInfo(defaultValue = "0")
    public long discountCents;

    @ColumnInfo(defaultValue = "0")
    public long statedSubtotalCents;

    @ColumnInfo(defaultValue = "0")
    public long statedTotalCents;

    public long createdAt;

    /** SPEC 7.3.4 and 7.9.14. */
    @NonNull
    @ColumnInfo(defaultValue = "IMPORT")
    public DraftStep draftStep = DraftStep.IMPORT;

    /** SPEC 7.9.14: the item the assignment loop was on. */
    @ColumnInfo(defaultValue = "0")
    public int draftItemPosition;

    /**
     * The number printed on "N items delivered", or -1.
     *
     * <p>A hint, never a check. SPEC 8.5.4 says it counts units and not rows, and SPEC 8.9.5
     * says reconciliation is always on money. It is kept so that a user whose rows plainly
     * do not add up to the bill can be told the order mentions more units than they have
     * rows, which points at a missing screenshot.
     */
    @ColumnInfo(defaultValue = "-1")
    public int deliveredUnitCount = -1;

    /**
     * SPEC 7.7.2: which order-level fields genuinely came off a screenshot, so the
     * "from screenshot" marker can be shown and then dropped once the user edits.
     * Stored as a comma-separated list of OrderField names.
     */
    @Nullable
    public String parsedFieldsCsv;

    public Order() {
    }

    /** Everything that is not a line item (SPEC 2.10). */
    public long adjustmentsTotalCents() {
        return taxCents + deliveryFeeCents + tipCents + otherFeeCents;
    }

    public boolean isEditable() {
        return status != OrderStatus.SETTLED;
    }
}
