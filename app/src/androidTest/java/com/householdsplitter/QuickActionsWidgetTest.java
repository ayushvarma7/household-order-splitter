package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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

import com.householdsplitter.widget.QuickActionsWidgetProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The quick actions widget's rendering.
 *
 * <p>Same reason as its neighbour: a RemoteViews layout fails at apply time rather than
 * compile time, inside the launcher's process, where nothing in this app would ever see it.
 * The widget would simply sit blank on somebody's home screen. Applying the views here is
 * what the launcher does.
 *
 * <p>This one is worth checking in particular because it is a plain layout with no state,
 * which is exactly the sort of file nobody looks at again: a view class RemoteViews does
 * not accept would go unnoticed until it was on a home screen.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class QuickActionsWidgetTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    @UiThreadTest
    public void theLayoutApplies() {
        View applied = apply(QuickActionsWidgetProvider.buildViews(context));
        assertNotNull(applied);
    }

    @Test
    @UiThreadTest
    public void allThreeActionsAreLabelledAndTappable() {
        View applied = apply(QuickActionsWidgetProvider.buildViews(context));

        for (int id : new int[]{R.id.quickScan, R.id.quickNewOrder, R.id.quickBalances}) {
            View action = applied.findViewById(id);
            assertNotNull("missing action " + id, action);
            assertTrue("an action nobody can press", action.hasOnClickListeners());
            assertTrue("an unlabelled action", ((TextView) action).getText().length() > 0);
        }
    }

    /** The three labels differ, which is what proves three separate intents were wired. */
    @Test
    @UiThreadTest
    public void theActionsAreNotAllTheSameOne() {
        View applied = apply(QuickActionsWidgetProvider.buildViews(context));
        String scan = ((TextView) applied.findViewById(R.id.quickScan)).getText().toString();
        String order =
                ((TextView) applied.findViewById(R.id.quickNewOrder)).getText().toString();
        String balances =
                ((TextView) applied.findViewById(R.id.quickBalances)).getText().toString();

        assertEquals(3, new java.util.HashSet<>(
                java.util.Arrays.asList(scan, order, balances)).size());
    }

    private View apply(RemoteViews views) {
        return views.apply(context, new FrameLayout(context));
    }
}
