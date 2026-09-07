package com.householdsplitter.data.repo;

import androidx.lifecycle.LiveData;

import com.householdsplitter.core.analytics.Balances;
import com.householdsplitter.data.dao.SettlementDao;
import com.householdsplitter.data.entity.SettlementPayment;
import com.householdsplitter.util.AppExecutors;
import com.householdsplitter.util.Callback;
import com.householdsplitter.util.Result;

import java.util.ArrayList;
import java.util.List;

/** The record of money actually handed over between members. */
public class SettlementRepository {

    private final SettlementDao dao;
    private final AppExecutors executors;

    public SettlementRepository(SettlementDao dao, AppExecutors executors) {
        this.dao = dao;
        this.executors = executors;
    }

    public LiveData<List<SettlementPayment>> observe(long householdId) {
        return dao.observeForHousehold(householdId);
    }

    /** Records a payment. Amounts are always positive; the direction is the two members. */
    public void record(long householdId, long fromMemberId, long toMemberId, long amountCents,
                       Callback<Result<Long>> callback) {
        executors.diskIO().execute(() -> {
            if (amountCents <= 0L) {
                post(callback, Result.failure("A payment has to be more than nothing"));
                return;
            }
            if (fromMemberId == toMemberId) {
                post(callback, Result.failure("Paying yourself settles nothing"));
                return;
            }
            long id = dao.insert(new SettlementPayment(householdId, fromMemberId, toMemberId,
                    amountCents, System.currentTimeMillis()));
            post(callback, Result.ok(id));
        });
    }

    /** Undoes a payment that was recorded by mistake. */
    public void delete(long paymentId, Callback<Result<Void>> callback) {
        executors.diskIO().execute(() -> {
            dao.deleteById(paymentId);
            post(callback, Result.ok(null));
        });
    }

    /** The payments as the balance calculation wants them. */
    public void paymentsForBalances(long householdId, Callback<List<Balances.Payment>> callback) {
        executors.diskIO().execute(() -> {
            List<Balances.Payment> payments = new ArrayList<>();
            for (SettlementPayment payment : dao.getForHouseholdSync(householdId)) {
                payments.add(new Balances.Payment(
                        payment.fromMemberId, payment.toMemberId, payment.amountCents));
            }
            post(callback, payments);
        });
    }

    public void paymentsSync(long householdId, Callback<List<SettlementPayment>> callback) {
        executors.diskIO().execute(() -> post(callback, dao.getForHouseholdSync(householdId)));
    }

    private <T> void post(Callback<T> callback, T value) {
        if (callback != null) {
            executors.mainThread().execute(() -> callback.onResult(value));
        }
    }
}
