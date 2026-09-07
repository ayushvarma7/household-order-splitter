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
import java.util.LinkedHashSet;
import java.util.Set;
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
            order.deliveredUnitCount = parsed.deliveredUnitCount();

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

    /**
     * Adds a further parse to an order that already exists. SPEC 8.1.3.
     *
     * <p>This is what makes "you may have missed a screenshot" actionable rather than
     * merely true. Two things it has to get right. New rows are de-duplicated against the
     * rows already stored, because a user adding a screenshot will overlap what they
     * already captured and SPEC 8.7.3's dedupe only ever sees one parse at a time. And an
     * order-level figure is only filled in where it is still absent: the user may have
     * corrected the subtotal by hand on S7, and a later screenshot must not quietly
     * overwrite that.
     *
     * @return how many rows were added
     */
    public void appendParse(long orderId, ParsedOrder parsed, List<String> imageUris,
                            Callback<Result<Integer>> callback) {
        executors.diskIO().execute(() -> {
            Order order = orderDao.getByIdSync(orderId);
            if (order == null) {
                post(callback, Result.failure("That order no longer exists"));
                return;
            }

            Set<String> existing = new LinkedHashSet<>();
            for (LineItemWithAssignments row : lineItemDao.getForOrderSync(orderId)) {
                existing.add(dedupeKey(row.item.name, row.item.lineTotalCents));
            }

            int position = lineItemDao.maxPositionSync(orderId) + 1;
            List<LineItem> fresh = new ArrayList<>();
            for (ParsedItem parsedItem : parsed.items()) {
                if (!existing.add(dedupeKey(parsedItem.name(), parsedItem.lineTotalCents()))) {
                    continue;
                }
                fresh.add(toLineItem(orderId, parsedItem, position++));
            }
            if (!fresh.isEmpty()) {
                lineItemDao.insertAll(fresh);
            }
            saveImages(orderId, imageUris);

            // Only fill what is still missing. A user's correction outranks a later parse.
            boolean changed = false;
            if (order.statedSubtotalCents == 0L && parsed.adjustments().statedSubtotalCents() != 0L) {
                order.statedSubtotalCents = parsed.adjustments().statedSubtotalCents();
                changed = true;
            }
            if (order.statedTotalCents == 0L && parsed.adjustments().statedTotalCents() != 0L) {
                order.statedTotalCents = parsed.adjustments().statedTotalCents();
                changed = true;
            }
            if (order.taxCents == 0L && parsed.adjustments().taxCents() != 0L) {
                order.taxCents = parsed.adjustments().taxCents();
                changed = true;
            }
            if (order.deliveryFeeCents == 0L && parsed.adjustments().deliveryFeeCents() != 0L) {
                order.deliveryFeeCents = parsed.adjustments().deliveryFeeCents();
                changed = true;
            }
            if (order.tipCents == 0L && parsed.adjustments().tipCents() != 0L) {
                order.tipCents = parsed.adjustments().tipCents();
                changed = true;
            }
            if (order.otherFeeCents == 0L && parsed.adjustments().otherFeeCents() != 0L) {
                order.otherFeeCents = parsed.adjustments().otherFeeCents();
                changed = true;
            }
            if (order.externalOrderNo == null && parsed.externalOrderNo() != null) {
                order.externalOrderNo = parsed.externalOrderNo();
                changed = true;
            }
            if (order.deliveredUnitCount <= 0 && parsed.deliveredUnitCount() > 0) {
                order.deliveredUnitCount = parsed.deliveredUnitCount();
                changed = true;
            }
            if (changed) {
                try {
                    orderDao.update(order);
                } catch (RuntimeException duplicateOrderNo) {
                    // The order number collided with another order. Keeping the rows we
                    // just added matters more than recording the number.
                    order.externalOrderNo = null;
                    orderDao.update(order);
                }
            }
            post(callback, Result.ok(fresh.size()));
        });
    }

    private static String dedupeKey(String name, long cents) {
        return (name == null ? "" : name.trim().toLowerCase().replaceAll("\\s+", " "))
                + "|" + cents;
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

    /**
     * Adds a row for money the rows do not account for.
     *
     * <p>When the items come to 11.68 and the bill says 53.52, telling the user they are
     * off by 41.84 and leaving them to find it is the least useful moment to stop helping.
     * Usually the cause is a screenshot they did not take, and the honest options are to go
     * and take it or to book the difference as one row and move on. This is the second.
     *
     * <p>The row is UNASSIGNED, not COMMON: it is real money somebody has to answer for,
     * and quietly charging it to everybody would be a guess dressed up as a total.
     */
    public void addDifferenceItem(long orderId, long cents, String name,
                                  Callback<Result<Long>> callback) {
        executors.diskIO().execute(() -> {
            if (cents == 0L) {
                post(callback, Result.failure("Nothing to add"));
                return;
            }
            LineItem item = new LineItem();
            item.orderId = orderId;
            item.name = name;
            item.rawOcrText = "";
            item.lineTotalCents = cents;
            item.scope = Scope.UNASSIGNED;
            item.needsReview = true;
            item.reviewReasonsCsv = ReviewReason.MANUALLY_ADDED.name();
            item.position = lineItemDao.maxPositionSync(orderId) + 1;
            post(callback, Result.ok(lineItemDao.insert(item)));
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

    /**
     * One answer applied to many rows at once.
     *
     * <p>The assignment loop of SPEC 7.9 is one item at a time, which is right for deciding.
     * It is the wrong shape for a decision already made: "these six are all mine" should be
     * one action, not six. SPEC 5.9 still holds, so a COMMON row has its assignment rows
     * cleared rather than being given a set of them.
     *
     * @return how many rows changed
     */
    public void assignMany(List<Long> lineItemIds, Scope scope, List<Long> memberIds,
                           List<Integer> shares, Callback<Result<Integer>> callback) {
        executors.diskIO().execute(() -> {
            int changed = 0;
            for (Long lineItemId : lineItemIds) {
                if (lineItemId == null) {
                    continue;
                }
                assignmentDao.deleteForItem(lineItemId);
                if (scope == Scope.SUBSET || scope == Scope.PERSONAL) {
                    List<ItemAssignment> assignments = new ArrayList<>();
                    for (int i = 0; i < memberIds.size(); i++) {
                        int share = shares == null || i >= shares.size() ? 1 : shares.get(i);
                        assignments.add(new ItemAssignment(lineItemId, memberIds.get(i), share));
                    }
                    if (!assignments.isEmpty()) {
                        assignmentDao.insertAll(assignments);
                    }
                }
                lineItemDao.updateScope(lineItemId, scope);
                changed++;
            }
            post(callback, Result.ok(changed));
        });
    }

    /**
     * Applies one remembered answer per row, in a single pass.
     *
     * <p>SPEC 7.9.12 forbids a suggestion advancing the screen by itself, and this does not:
     * the user asked for every suggestion to be applied, which is a decision rather than an
     * assumption, and every row it touches stays editable afterwards.
     *
     * @param answers item id to the scope and members to apply; entries may be null
     * @return how many rows a suggestion was found for
     */
    public void applyRememberedAnswers(List<Long> lineItemIds, List<Scope> scopes,
                                       List<List<Long>> memberIds,
                                       Callback<Result<Integer>> callback) {
        executors.diskIO().execute(() -> {
            int applied = 0;
            for (int i = 0; i < lineItemIds.size(); i++) {
                Scope scope = scopes.get(i);
                if (scope == null || scope == Scope.UNASSIGNED) {
                    continue;
                }
                Long itemId = lineItemIds.get(i);
                List<Long> members = memberIds.get(i);
                if ((scope == Scope.SUBSET || scope == Scope.PERSONAL)
                        && (members == null || members.isEmpty())) {
                    // A remembered answer with nobody in it cannot be applied.
                    continue;
                }
                assignmentDao.deleteForItem(itemId);
                if (scope == Scope.SUBSET || scope == Scope.PERSONAL) {
                    List<ItemAssignment> assignments = new ArrayList<>();
                    for (Long memberId : members) {
                        assignments.add(new ItemAssignment(itemId, memberId, 1));
                    }
                    assignmentDao.insertAll(assignments);
                }
                lineItemDao.updateScope(itemId, scope);
                applied++;
            }
            post(callback, Result.ok(applied));
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
