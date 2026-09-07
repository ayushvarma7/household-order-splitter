package com.householdsplitter.data.entity;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Money one member actually handed to another to settle up.
 *
 * <p>Without this the running balances are advice that goes stale the moment anybody
 * transfers anything: the app would keep asking for money that had already been paid.
 *
 * <p>Neither member foreign key cascades. SPEC 5.10 archives a member who has history
 * rather than deleting them, and a payment is history: deleting the row would rewrite what
 * the household actually did.
 */
@Entity(
        tableName = "settlement_payments",
        foreignKeys = {
                @ForeignKey(
                        entity = Household.class,
                        parentColumns = "id",
                        childColumns = "householdId",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(
                        entity = Member.class,
                        parentColumns = "id",
                        childColumns = "fromMemberId",
                        onDelete = ForeignKey.NO_ACTION),
                @ForeignKey(
                        entity = Member.class,
                        parentColumns = "id",
                        childColumns = "toMemberId",
                        onDelete = ForeignKey.NO_ACTION)
        },
        indices = {
                @Index("householdId"),
                @Index("fromMemberId"),
                @Index("toMemberId")
        })
public class SettlementPayment {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long householdId;

    /** Who handed the money over. */
    public long fromMemberId;

    /** Who received it. */
    public long toMemberId;

    /** Always positive; the direction is carried by the two member ids. */
    public long amountCents;

    public long paidAt;

    @Nullable
    public String note;

    public SettlementPayment() {
    }

    public SettlementPayment(long householdId, long fromMemberId, long toMemberId,
                             long amountCents, long paidAt) {
        this.householdId = householdId;
        this.fromMemberId = fromMemberId;
        this.toMemberId = toMemberId;
        this.amountCents = amountCents;
        this.paidAt = paidAt;
    }
}
