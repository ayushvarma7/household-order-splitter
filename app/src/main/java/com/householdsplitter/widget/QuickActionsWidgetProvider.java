package com.householdsplitter.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.householdsplitter.R;
import com.householdsplitter.ui.MainActivity;

/**
 * A row of the three things somebody opens this app to do.
 *
 * <p>Separate from the balances widget rather than bolted onto it, because they answer
 * different questions and are wanted in different places. The balances widget is something
 * to read: it earns a large tile and it costs a pass over every settled order to draw. This
 * is something to press, it is one cell high, and it reads nothing at all.
 *
 * <p>Nothing here touches the database, so unlike its neighbour it needs no background work
 * and no {@code goAsync}. An update is three pending intents and a layout.
 *
 * <p>Each action needs its own request code. Two PendingIntents that differ only in their
 * extras are the same PendingIntent as far as the system is concerned, so without distinct
 * codes all three buttons would quietly end up doing whichever was created last.
 */
public class QuickActionsWidgetProvider extends AppWidgetProvider {

    private static final int REQUEST_SCAN = 10;
    private static final int REQUEST_NEW_ORDER = 11;
    private static final int REQUEST_BALANCES = 12;

    /** Redraws every placed instance. */
    public static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(
                new ComponentName(context, QuickActionsWidgetProvider.class));
        if (ids.length == 0) {
            return;
        }
        manager.updateAppWidget(ids, buildViews(context));
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        manager.updateAppWidget(appWidgetIds, buildViews(context));
    }

    /**
     * Visible for testing, because a RemoteViews layout fails at apply time rather than
     * compile time: an unsupported view class or a stale id throws inside the launcher,
     * where nothing in this app would ever see it.
     */
    public static RemoteViews buildViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.widget_quick_actions);
        views.setOnClickPendingIntent(R.id.quickScan,
                open(context, MainActivity.OPEN_SCAN_BILL, REQUEST_SCAN));
        views.setOnClickPendingIntent(R.id.quickNewOrder,
                open(context, MainActivity.OPEN_NEW_ORDER, REQUEST_NEW_ORDER));
        views.setOnClickPendingIntent(R.id.quickBalances,
                open(context, MainActivity.OPEN_BALANCES, REQUEST_BALANCES));
        return views;
    }

    private static PendingIntent open(Context context, String destination, int requestCode) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.putExtra(MainActivity.EXTRA_OPEN, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
