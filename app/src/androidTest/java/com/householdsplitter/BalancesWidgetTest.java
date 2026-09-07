package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.householdsplitter.core.analytics.Balances;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.widget.BalancesWidgetProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The balances widget's rendering.
 *
 * <p>Worth a test because a RemoteViews layout fails at apply time rather than compile time.
 * An unsupported view type or a stale id throws inside the launcher process, where nothing
 * in this app would ever see it: the widget would simply sit there blank on somebody's home
 * screen. Applying the views here is exactly what the launcher does.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class BalancesWidgetTest {

    private Context context;
    private CurrencyFormat money;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        money = new CurrencyFormat("$", Locale.US);
    }

    /** The layout inflates at all, which is the failure this test exists to catch. */
    @Test
    @UiThreadTest
    public void theLayoutApplies() {
        View applied = apply(BalancesWidgetProvider.buildViews(
                context, "Flat 12", "Everyone is square.", null, null));

        assertNotNull(applied);
        assertEquals("Flat 12", text(applied, R.id.widgetTitle));
    }

    @Test
    @UiThreadTest
    public void aMessageShowsInsteadOfRows() {
        View applied = apply(BalancesWidgetProvider.buildViews(
                context, "Flat 12", "Everyone is square.", null, null));

        assertEquals(View.VISIBLE, visibility(applied, R.id.widgetMessage));
        assertEquals("Everyone is square.", text(applied, R.id.widgetMessage));
        assertEquals(View.GONE, visibility(applied, R.id.row1));
        assertEquals(View.GONE, visibility(applied, R.id.widgetMore));
    }

    @Test
    @UiThreadTest
    public void rowsShowInsteadOfAMessage() {
        View applied = apply(BalancesWidgetProvider.buildViews(context, "Flat 12", null,
                new String[]{"Ben pays Ana $12.40", "Chen pays Ana $3.10"}, null));

        assertEquals(View.GONE, visibility(applied, R.id.widgetMessage));
        assertEquals("Ben pays Ana $12.40", text(applied, R.id.row1));
        assertEquals("Chen pays Ana $3.10", text(applied, R.id.row2));
        assertEquals("the third row is not used", View.GONE, visibility(applied, R.id.row3));
    }

    /**
     * Rows are reused across updates, so a row left over from a previous, longer answer
     * would keep showing a payment that has already been made.
     */
    @Test
    @UiThreadTest
    public void unusedRowsAreHiddenNotLeftBehind() {
        View applied = apply(BalancesWidgetProvider.buildViews(
                context, "Flat 12", null, new String[]{"Ben pays Ana $12.40"}, null));

        assertEquals(View.VISIBLE, visibility(applied, R.id.row1));
        assertEquals(View.GONE, visibility(applied, R.id.row2));
        assertEquals(View.GONE, visibility(applied, R.id.row3));
    }

    @Test
    @UiThreadTest
    public void theMoreLineShowsWhenThereAreTooManyToFit() {
        View applied = apply(BalancesWidgetProvider.buildViews(context, "Flat 12", null,
                new String[]{"a", "b", "c"}, "and 2 more"));

        assertEquals(View.VISIBLE, visibility(applied, R.id.widgetMore));
        assertEquals("and 2 more", text(applied, R.id.widgetMore));
    }

    /** "Ben pays Ana $12.40" is an instruction; "Ben: -12.40" is a puzzle. */
    @Test
    public void transfersBecomeInstructions() {
        List<Balances.Transfer> transfers = transfersOf(
                new Object[]{"Ben", "Ana", 1240L},
                new Object[]{"Chen", "Ana", 310L});

        String[] rows = BalancesWidgetProvider.rowsFor(context, transfers, money);

        assertEquals(2, rows.length);
        assertEquals("Ben pays Ana $12.40", rows[0]);
        assertEquals("Chen pays Ana $3.10", rows[1]);
    }

    /** Three lines is as much as stays glanceable, and the rest are counted rather than cut. */
    @Test
    public void moreTransfersThanFitAreCounted() {
        List<Balances.Transfer> transfers = transfersOf(
                new Object[]{"A", "E", 100L},
                new Object[]{"B", "E", 100L},
                new Object[]{"C", "E", 100L},
                new Object[]{"D", "E", 100L},
                new Object[]{"F", "E", 100L});

        String[] rows = BalancesWidgetProvider.rowsFor(context, transfers, money);

        assertEquals(3, rows.length);
        assertEquals("and 2 more",
                BalancesWidgetProvider.moreFor(context, transfers.size(), rows.length));
    }

    @Test
    public void nothingIsAddedWhenEveryTransferFits() {
        assertNull(BalancesWidgetProvider.moreFor(context, 2, 2));
        assertNull(BalancesWidgetProvider.moreFor(context, 1, 3));
    }

    /**
     * A {@link Balances.Transfer} has no public constructor, so the real thing is built from
     * the real balances the way the app does.
     */
    private List<Balances.Transfer> transfersOf(Object[]... owed) {
        // Each debtor owes the same creditor, which is what the greedy matcher turns into
        // one transfer per debtor.
        List<Balances.Settlement> settlements = new java.util.ArrayList<>();
        long creditorId = 100L;
        String creditor = (String) owed[0][1];
        List<com.householdsplitter.core.calc.input.CalcMember> members =
                new java.util.ArrayList<>();
        members.add(new com.householdsplitter.core.calc.input.CalcMember(
                creditorId, creditor, 0));
        List<com.householdsplitter.core.calc.input.CalcLineItem> items =
                new java.util.ArrayList<>();
        long total = 0L;
        for (int i = 0; i < owed.length; i++) {
            long memberId = 200L + i;
            members.add(new com.householdsplitter.core.calc.input.CalcMember(
                    memberId, (String) owed[i][0], i + 1));
            long cents = (Long) owed[i][2];
            items.add(com.householdsplitter.core.calc.input.CalcLineItem.personal(
                    i + 1L, "Item " + i, cents, memberId));
            total += cents;
        }
        com.householdsplitter.core.calc.result.SplitResult result =
                com.householdsplitter.core.calc.SplitCalculator.calculate(
                        new com.householdsplitter.core.calc.input.CalcOrder(members, items,
                                com.householdsplitter.core.calc.input.Adjustments.none(),
                                total,
                                com.householdsplitter.core.calc.AllocationMode.PROPORTIONAL));
        settlements.add(new Balances.Settlement(creditorId, result));

        List<Balances.Transfer> transfers = Balances.of(settlements).transfers();
        assertTrue("the fixture should produce one transfer per debtor",
                transfers.size() == owed.length);
        return transfers;
    }

    private View apply(RemoteViews views) {
        FrameLayout parent = new FrameLayout(context);
        return views.apply(context, parent);
    }

    private String text(View root, int id) {
        TextView view = root.findViewById(id);
        assertNotNull("no view for id " + id, view);
        return view.getText().toString();
    }

    private int visibility(View root, int id) {
        View view = root.findViewById(id);
        assertNotNull("no view for id " + id, view);
        return view.getVisibility();
    }
}
