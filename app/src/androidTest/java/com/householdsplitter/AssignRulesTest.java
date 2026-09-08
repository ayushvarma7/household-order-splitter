package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.Observer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.MemberRule;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderParticipant;
import com.householdsplitter.data.relation.OrderBundle;
import com.householdsplitter.di.ServiceLocator;
import com.householdsplitter.suggest.RuleService;
import com.householdsplitter.ui.assign.AssignViewModel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Standing rules as the assign screen actually uses them.
 *
 * <p>{@link StandingRulesTest} covers storing and matching. This covers the wiring, which is
 * where a rule that works perfectly in isolation still fails to reach the user: whether
 * tapping Everyone runs the rules, whether the screen is told what happened, and whether
 * touching a chip by hand hands control back.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class AssignRulesTest {

    private AppDatabase database;
    private ServiceLocator locator;
    private RuleService rules;
    private long householdId;
    private long orderId;
    private long anaId;
    private long benId;
    private long chenId;

    @Before
    public void setUp() throws Exception {
        TestData.wipe();
        database = TestData.database();
        locator = TestData.locator();
        rules = locator.ruleService();

        householdId = database.householdDao().insert(new Household("Fixture Group", 1L));
        locator.currentHouseholdId(householdId);
        anaId = database.memberDao().insert(new Member(householdId, "Ana", "#1F6FB2", 0));
        benId = database.memberDao().insert(new Member(householdId, "Ben", "#B3261E", 1));
        chenId = database.memberDao().insert(new Member(householdId, "Chen", "#1B5E20", 2));

        Order order = new Order();
        order.householdId = householdId;
        order.label = "Fixture order";
        order.orderDate = 1L;
        order.createdAt = 1L;
        orderId = database.orderDao().insert(order);
        database.participantDao().insertAll(Arrays.asList(
                new OrderParticipant(orderId, anaId),
                new OrderParticipant(orderId, benId),
                new OrderParticipant(orderId, chenId)));

        addItem("Great Value Light Beer, 12 pk", 1299L, 0);
        addItem("Whole milk", 342L, 1);
    }

    private void addItem(String name, long cents, int position) {
        LineItem item = new LineItem();
        item.orderId = orderId;
        item.name = name;
        item.rawOcrText = name;
        item.lineTotalCents = cents;
        item.quantity = 1;
        item.scope = Scope.UNASSIGNED;
        item.position = position;
        database.lineItemDao().insert(item);
    }

    private void addRule(long memberId, String keyword, MemberRule.Kind kind) throws Exception {
        database.memberRuleDao().insert(new MemberRule(householdId, memberId, keyword, kind));
        CountDownLatch latch = new CountDownLatch(1);
        rules.refresh(householdId, loaded -> latch.countDown());
        assertTrue(latch.await(15, TimeUnit.SECONDS));
    }

    /**
     * The headline case. On a household where one person is never on beer, tapping Everyone
     * on a beer item must not put them back on it, every order, forever.
     */
    @Test
    public void tappingEveryoneRespectsAnExcludeRule() throws Exception {
        addRule(benId, "beer", MemberRule.Kind.EXCLUDE);
        AssignViewModel model = readyModel();

        onMain(model::selectEveryone);

        Map<Long, Integer> selection = model.selection().getValue();
        assertNotNull(selection);
        assertEquals(2, selection.size());
        assertTrue(selection.containsKey(anaId));
        assertTrue(selection.containsKey(chenId));
        assertFalse("Ben stays off", selection.containsKey(benId));
        assertEquals("two off everyone is a subset, not a common item",
                Scope.SUBSET, model.resolvedScope());
    }

    /**
     * A rule quietly changing who pays would be worse than no rules. The note has to name
     * both the person and the keyword, or the user cannot tell which rule did it.
     */
    @Test
    public void theScreenIsToldWhatTheRuleDid() throws Exception {
        addRule(benId, "beer", MemberRule.Kind.EXCLUDE);
        AssignViewModel model = readyModel();

        onMain(model::selectEveryone);

        AssignViewModel.RuleNote note = model.ruleNote().getValue();
        assertNotNull("the note is what makes the rule visible", note);
        assertTrue(note.removed(), note.removed().contains("Ben"));
        assertTrue("and which rule did it", note.removed().contains("beer"));
        assertTrue("nobody was added", note.added().isEmpty());
    }

    /** A rule is a starting point, not a veto: putting Ben back must stick. */
    @Test
    public void theUserCanPutSomebodyBackOn() throws Exception {
        addRule(benId, "beer", MemberRule.Kind.EXCLUDE);
        AssignViewModel model = readyModel();
        onMain(model::selectEveryone);

        onMain(() -> model.toggle(benId));

        Map<Long, Integer> selection = model.selection().getValue();
        assertNotNull(selection);
        assertTrue("the user's choice wins", selection.containsKey(benId));
        assertNull("and the note clears, because the user has taken over",
                model.ruleNote().getValue());
    }

    /** The rule must not touch an item its keyword says nothing about. */
    @Test
    public void anUnrelatedItemIsUnaffected() throws Exception {
        addRule(benId, "beer", MemberRule.Kind.EXCLUDE);
        AssignViewModel model = readyModel();
        onMain(() -> model.jumpTo(1));   // Whole milk

        onMain(model::selectEveryone);

        Map<Long, Integer> selection = model.selection().getValue();
        assertNotNull(selection);
        assertEquals("everyone, untouched", 3, selection.size());
        assertEquals(Scope.COMMON, model.resolvedScope());
        assertNull(model.ruleNote().getValue());
    }

    /** With no rules at all the screen behaves exactly as it did before the feature. */
    @Test
    public void withoutRulesEveryoneMeansEveryone() throws Exception {
        AssignViewModel model = readyModel();

        onMain(model::selectEveryone);

        Map<Long, Integer> selection = model.selection().getValue();
        assertNotNull(selection);
        assertEquals(3, selection.size());
        assertEquals(Scope.COMMON, model.resolvedScope());
        assertNull(model.ruleNote().getValue());
    }

    /** An include rule prefills somebody onto an item nobody has answered yet. */
    @Test
    public void anIncludeRulePrefillsOnAFreshItem() throws Exception {
        addRule(chenId, "beer", MemberRule.Kind.INCLUDE);
        AssignViewModel model = readyModel();

        Map<Long, Integer> selection = model.selection().getValue();
        assertNotNull(selection);
        assertEquals(1, selection.size());
        assertTrue(selection.containsKey(chenId));
        AssignViewModel.RuleNote note = model.ruleNote().getValue();
        assertNotNull(note);
        assertTrue(note.added(), note.added().contains("Chen"));
        assertTrue("nobody was removed", note.removed().isEmpty());
    }

    /** SPEC 7.8.5: a rule cannot put somebody on an order they are not in on. */
    @Test
    public void aRuleCannotAddANonParticipant() throws Exception {
        // Chen leaves the order, and their include rule has to stop applying with them.
        database.getOpenHelper().getWritableDatabase().execSQL(
                "DELETE FROM order_participants WHERE orderId = " + orderId
                        + " AND memberId = " + chenId);
        addRule(chenId, "beer", MemberRule.Kind.INCLUDE);
        AssignViewModel model = readyModel();

        onMain(model::selectEveryone);

        Map<Long, Integer> selection = model.selection().getValue();
        assertNotNull(selection);
        assertFalse(selection.containsKey(chenId));
        assertEquals(2, selection.size());
    }

    /**
     * Builds the ViewModel and waits for the order to arrive, which is what the fragment's
     * bundle observer does before anything is rendered.
     */
    private AssignViewModel readyModel() throws Exception {
        CountDownLatch loaded = new CountDownLatch(1);
        AssignViewModel[] holder = new AssignViewModel[1];
        onMain(() -> {
            AssignViewModel model = new AssignViewModel(locator.orderRepository(),
                    locator.assignmentMemory(), rules, orderId, householdId);
            holder[0] = model;
            model.bundle().observeForever(new Observer<OrderBundle>() {
                @Override
                public void onChanged(OrderBundle bundle) {
                    if (bundle == null || bundle.items.isEmpty()) {
                        return;
                    }
                    model.restorePosition(bundle);
                    model.bundle().removeObserver(this);
                    loaded.countDown();
                }
            });
        });
        assertTrue("the order never loaded", loaded.await(15, TimeUnit.SECONDS));
        // The suggestion lookup and the rule load are both asynchronous; let them settle so
        // the assertions see the state the user would.
        Thread.sleep(600L);
        return holder[0];
    }

    private void onMain(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> {
                    action.run();
                    done.countDown();
                });
        assertTrue(done.await(15, TimeUnit.SECONDS));
    }
}
