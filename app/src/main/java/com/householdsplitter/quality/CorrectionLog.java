package com.householdsplitter.quality;

import com.householdsplitter.core.parse.StoreKind;
import com.householdsplitter.data.dao.CorrectionEventDao;
import com.householdsplitter.data.dao.LineItemDao;
import com.householdsplitter.data.dao.OrderDao;
import com.householdsplitter.data.entity.CorrectionEvent;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.util.AppExecutors;
import com.householdsplitter.util.Callback;

/**
 * Writes down what the reader got wrong, as it happens.
 *
 * <p>The scorecard already counts corrections and can say the reader is right ninety-four
 * percent of the time. That number has never once suggested a fix. Every improvement to
 * this parser so far came from a single receipt somebody looked at: a page photographed
 * sideways, a description printed above its figures, a column of amounts sitting a line
 * high. What was missing was a record of those moments at the time they happened, instead
 * of a percentage afterwards.
 *
 * <p>So each entry carries what an experiment log carries: the prediction, the observation,
 * and enough context to go back to the page. What the reader said, what it should have
 * said, which store's vocabulary produced it, and which image it was on.
 *
 * <p>Recording is silent and never blocks. A correction that fails to log is a lost
 * observation, which is a shame; a correction that fails to save because logging threw is a
 * bug in the thing the user was actually doing.
 */
public class CorrectionLog {

    private final CorrectionEventDao dao;
    private final OrderDao orderDao;
    private final LineItemDao lineItemDao;
    private final AppExecutors executors;

    public CorrectionLog(CorrectionEventDao dao, OrderDao orderDao, LineItemDao lineItemDao,
                         AppExecutors executors) {
        this.dao = dao;
        this.orderDao = orderDao;
        this.lineItemDao = lineItemDao;
        this.executors = executors;
    }

    /**
     * Records an edit, if it changed anything worth recording.
     *
     * <p>Compares against what the reader originally produced rather than against the row's
     * previous state, because that is the prediction being scored. A name corrected twice
     * leaves two entries, both measured from the same starting point, which is what makes
     * the second one legible: the first correction was also wrong.
     *
     * @param edited the row as the user saved it
     */
    public void recordEdit(LineItem edited, Callback<Long> onRecorded) {
        if (edited == null || edited.id == 0L) {
            deliver(onRecorded, 0L);
            return;
        }
        executors.diskIO().execute(() -> {
            try {
                String kind = kindOf(edited);
                if (kind == null) {
                    deliver(onRecorded, 0L);
                    return;
                }
                deliver(onRecorded, write(edited, kind));
            } catch (RuntimeException loggingIsNeverFatal) {
                deliver(onRecorded, 0L);
            }
        });
    }

    /** Records a row the user typed in because the reader did not find it at all. */
    public void recordAdded(long lineItemId, Callback<Long> onRecorded) {
        executors.diskIO().execute(() -> {
            try {
                LineItem item = lineItemDao.getByIdSync(lineItemId);
                if (item == null) {
                    deliver(onRecorded, 0L);
                    return;
                }
                deliver(onRecorded, write(item, CorrectionEvent.ADDED_BY_HAND));
            } catch (RuntimeException loggingIsNeverFatal) {
                deliver(onRecorded, 0L);
            }
        });
    }

    /**
     * Records a row the user deleted.
     *
     * <p>Worth as much as a missed row and easier to act on: a row that was read, charged
     * for and then removed is a charge the reader invented, which is the failure mode the
     * whole parser is arranged to avoid.
     */
    public void recordDeleted(LineItem deleted) {
        if (deleted == null || deleted.origin != com.householdsplitter.core.quality.ItemOrigin.PARSED) {
            return;
        }
        executors.diskIO().execute(() -> {
            try {
                write(deleted, CorrectionEvent.DELETED_AS_WRONG);
            } catch (RuntimeException loggingIsNeverFatal) {
                // Nothing to do and nothing to tell the user.
            }
        });
    }

    /**
     * Attaches what the user said about an entry: which image, and what went wrong.
     *
     * <p>Separate from recording it, because the correction has to save whether or not the
     * user wants to answer any questions about it. The entry exists either way; this only
     * fills in what they chose to add.
     */
    public void annotate(long eventId, int imageIndex, String reason) {
        if (eventId == 0L) {
            return;
        }
        executors.diskIO().execute(() -> {
            try {
                CorrectionEvent event = dao.byIdSync(eventId);
                if (event == null) {
                    return;
                }
                event.imageIndex = imageIndex;
                event.reason = reason;
                dao.update(event);
            } catch (RuntimeException loggingIsNeverFatal) {
                // As above.
            }
        });
    }

    /** Which kind of mistake this edit represents, or null when it changed nothing. */
    private static String kindOf(LineItem item) {
        if (item.origin != com.householdsplitter.core.quality.ItemOrigin.PARSED) {
            return null;
        }
        boolean nameChanged = item.parsedName != null && !item.parsedName.equals(item.name);
        boolean amountChanged = item.parsedCents != 0L && item.parsedCents != item.lineTotalCents;
        // Amount first: a row whose figure was wrong is a worse failure than one whose name
        // was, and an entry records the most serious thing that happened to it.
        if (amountChanged) {
            return CorrectionEvent.AMOUNT_CORRECTED;
        }
        if (nameChanged) {
            return CorrectionEvent.NAME_CORRECTED;
        }
        return null;
    }

    private long write(LineItem item, String kind) {
        Order order = orderDao.getByIdSync(item.orderId);
        CorrectionEvent event = new CorrectionEvent();
        event.householdId = order == null ? 0L : order.householdId;
        event.orderId = item.orderId;
        event.lineItemId = item.id;
        event.store = order == null || order.store == null
                ? StoreKind.WALMART.name() : order.store.name();
        event.kind = kind;
        event.parsedName = item.parsedName;
        event.parsedCents = item.parsedCents;
        event.finalName = item.name;
        event.finalCents = item.lineTotalCents;
        event.finalQuantity = item.quantity;
        event.imageIndex = item.sourceImageIndex;
        event.missVerdict = item.missVerdict;
        event.createdAt = System.currentTimeMillis();
        return dao.insert(event);
    }

    private void deliver(Callback<Long> callback, long value) {
        if (callback == null) {
            return;
        }
        executors.mainThread().execute(() -> callback.onResult(value));
    }
}
