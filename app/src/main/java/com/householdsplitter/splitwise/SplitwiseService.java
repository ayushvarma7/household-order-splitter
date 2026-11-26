package com.householdsplitter.splitwise;

import android.content.Context;

import com.householdsplitter.core.calc.result.MemberSplit;
import com.householdsplitter.core.calc.result.SplitResult;
import com.householdsplitter.core.splitwise.ExpenseBuilder;
import com.householdsplitter.core.splitwise.ExpensePayload;
import com.householdsplitter.core.splitwise.SplitwiseClient;
import com.householdsplitter.core.splitwise.SplitwiseException;
import com.householdsplitter.core.splitwise.SplitwiseGroup;
import com.householdsplitter.core.splitwise.SplitwiseUser;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.util.AppExecutors;
import com.householdsplitter.util.Callback;
import com.householdsplitter.util.Result;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Connects to Splitwise and pushes a split order as one expense.
 *
 * <p>Network work runs on the disk executor and every callback lands on the main thread.
 * The connected person is the payer: the app records who fronted the money per order, but
 * the key owner is the only account Splitwise will let this app act as.
 */
public final class SplitwiseService {

    private final SplitwiseStore store;
    private final AppExecutors executors;
    private final SplitwiseClient client;

    public SplitwiseService(Context context, AppExecutors executors) {
        this.store = new SplitwiseStore(context);
        this.executors = executors;
        this.client = new SplitwiseClient(new HttpSplitwiseTransport(store::apiKey));
    }

    public boolean isConnected() {
        return store.isConnected();
    }

    public String connectedName() {
        return store.userName();
    }

    /** Checks the pasted key against Splitwise and, if it works, stores it sealed. */
    public void connect(String apiKey, Callback<Result<SplitwiseUser>> callback) {
        String trimmed = apiKey == null ? "" : apiKey.trim();
        if (trimmed.isEmpty()) {
            callback.onResult(Result.failure("Paste your Splitwise API key first."));
            return;
        }
        executors.diskIO().execute(() -> {
            Result<SplitwiseUser> outcome;
            try {
                // A throwaway client so the key is tested before anything is stored.
                SplitwiseUser user = new SplitwiseClient(new HttpSplitwiseTransport(() -> trimmed))
                        .currentUser();
                outcome = store.saveConnection(trimmed, user.id(), user.displayName(),
                        user.defaultCurrency())
                        ? Result.ok(user) : Result.failure("Could not store the key securely.");
            } catch (SplitwiseException failure) {
                outcome = Result.failure(failure.getMessage());
            }
            deliver(callback, outcome);
        });
    }

    public void disconnect() {
        store.clear();
    }

    public void loadGroups(Callback<Result<List<SplitwiseGroup>>> callback) {
        executors.diskIO().execute(() -> {
            Result<List<SplitwiseGroup>> outcome;
            try {
                outcome = Result.ok(client.groups());
            } catch (SplitwiseException failure) {
                outcome = Result.failure(failure.getMessage());
            }
            deliver(callback, outcome);
        });
    }

    public long linkedGroupId(long householdId) {
        return store.groupId(householdId);
    }

    public String linkedGroupName(long householdId) {
        return store.groupName(householdId);
    }

    public long linkedUser(long memberId) {
        return store.memberUser(memberId);
    }

    public void saveLink(long householdId, SplitwiseGroup group, long[] memberIds, long[] userIds) {
        store.saveGroup(householdId, group.id(), group.name());
        for (int i = 0; i < memberIds.length; i++) {
            store.saveMemberUser(memberIds[i], userIds[i]);
        }
    }

    /** The Splitwise expense an order was sent as, or 0 if it never was. */
    public long sentExpenseId(long orderId) {
        return store.expenseId(orderId);
    }

    public long sentAt(long orderId) {
        return store.sentAt(orderId);
    }

    /**
     * Sends the order. First time it creates the expense; with {@code replace} it updates the
     * one already sent, so a corrected split overwrites the old figures instead of adding a
     * second expense.
     */
    public void send(Order order, SplitResult split, boolean replace, Callback<Result<Long>> callback) {
        List<ExpenseBuilder.Share> shares = new ArrayList<>();
        for (MemberSplit member : split.members()) {
            long user = store.memberUser(member.memberId());
            if (user == 0L) {
                callback.onResult(Result.failure(member.memberName() + " is not linked to a Splitwise person."));
                return;
            }
            shares.add(new ExpenseBuilder.Share(user, member.finalCents()));
        }
        long group = store.groupId(order.householdId);
        if (group == 0L) {
            callback.onResult(Result.failure("Link this household to a Splitwise group first."));
            return;
        }
        ExpensePayload payload;
        try {
            payload = ExpenseBuilder.build(order.label, "Sent from Household Order Splitter",
                    isoDate(order.orderDate), store.currency(), group, store.userId(), shares);
        } catch (IllegalArgumentException invalid) {
            callback.onResult(Result.failure(invalid.getMessage()));
            return;
        }
        long previous = replace ? store.expenseId(order.id) : 0L;
        long orderId = order.id;
        executors.diskIO().execute(() -> {
            Result<Long> outcome;
            try {
                long id = previous != 0L ? client.updateExpense(previous, payload)
                        : client.createExpense(payload);
                store.saveSent(orderId, id, System.currentTimeMillis());
                outcome = Result.ok(id);
            } catch (SplitwiseException failure) {
                outcome = Result.failure(failure.getMessage());
            }
            deliver(callback, outcome);
        });
    }

    private static String isoDate(long millis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(millis));
    }

    private <T> void deliver(Callback<T> callback, T value) {
        executors.mainThread().execute(() -> callback.onResult(value));
    }
}
