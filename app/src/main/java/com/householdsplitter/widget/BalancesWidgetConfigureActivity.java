package com.householdsplitter.widget;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.householdsplitter.R;
import com.householdsplitter.SplitterApp;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.databinding.ActivityWidgetConfigureBinding;
import com.householdsplitter.databinding.ItemWidgetGroupChoiceBinding;
import com.householdsplitter.di.ServiceLocator;
import com.householdsplitter.ui.theme.Palette;

import java.util.List;

/**
 * Asked once, when a balances widget is placed: which group should this one show?
 *
 * <p>The question exists because a home screen can hold two of these and the useful thing
 * to put in them is two different groups, which the app itself cannot express: it has one
 * group open at a time.
 *
 * <p>The first option is "whichever group is open", and it is the default, because for
 * anybody with a single group that is both correct and requires no thought. Picking a
 * specific group is for the case where it matters.
 *
 * <p>Three rules that a widget configuration screen has to follow and is easy not to:
 *
 * <ul>
 *   <li>The result is set to {@code RESULT_CANCELED} before anything else. If the user
 *       backs out, the launcher must hear "cancelled" and drop the widget rather than
 *       leaving an unconfigured one on the home screen.
 *   <li>The widget is drawn before this screen closes. A configuration activity does not
 *       trigger an update on its own, so without that the user is returned to a blank
 *       widget that fills in whenever the system next feels like it.
 *   <li>The palette overlay is applied here too. This is a separate activity, so it does
 *       not inherit the theme MainActivity sets, and without it a Slate install briefly
 *       shows a purple screen.
 * </ul>
 */
public class BalancesWidgetConfigureActivity extends AppCompatActivity {

    private ActivityWidgetConfigureBinding binding;
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ServiceLocator locator = ((SplitterApp) getApplication()).serviceLocator();
        Palette palette = locator.settings().palette();
        if (palette.overlayRes() != 0) {
            getTheme().applyStyle(palette.overlayRes(), true);
        }
        super.onCreate(savedInstanceState);

        // Before anything else, so backing out of this screen drops the widget instead of
        // leaving one behind that was never configured.
        setResult(RESULT_CANCELED);

        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            appWidgetId = intent.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        binding = ActivityWidgetConfigureBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // A close, not a back arrow, and it cancels. The result was already set to
        // RESULT_CANCELED, so leaving this way tells the launcher to drop the widget rather
        // than keep an unconfigured one.
        binding.toolbar.setNavigationContentDescription(R.string.action_cancel);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        long chosen = new WidgetPreferences(this).householdFor(appWidgetId);
        locator.executors().diskIO().execute(() -> {
            List<Household> groups = locator.database().householdDao().getAllSync();
            runOnUiThread(() -> render(locator, groups, chosen));
        });
    }

    private void render(ServiceLocator locator, List<Household> groups, long chosen) {
        if (binding == null) {
            return;
        }
        binding.groupList.removeAllViews();
        binding.groupList.addView(choice(
                getString(R.string.widget_configure_follow_app),
                getString(R.string.widget_configure_follow_app_detail),
                chosen == WidgetPreferences.FOLLOW_APP,
                WidgetPreferences.FOLLOW_APP, locator));

        for (Household group : groups) {
            binding.groupList.addView(choice(group.name, null, chosen == group.id,
                    group.id, locator));
        }
        // One group and nothing to decide: the only sensible answer is already correct, so
        // asking about it is the interruption rather than the help.
        if (groups.size() <= 1) {
            commit(locator, WidgetPreferences.FOLLOW_APP);
        }
    }

    private View choice(String name, String detail, boolean selected, long householdId,
                        ServiceLocator locator) {
        ItemWidgetGroupChoiceBinding row = ItemWidgetGroupChoiceBinding.inflate(
                LayoutInflater.from(this), binding.groupList, false);
        row.choiceName.setText(name);
        row.choiceDetail.setVisibility(detail == null ? View.GONE : View.VISIBLE);
        if (detail != null) {
            row.choiceDetail.setText(detail);
        }
        row.choiceTick.setVisibility(selected ? View.VISIBLE : View.GONE);
        row.getRoot().setOnClickListener(v -> commit(locator, householdId));
        return row.getRoot();
    }

    /** Records the choice, draws the widget, and hands the launcher its id back. */
    private void commit(ServiceLocator locator, long householdId) {
        new WidgetPreferences(this).householdFor(appWidgetId, householdId);
        // Drawn here, because placing a widget through a configuration activity does not
        // trigger an update on its own and the user would otherwise be handed a blank one.
        BalancesWidgetProvider.refresh(this);
        setResult(RESULT_OK, new Intent().putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
