package com.householdsplitter.data.converter;

import androidx.room.TypeConverter;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.parse.StoreKind;
import com.householdsplitter.core.quality.ItemOrigin;
import com.householdsplitter.data.entity.DraftStep;
import com.householdsplitter.data.entity.MemberRule;
import com.householdsplitter.data.entity.OrderStatus;

/**
 * Enums are stored as their names rather than their ordinals, so reordering an enum can
 * never silently reinterpret existing rows.
 */
public class Converters {

    @TypeConverter
    public static String fromRuleKind(MemberRule.Kind value) {
        return value == null ? MemberRule.Kind.EXCLUDE.name() : value.name();
    }

    @TypeConverter
    public static MemberRule.Kind toRuleKind(String value) {
        if (value == null) {
            return MemberRule.Kind.EXCLUDE;
        }
        try {
            return MemberRule.Kind.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            return MemberRule.Kind.EXCLUDE;
        }
    }

    @TypeConverter
    public static String fromScope(Scope value) {
        return value == null ? Scope.UNASSIGNED.name() : value.name();
    }

    @TypeConverter
    public static Scope toScope(String value) {
        if (value == null) {
            return Scope.UNASSIGNED;
        }
        try {
            return Scope.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            return Scope.UNASSIGNED;
        }
    }

    @TypeConverter
    public static String fromOrderStatus(OrderStatus value) {
        return value == null ? OrderStatus.DRAFT.name() : value.name();
    }

    @TypeConverter
    public static OrderStatus toOrderStatus(String value) {
        if (value == null) {
            return OrderStatus.DRAFT;
        }
        try {
            return OrderStatus.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            return OrderStatus.DRAFT;
        }
    }

    @TypeConverter
    public static String fromDraftStep(DraftStep value) {
        return value == null ? DraftStep.IMPORT.name() : value.name();
    }

    @TypeConverter
    public static DraftStep toDraftStep(String value) {
        if (value == null) {
            return DraftStep.IMPORT;
        }
        try {
            return DraftStep.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            return DraftStep.IMPORT;
        }
    }

    /**
     * Walmart is the fallback for an unreadable value, because every order that predates
     * this column was a Walmart order.
     */
    @TypeConverter
    public static String fromStoreKind(StoreKind value) {
        return value == null ? StoreKind.WALMART.name() : value.name();
    }

    @TypeConverter
    public static StoreKind toStoreKind(String value) {
        return StoreKind.fromName(value);
    }

    /**
     * PARSED is the fallback, because every row that predates this column was produced by
     * the reader. Reading an unknown value as MANUAL would credit the reader for rows it
     * never had to get right.
     */
    @TypeConverter
    public static String fromItemOrigin(ItemOrigin value) {
        return value == null ? ItemOrigin.PARSED.name() : value.name();
    }

    @TypeConverter
    public static ItemOrigin toItemOrigin(String value) {
        if (value == null) {
            return ItemOrigin.PARSED;
        }
        try {
            return ItemOrigin.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            return ItemOrigin.PARSED;
        }
    }
}
