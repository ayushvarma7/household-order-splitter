package com.householdsplitter.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import com.householdsplitter.R;
import com.householdsplitter.SplitterApp;
import com.householdsplitter.core.analytics.Balances;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.di.ServiceLocator;
import com.householdsplitter.ui.MainActivity;

import java.util.List;

/**
 * A home-screen widget showing who owes whom.
 *
 * <p>The question a household asks between shops is not "what did we spend?" but "am I
 * square with anyone?", and answering it currently means opening the app and finding the
 * analytics screen. This puts the answer where they already look.
 *
 * <p>It shows the suggested transfers rather than each person's net balance, because
 * "Ben pays Ana $12.40" is an instruction and "Ben: -12.40" is a puzzle. They come from the
 * same {@link Balances} the analytics screen uses, so the widget and the screen can never
 * disagree.
 */
public class BalancesWidgetProvider extends AppWidgetProvider {

    /** As many as fit before the widget stops being glanceable. */
    private static final int MAX_ROWS = 3;

    private static final int[] ROW_IDS = {R.id.row1, R.id.row2, R.id.row3};

    /**
     * Redraws every instance of the widget. Called after settling an order or recording a
     * payment, which are the only two things that can change the answer.
     */
    public static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(
                new android.content.ComponentName(context, BalancesWidgetProvider.class));
        if (ids.length == 0) {
            return;
        }
        Intent intent = new Intent(context, BalancesWidgetProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        context.sendBroadcast(intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        // Reading the balances means reading every settled order off the disk, which is far
        // too much for a broadcast's main thread. goAsync holds the receiver alive until the
        // work finishes; without it onUpdate would return before there was anything to draw
        // and the widget would stay on whatever it last showed.
        PendingResult pending = goAsync();
        ServiceLocator locator = ((SplitterApp) context.getApplicationContext())
                .serviceLocator();

        locator.executors().diskIO().execute(() -> {
            com.householdsplitter.data.entity.Household household =
                    locator.database().householdDao().getHouseholdSync();
            if (household == null) {
                // Nothing set up yet. An invitation, not an error.
                push(context, manager, appWidgetIds,
                        context.getString(R.string.app_name),
                        context.getString(R.string.widget_no_household), null, null);
                pending.finish();
                return;
            }
            locator.workbookService().analyse(household.id, insight -> {
                try {
                    if (insight == null || insight.balances == null) {
                        push(context, manager, appWidgetIds, household.name,
                                context.getString(R.string.widget_nothing_yet), null, null);
                        return;
                    }
                    List<Balances.Transfer> transfers = insight.balances.transfers();
                    if (transfers.isEmpty()) {
                        push(context, manager, appWidgetIds, household.name,
                                context.getString(R.string.widget_all_square), null, null);
                        return;
                    }
                    CurrencyFormat money = new CurrencyFormat(
                            locator.settings().currencySymbol(), locator.settings().locale());
                    String[] rows = rowsFor(context, transfers, money);
                    push(context, manager, appWidgetIds, household.name, null, rows,
                            moreFor(context, transfers.size(), rows.length));
                } finally {
                    pending.finish();
                }
            });
        });
    }

    private void push(Context context, AppWidgetManager manager, int[] appWidgetIds,
                      String title, String message, String[] rows, String more) {
        for (int appWidgetId : appWidgetIds) {
            manager.updateAppWidget(appWidgetId,
                    buildViews(context, title, message, rows, more));
        }
    }

    /**
     * Builds the widget's contents.
     *
     * <p>Separate from {@link #push} and visible for testing because a RemoteViews layout
     * fails at apply time, not compile time: an unsupported view type or a stale id throws
     * inside the launcher, where nothing in this app would ever see it.
     *
     * @param message a single line instead of rows, for the states that have no transfers
     * @param rows    one line per suggested payment
     * @param more    "and 2 more", or null
     */
    public static RemoteViews buildViews(Context context, String title, String message,
                                  String[] rows, String more) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_balances);
        views.setTextViewText(R.id.widgetTitle, title);

        views.setViewVisibility(R.id.widgetMessage, message == null ? View.GONE : View.VISIBLE);
        if (message != null) {
            views.setTextViewText(R.id.widgetMessage, message);
        }
        for (int i = 0; i < ROW_IDS.length; i++) {
            boolean used = rows != null && i < rows.length;
            views.setViewVisibility(ROW_IDS[i], used ? View.VISIBLE : View.GONE);
            if (used) {
                views.setTextViewText(ROW_IDS[i], rows[i]);
            }
        }
        views.setViewVisibility(R.id.widgetMore, more == null ? View.GONE : View.VISIBLE);
        if (more != null) {
            views.setTextViewText(R.id.widgetMore, more);
        }

        views.setOnClickPendingIntent(R.id.widgetRoot, openBalances(context));
        return views;
    }

    /**
     * Turns the household's transfers into the widget's lines. Pulled out so the mapping
     * from balances to text can be checked without a launcher.
     */
    public static String[] rowsFor(Context context, List<Balances.Transfer> transfers,
                            CurrencyFormat money) {
        String[] rows = new String[Math.min(MAX_ROWS, transfers.size())];
        for (int i = 0; i < rows.length; i++) {
            Balances.Transfer transfer = transfers.get(i);
            rows[i] = context.getString(R.string.widget_transfer,
                    transfer.from().name(), transfer.to().name(),
                    money.format(transfer.amountCents()));
        }
        return rows;
    }

    /** "and 2 more", or null when every transfer fits. */
    public static String moreFor(Context context, int transferCount, int shownCount) {
        return transferCount > shownCount
                ? context.getString(R.string.widget_more, transferCount - shownCount) : null;
    }

    /** Tapping the widget lands on the screen the numbers came from, not just the app. */
    private static PendingIntent openBalances(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_BALANCES);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
