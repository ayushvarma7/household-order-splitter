package com.householdsplitter.data.repo;

import androidx.lifecycle.LiveData;

import com.householdsplitter.core.calc.AllocationMode;
import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.calc.SplitCalculator;
import com.householdsplitter.core.calc.result.SplitResult;
import com.householdsplitter.core.parse.model.OrderField;
import com.householdsplitter.core.parse.model.ParsedItem;
import com.householdsplitter.core.parse.model.ParsedOrder;
import com.householdsplitter.core.parse.model.ReviewReason;
import com.householdsplitter.data.dao.AssignmentDao;
import com.householdsplitter.data.dao.LineItemDao;
import com.householdsplitter.data.dao.MemberDao;
import com.householdsplitter.data.dao.OrderDao;
import com.householdsplitter.data.dao.OrderImageDao;
import com.householdsplitter.data.dao.ParticipantDao;
import com.householdsplitter.data.entity.DraftStep;
import com.householdsplitter.data.entity.ItemAssignment;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderImage;
import com.householdsplitter.data.entity.OrderStatus;
import com.householdsplitter.data.mapper.CalcMapper;
import com.householdsplitter.data.relation.LineItemWithAssignments;
import com.householdsplitter.data.relation.OrderBundle;
import com.householdsplitter.data.relation.OrderWithMembers;
import com.householdsplitter.util.AppExecutors;
import com.householdsplitter.util.Callback;
import com.householdsplitter.util.Result;

import java.util.ArrayList;
import java.util.List;

/** Orders, their rows, their participants and their assignments. */
public class OrderRepository {

    private final OrderDao orderDao;
    private final OrderImageDao imageDao;
    private final LineItemDao lineItemDao;
    private final ParticipantDao participantDao;
    private final AssignmentDao assignmentDao;
    private final MemberDao memberDao;
    private final AppExecutors executors;

    public OrderRepository(OrderDao orderDao, OrderImageDao imageDao, LineItemDao lineItemDao,
                           ParticipantDao participantDao, AssignmentDao assignmentDao,
                           MemberDao memberDao, AppExecutors executors) {
        this.orderDao = orderDao;
        this.imageDao = imageDao;
        this.lineItemDao = lineItemDao;
        this.participantDao = participantDao;
        this.assignmentDao = assignmentDao;
        this.memberDao = memberDao;
        this.executors = executors;
    }

    public LiveData<List<OrderWithMembers>> observeOrders(long householdId) {
        return orderDao.observeOrders(householdId);
    }

    public LiveData<OrderBundle> observeBundle(long orderId) {
        return orderDao.observeBundle(orderId);
    }

    /** SPEC 8.6.7: warn before creating a second order with the same order number. */
    public void findExistingByOrderNo(String externalOrderNo, Callback<Order> callback) {
        executors.diskIO().execute(() -> {
            Order existing = externalOrderNo == null || externalOrderNo.isEmpty()
                    ? null : orderDao.findByExternalOrderNoSync(externalOrderNo);
            post(callback, existing);
        });
    }

    /** SPEC 8.1.3: shared into an existing draft, offer to add to it. */
    public void latestDraft(long householdId, Callback<Order> callback) {
        executors.diskIO().execute(() -> post(callback, orderDao.latestDraftSync(householdId)));
    }

    /**
     * Persists a parse. Everything lands as a DRAFT and goes to the editable review screen
     * (SPEC 7.6.1, PROMPT hard rule 7): the parser never decides anything is final.
     */
    public void createFromParse(long householdId, ParsedOrder parsed, List<String> imageUris,
                                Callback<Result<Long>> callback) {
        executors.diskIO().execute(() -> {
            Order order = new Order();
            order.householdId = householdId;
            order.createdAt = System.currentTimeMillis();
            order.orderDate = parsed.orderDateMillis() == null
                    ? order.createdAt : parsed.orderDateMillis();
            order.label = parsed.label() == null || parsed.label().isEmpty()
                    ? defaultLabel(order.orderDate) : parsed.label();
            order.externalOrderNo = emptyToNull(parsed.externalOrderNo());
            order.status = OrderStatus.DRAFT;
            order.draftStep = DraftStep.REVIEW;
            order.taxCents = parsed.adjustments().taxCents();
            order.deliveryFeeCents = parsed.adjustments().deliveryFeeCents();
            order.tipCents = parsed.adjustments().tipCents();
            order.otherFeeCents = parsed.adjustments().otherFeeCents();
            order.discountCents = parsed.adjustments().discountCents();
            order.statedSubtotalCents = parsed.adjustments().statedSubtotalCents();
            order.statedTotalCents = parsed.adjustments().statedTotalCents();
            order.parsedFieldsCsv = joinFields(parsed.parsedFields());

            long orderId;
            try {
                orderId = orderDao.insert(order);
            } catch (RuntimeException duplicateOrderNo) {
                // The unique index fired: fall back to inserting without the number rather
                // than losing the whole import (SPEC 8.6.7 already warned the user).
                order.externalOrderNo = null;
                orderId = orderDao.insert(order);
            }

            List<LineItem> rows = new ArrayList<>();
            int position = 0;
            for (ParsedItem parsedItem : parsed.items()) {
                rows.add(toLineItem(orderId, parsedItem, position++));
            }
            if (!rows.isEmpty()) {
                lineItemDao.insertAll(rows);
            }
            saveImages(orderId, imageUris);

            // SPEC 7.8.2: everyone is a participant by default; S8 lets the user narrow it.
            List<Long> everyone = new ArrayList<>();
            for (Member member : memberDao.getActiveSync(householdId)) {
                everyone.add(member.id);
            }
            participantDao.replaceForOrder(orderId, everyone);

            post(callback, Result.ok(orderId));
        });
    }

    /** SPEC 7.5.4: "Enter manually" creates an empty order and jumps straight to S6. */
    public void createEmpty(long householdId, Callback<Result<Long>> callback) {
        createFromParse(householdId, ParsedOrder.empty(), new ArrayList<>(), callback);
    }

    public void addImages(long orderId, List<String> imageUris, Callback<Result<Void>> callback) {
        executors.diskIO().execute(() -> {
            saveImages(orderId, imageUris);
            post(callback, Result.ok(null));
        });
    }

    /** SPEC 7.6.5 and 7.6.7. */
    public void saveItem(LineItem item, Callback<Result<Void>> callback) {
        executors.diskIO().execute(() -> {
            if (item.id == 0L) {
                item.position = lineItemDao.maxPositionSync(item.orderId) + 1;
                lineItemDao.insert(item);
            } else {
                lineItemDao.update(item);
            }
            post(callback, Result.ok(null));
        });
    }

    /** SPEC 7.6.8: swipe to delete, with an Undo snackbar, so this must be reversible. */
    public void deleteItem(LineItem item, Callback<Result<Void>> callback) {
        executors.diskIO().execute(() -> {
            lineItemDao.delete(item);
            post(callback, Result.ok(null));
        });
    }

    public void restoreItem(LineItem item, Callback<Result<Void>> callback) {
        executors.diskIO().execute(() -> {
            lineItemDao.insert(item);
            post(callback, Result.ok(null));
        });
    }

    /** SPEC 7.7: the order-level fields, each editable. */
    public void saveOrder(Order order, Callback<Result<Void>> callback) {
        executors.diskIO().execute(() -> {
            orderDao.update(order);
            post(callback, Result.ok(null));
        });
    }

    public void updateProgress(long orderId, DraftStep step, int itemPosition) {
        executors.diskIO().execute(() -> orderDao.updateProgress(orderId, step, itemPosition));
    }

    /**
     * SPEC 7.8.5: changing the participants preserves every existing assignment except
     * those belonging to a removed participant, whose items are flagged for reassignment.
     */
    public void setParticipants(long orderId, List<Long> memberIds, Callback<Result<Void>> callback) {
        executors.diskIO().execute(() -> {
            List<Long> previous = participantDao.memberIdsSync(orderId);
            participantDao.replaceForOrder(orderId, memberIds);

            for (Long was : previous) {
                if (!memberIds.contains(was)) {
                    assignmentDao.deleteForMemberInOrder(orderId, was);
                }
            }
            for (Long orphanedItemId : assignmentDao.itemsLeftWithoutAssigneesSync(orderId)) {
                lineItemDao.updateScope(orphanedItemId, Scope.UNASSIGNED);
            }
            post(callback, Result.ok(null));
        });
    }

    /**
     * SPEC 7.9.4 and 5.9. Writing COMMON clears the item's assignment rows, so the item
     * resolves against the participant list and recalculates whenever that list changes.
     */
    public void assign(long lineItemId, Scope scope, List<ItemAssignment> assignments,
                       Callback<Result<Void>> callback) {
        executors.diskIO().execute(() -> {
            assignmentDao.deleteForItem(lineItemId);
            if (scope == Scope.SUBSET || scope == Scope.PERSONAL) {
                assignmentDao.insertAll(assignments);
            }
            lineItemDao.updateScope(lineItemId, scope);
            post(callback, Result.ok(null));
        });
    }

    /** SPEC 7.9.10: "Assign all remaining as common", from the current position onward. */
    public void assignRemainingAsCommon(long orderId, int fromPosition,
                                        Callback<Result<Integer>> callback) {
        executors.diskIO().execute(() -> {
            int changed = 0;
            for (LineItem item : lineItemDao.getUnassignedSync(orderId)) {
                if (item.position < fromPosition) {
                    continue;
                }
                assignmentDao.deleteForItem(item.id);
                lineItemDao.updateScope(item.id, Scope.COMMON);
                changed++;
            }
            post(callback, Result.ok(changed));
        });
    }

    /** SPEC 7.10.6: the blocking panel needs the offending rows, tappable. */
    public void unassignedItems(long orderId, Callback<List<LineItem>> callback) {
        executors.diskIO().execute(() -> post(callback, lineItemDao.getUnassignedSync(orderId)));
    }

    /** SPEC 6.4, off the main thread. Throws are surfaced as a failure, never swallowed. */
    public void calculate(long orderId, AllocationMode mode, Callback<Result<SplitResult>> callback) {
        executors.diskIO().execute(() -> {
            OrderBundle bundle = orderDao.getBundleSync(orderId);
            if (bundle == null) {
                post(callback, Result.failure("That order no longer exists"));
                return;
            }
            try {
                post(callback, Result.ok(
                        SplitCalculator.calculate(CalcMapper.toCalcOrder(bundle, mode))));
            } catch (RuntimeException failed) {
                post(callback, Result.failure(failed.getMessage()));
            }
        });
    }

    public void setPayer(long orderId, Long payerMemberId) {
        executors.diskIO().execute(() -> orderDao.updatePayer(orderId, payerMemberId));
    }

    /** SPEC 7.10.7. */
    public void setStatus(long orderId, OrderStatus status) {
        executors.diskIO().execute(() -> orderDao.updateStatus(orderId, status));
    }

    public void rename(long orderId, String label) {
        executors.diskIO().execute(() -> orderDao.rename(orderId, label));
    }

    /**
     * SPEC 7.3.6, "Duplicate assignments".
     *
     * <p>Copies the order into a fresh draft carrying its rows, its participants and its
     * answers, so a weekly shop of largely the same things starts from last week's answers
     * rather than from nothing. The copy is a DRAFT with no order number, so it can never
     * collide with the original under the unique index of SPEC 5.3, and no screenshot is
     * carried over because those belong to the shop that actually happened (SPEC 3.4).
     */
    public void duplicate(long orderId, Callback<Result<Long>> callback) {
        executors.diskIO().execute(() -> {
            OrderBundle source = orderDao.getBundleSync(orderId);
            if (source == null) {
                post(callback, Result.failure("That order no longer exists"));
                return;
            }
            Order copy = new Order();
            copy.householdId = source.order.householdId;
            copy.label = source.order.label + " (copy)";
            copy.orderDate = System.currentTimeMillis();
            copy.createdAt = copy.orderDate;
            copy.status = OrderStatus.DRAFT;
            copy.draftStep = DraftStep.REVIEW;
            copy.taxCents = source.order.taxCents;
            copy.deliveryFeeCents = source.order.deliveryFeeCents;
            copy.tipCents = source.order.tipCents;
            copy.otherFeeCents = source.order.otherFeeCents;
            copy.discountCents = source.order.discountCents;
            copy.statedSubtotalCents = source.order.statedSubtotalCents;
            copy.statedTotalCents = source.order.statedTotalCents;
            copy.payerMemberId = source.order.payerMemberId;
            long copyId = orderDao.insert(copy);

            List<Long> participants = new ArrayList<>();
            for (Member member : source.participants) {
                participants.add(member.id);
            }
            participantDao.replaceForOrder(copyId, participants);

            for (LineItemWithAssignments row : source.items) {
                LineItem item = new LineItem();
                item.orderId = copyId;
                item.name = row.item.name;
                item.rawOcrText = row.item.rawOcrText;
                item.quantity = row.item.quantity;
                item.lineTotalCents = row.item.lineTotalCents;
                item.unitPriceText = row.item.unitPriceText;
                item.scope = row.item.scope;
                item.sourceSection = row.item.sourceSection;
                item.needsReview = row.item.needsReview;
                item.reviewReasonsCsv = row.item.reviewReasonsCsv;
                item.position = row.item.position;
                long newItemId = lineItemDao.insert(item);

                List<ItemAssignment> assignments = new ArrayList<>();
                for (ItemAssignment assignment : row.assignments) {
                    assignments.add(new ItemAssignment(newItemId, assignment.memberId,
                            assignment.shares));
                }
                if (!assignments.isEmpty()) {
                    assignmentDao.insertAll(assignments);
                }
            }
            post(callback, Result.ok(copyId));
        });
    }

    /** SPEC 7.3.6. */
    public void delete(long orderId, Callback<Result<Void>> callback) {
        executors.diskIO().execute(() -> {
            orderDao.deleteById(orderId);
            post(callback, Result.ok(null));
        });
    }

    public void bundle(long orderId, Callback<OrderBundle> callback) {
        executors.diskIO().execute(() -> post(callback, orderDao.getBundleSync(orderId)));
    }

    public void allBundles(long householdId, Callback<List<OrderBundle>> callback) {
        executors.diskIO().execute(() -> post(callback, orderDao.getAllBundlesSync(householdId)));
    }

    private void saveImages(long orderId, List<String> imageUris) {
        if (imageUris == null || imageUris.isEmpty()) {
            return;
        }
        int start = imageDao.getForOrderSync(orderId).size();
        List<OrderImage> images = new ArrayList<>();
        for (int i = 0; i < imageUris.size(); i++) {
            images.add(new OrderImage(orderId, imageUris.get(i), start + i));
        }
        imageDao.insertAll(images);
    }

    private static LineItem toLineItem(long orderId, ParsedItem parsed, int position) {
        LineItem item = new LineItem();
        item.orderId = orderId;
        item.name = parsed.name();
        item.rawOcrText = parsed.rawOcrText();
        item.quantity = parsed.quantity();
        item.lineTotalCents = parsed.lineTotalCents();
        item.unitPriceText = parsed.unitPriceText();
        item.scope = parsed.scope();
        item.sourceSection = parsed.sourceSection();
        item.needsReview = parsed.needsReview();
        item.reviewReasonsCsv = joinReasons(parsed.reviewReasons());
        item.position = position;
        return item;
    }

    private static String joinReasons(Iterable<ReviewReason> reasons) {
        StringBuilder out = new StringBuilder();
        for (ReviewReason reason : reasons) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(reason.name());
        }
        return out.length() == 0 ? null : out.toString();
    }

    private static String joinFields(Iterable<OrderField> fields) {
        StringBuilder out = new StringBuilder();
        for (OrderField field : fields) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(field.name());
        }
        return out.length() == 0 ? null : out.toString();
    }

    private static String defaultLabel(long millis) {
        return new java.text.SimpleDateFormat("MMM dd", java.util.Locale.US)
                .format(new java.util.Date(millis)) + " Walmart";
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private <T> void post(Callback<T> callback, T value) {
        if (callback != null) {
            executors.mainThread().execute(() -> callback.onResult(value));
        }
    }
}
