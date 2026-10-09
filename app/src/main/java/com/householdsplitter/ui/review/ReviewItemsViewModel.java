package com.householdsplitter.ui.review;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.householdsplitter.core.parse.model.ParsedAdjustments;
import com.householdsplitter.core.parse.Reconciler;
import com.householdsplitter.core.parse.model.Reconciliation;
import com.householdsplitter.core.quality.ItemOrigin;
import com.householdsplitter.data.entity.CorrectionEvent;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.quality.MissDiagnosisService;
import com.householdsplitter.data.relation.LineItemWithAssignments;
import com.householdsplitter.data.relation.OrderBundle;
import com.householdsplitter.data.repo.OrderRepository;

import java.util.ArrayList;
import java.util.List;

/** S6. SPEC 7.6. */
public class ReviewItemsViewModel extends ViewModel {

    private final OrderRepository repository;
    private final long orderId;
    private final LiveData<OrderBundle> bundle;
    private final MutableLiveData<LineItem> lastDeleted = new MutableLiveData<>();

    /** Optional: without it, rows are still recorded as hand-typed, just not diagnosed. */
    private final MissDiagnosisService diagnosis;
    private final com.householdsplitter.quality.CorrectionLog corrections;

    public ReviewItemsViewModel(OrderRepository repository, long orderId) {
        this(repository, null, orderId);
    }

    public ReviewItemsViewModel(OrderRepository repository, MissDiagnosisService diagnosis,
                                long orderId) {
        this(repository, diagnosis, null, orderId);
    }

    public ReviewItemsViewModel(OrderRepository repository, MissDiagnosisService diagnosis,
                                com.householdsplitter.quality.CorrectionLog corrections,
                                long orderId) {
        this.diagnosis = diagnosis;
        this.corrections = corrections;
        this.repository = repository;
        this.orderId = orderId;
        this.bundle = repository.observeBundle(orderId);
    }

    /**
     * The entry just written, so the screen can offer to annotate it.
     *
     * <p>Carries the id and what kind of correction it was, because the question worth
     * asking differs: a row typed in by hand wants to know which image it was missed from,
     * and a row whose price was corrected does not, since the reader already knows where
     * that one was.
     */
    public static final class Logged {

        public final long eventId;
        public final String kind;
        public final String name;

        Logged(long eventId, String kind, String name) {
            this.eventId = eventId;
            this.kind = kind;
            this.name = name;
        }
    }

    private final androidx.lifecycle.MutableLiveData<Logged> lastLogged =
            new androidx.lifecycle.MutableLiveData<>();

    /** Set once after a correction is recorded, then cleared when the screen has shown it. */
    public LiveData<Logged> lastLogged() {
        return lastLogged;
    }

    public void clearLastLogged() {
        lastLogged.setValue(null);
    }

    public long orderId() {
        return orderId;
    }

    public LiveData<OrderBundle> bundle() {
        return bundle;
    }

    public LiveData<List<LineItem>> items() {
        return Transformations.map(bundle, value -> {
            List<LineItem> items = new ArrayList<>();
            if (value != null) {
                for (LineItemWithAssignments row : value.items) {
                    items.add(row.item);
                }
            }
            return items;
        });
    }

    /** SPEC 7.6.2 and 8.9: the header banner reports the reconciliation state. */
    public LiveData<Reconciliation> reconciliation() {
        return Transformations.map(bundle, value -> {
            if (value == null) {
                return null;
            }
            long itemsSubtotal = 0L;
            for (LineItemWithAssignments row : value.items) {
                if (row.item.scope.isChargeable() || row.item.scope.isUnanswered()) {
                    itemsSubtotal += row.item.lineTotalCents;
                }
            }
            ParsedAdjustments adjustments = new ParsedAdjustments(
                    value.order.statedSubtotalCents,
                    value.order.taxCents,
                    value.order.deliveryFeeCents,
                    value.order.tipCents,
                    value.order.otherFeeCents,
                    value.order.discountCents,
                    value.order.statedTotalCents);
            return Reconciler.reconcile(itemsSubtotal, adjustments);
        });
    }

    /** SPEC 7.6.9: the offending rows, so they can be scrolled into view. */
    public List<Integer> blockingPositions() {
        List<Integer> positions = new ArrayList<>();
        OrderBundle value = bundle.getValue();
        if (value == null) {
            return positions;
        }
        for (int i = 0; i < value.items.size(); i++) {
            if (!value.items.get(i).item.isReadyForSplitting()) {
                positions.add(i);
            }
        }
        return positions;
    }

    public void save(LineItem item) {
        final boolean isNewAndTypedByHand = item.id == 0L && item.origin == ItemOrigin.MANUAL;
        final String name = item.name;
        repository.saveItem(item, result -> {
            // Asked now rather than later: the screenshots are referenced by URI, and a URI
            // stops resolving once the picture leaves the gallery. This is the moment they
            // are certainly readable.
            if (isNewAndTypedByHand && diagnosis != null && item.id != 0L) {
                diagnosis.diagnose(item.id);
            }
            if (corrections == null || item.id == 0L) {
                return;
            }
            if (isNewAndTypedByHand) {
                corrections.recordAdded(item.id, eventId -> {
                    if (eventId != 0L) {
                        lastLogged.setValue(new Logged(eventId,
                                CorrectionEvent.ADDED_BY_HAND, name));
                    }
                });
                return;
            }
            corrections.recordEdit(item, eventId -> {
                if (eventId != 0L) {
                    lastLogged.setValue(new Logged(eventId, kindOf(item), name));
                }
            });
        });
    }

    public void annotate(long eventId, int imageIndex, String reason) {
        if (corrections != null) {
            corrections.annotate(eventId, imageIndex, reason);
        }
    }

    /**
     * What the reader originally produced for the row behind this entry, for showing back.
     *
     * <p>Matched by the corrected name, which is what the entry carries, and read off the
     * row in hand rather than the log so the sheet opens without a database round trip.
     * Null for a row the reader never produced or got right, which is the case where there
     * is nothing worth showing back.
     */
    public String whatTheReaderSaid(String correctedName) {
        OrderBundle value = bundle.getValue();
        if (value == null || correctedName == null) {
            return null;
        }
        for (LineItemWithAssignments row : value.items) {
            if (!correctedName.equals(row.item.name)) {
                continue;
            }
            String read = row.item.parsedName;
            return read == null || read.isEmpty() || read.equals(row.item.name) ? null : read;
        }
        return null;
    }

    /** Mirrors what the log decided, so the prompt can word itself for the right mistake. */
    private static String kindOf(LineItem item) {
        boolean amountChanged = item.parsedCents != 0L && item.parsedCents != item.lineTotalCents;
        return amountChanged
                ? CorrectionEvent.AMOUNT_CORRECTED : CorrectionEvent.NAME_CORRECTED;
    }

    /** SPEC 7.6.8: delete with an Undo that really restores the row. */
    public void delete(LineItem item) {
        lastDeleted.setValue(item);
        // Recorded before the delete, while the row still exists to be read. A row the
        // reader produced and the user removed is a charge that was invented, which is the
        // most serious thing this parser can do and the most useful thing to have written
        // down.
        if (corrections != null) {
            corrections.recordDeleted(item);
        }
        repository.deleteItem(item, result -> {
        });
    }

    public void undoDelete() {
        LineItem item = lastDeleted.getValue();
        if (item != null) {
            repository.restoreItem(item, result -> {
            });
            lastDeleted.setValue(null);
        }
    }

    /**
     * How many units the bill mentioned, or -1.
     *
     * <p>A hint only (SPEC 8.5.4). Useful for saying "the order mentions 33 units and you
     * have 5 rows", which points at a missing screenshot.
     */
    public int deliveredUnitCount() {
        OrderBundle value = bundle.getValue();
        return value == null ? -1 : value.order.deliveredUnitCount;
    }

    public int itemCount() {
        OrderBundle value = bundle.getValue();
        return value == null ? 0 : value.items.size();
    }

    /** The money the rows do not account for, as a positive shortfall or negative excess. */
    public long unaccountedCents() {
        OrderBundle value = bundle.getValue();
        if (value == null) {
            return 0L;
        }
        long items = 0L;
        for (LineItemWithAssignments row : value.items) {
            if (row.item.scope.isChargeable() || row.item.scope.isUnanswered()) {
                items += row.item.lineTotalCents;
            }
        }
        long stated = value.order.statedSubtotalCents;
        return stated == 0L ? 0L : stated - items;
    }

    /** Books the shortfall as one row rather than leaving the user to hunt for it. */
    public void addDifferenceAsItem(String name, com.householdsplitter.util.Callback<Long> onDone) {
        repository.addDifferenceItem(orderId, unaccountedCents(), name,
                result -> onDone.onResult(result.isOk() ? result.value() : null));
    }

    /** SPEC 11.1: breaks a row of quantity N into N rows of equal price. */
    public void splitByQuantity(long lineItemId,
                                com.householdsplitter.util.Callback<Integer> onDone) {
        repository.splitByQuantity(lineItemId,
                result -> onDone.onResult(result.isOk() ? result.value() : 0));
    }

    /** SPEC 7.6.7: a blank row, opened straight into the edit sheet. */
    public LineItem newBlankItem() {
        LineItem item = new LineItem();
        item.orderId = orderId;
        // Typed by a person, so the reader is not judged on it and, if the text turns out
        // to be on a screenshot after all, it is a charge the reader missed.
        item.origin = ItemOrigin.MANUAL;
        // Started at whatever the rows are short of the printed subtotal.
        //
        // When the bill states a subtotal and the rows do not reach it, the amount of the
        // row about to be typed is not a guess: it is the difference, exactly. Filling it in
        // saves the commonest case a step, and where the shortfall is larger than one row it
        // is still a better starting figure than nothing, because it is visibly wrong and
        // gets corrected rather than silently accepted.
        //
        // Zero when the rows already add up, when the bill stated no subtotal, or when the
        // rows overshoot, which is a different problem and not one a new row solves.
        long shortfall = unaccountedCents();
        if (shortfall > 0L) {
            item.lineTotalCents = shortfall;
        }
        return item;
    }
}
