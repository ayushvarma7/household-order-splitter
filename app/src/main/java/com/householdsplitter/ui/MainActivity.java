package com.householdsplitter.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.WindowCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.fragment.NavHostFragment;

import com.householdsplitter.R;
import com.householdsplitter.SplitterApp;
import com.householdsplitter.databinding.ActivityMainBinding;
import com.householdsplitter.di.ServiceLocator;
import com.householdsplitter.ui.importer.ImportFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * The single Activity. SPEC 4.4: everything else is a Fragment inside one navigation graph.
 *
 * <p>The start destination is decided here rather than in the graph, because SPEC 7.1.1
 * says S1 is reached on "first launch only, when no Household row exists" and SPEC 7.3.1
 * says Home is reached on "app launch when a household exists". Choosing up front also
 * satisfies SPEC 7.1.5, which forbids returning to S1 once a household exists: S1 is never
 * put on the back stack at all.
 */
public class MainActivity extends AppCompatActivity {

    /**
     * Which screen to open past Home, set by the widget and the launcher shortcuts. Not a
     * nav deep link: the start destination is decided here at runtime (see the class note),
     * so a deep link would fight it for the back stack.
     */
    public static final String EXTRA_OPEN = "com.householdsplitter.OPEN";
    public static final String OPEN_BALANCES = "balances";
    public static final String OPEN_NEW_ORDER = "new_order";

    private ActivityMainBinding binding;
    private volatile boolean startDestinationResolved;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Must run before super.onCreate. Holds the app's mark on screen until the first
        // frame is ready, so launch never shows a blank white window, and hands over to the
        // main theme afterwards.
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        // The very first launch has to read the database before it knows whether to open
        // onboarding or Home. Keeping the splash up for that read means the user never sees
        // the wrong screen appear and then get replaced.
        splashScreen.setKeepOnScreenCondition(() -> !startDestinationResolved);

        // Draw behind the system bars. Each screen then insets itself from the real bar
        // heights rather than assuming any, which is what keeps a title off the clock on
        // one device and off a gesture bar on another.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState != null) {
            // The NavHostFragment restores its own back stack; do not re-point the graph.
            startDestinationResolved = true;
            return;
        }

        ServiceLocator locator = ((SplitterApp) getApplication()).serviceLocator();
        // SPEC 4.7: no database work on the main thread, not even a count.
        locator.executors().diskIO().execute(() -> {
            com.householdsplitter.data.entity.Household household =
                    locator.database().householdDao().getHouseholdSync();
            boolean hasHousehold = household != null;
            if (hasHousehold) {
                locator.currentHouseholdId(household.id);
            }
            locator.executors().mainThread().execute(() -> {
                if (binding == null || isFinishing()) {
                    startDestinationResolved = true;
                    return;
                }
                NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host);
                if (host == null) {
                    startDestinationResolved = true;
                    return;
                }
                NavController controller = host.getNavController();
                NavGraph graph = controller.getNavInflater().inflate(R.navigation.nav_graph);
                // Three cases, not two: a household means Home (SPEC 7.3.1); no household
                // and no introduction read means start by explaining what this is; no
                // household but the introduction already read means straight to S1
                // (SPEC 7.1.1), because nobody should have to read it twice.
                int start;
                if (hasHousehold) {
                    start = R.id.homeFragment;
                } else if (locator.settings().welcomeSeen()) {
                    start = R.id.setupGroupFragment;
                } else {
                    start = R.id.welcomeFragment;
                }
                graph.setStartDestination(start);
                controller.setGraph(graph);
                startDestinationResolved = true;

                // SPEC 8.1.2: the primary real-world path. Screenshot the Walmart app, share
                // it here, and land on the import screen with the images already loaded.
                ArrayList<String> shared = sharedImageUris(getIntent());
                if (!shared.isEmpty() && hasHousehold) {
                    offerToAddToDraft(locator, controller, shared);
                    return;
                }
                if (hasHousehold) {
                    openRequestedScreen(controller, getIntent());
                }
            });
        });
    }

    /**
     * Follows a widget tap or a launcher shortcut, once Home is on the stack.
     *
     * <p>Navigating on top of Home rather than replacing it means Back goes where the user
     * expects: to their list of orders, not out of the app.
     */
    private void openRequestedScreen(NavController controller, Intent intent) {
        String open = intent == null ? null : intent.getStringExtra(EXTRA_OPEN);
        if (OPEN_BALANCES.equals(open)) {
            controller.navigate(R.id.analyticsFragment);
        } else if (OPEN_NEW_ORDER.equals(open)) {
            controller.navigate(R.id.importFragment);
        }
    }

    /**
     * SPEC 8.1.3: when there is already a draft, ask whether these screenshots belong to it.
     *
     * <p>Worth asking rather than guessing either way. Sharing a second batch into an order
     * half captured is the common case, and silently starting a new order would leave two
     * halves of one shop as two orders. Silently appending would be worse when the user
     * really has started shopping again.
     */
    private void offerToAddToDraft(ServiceLocator locator, NavController controller,
                                   ArrayList<String> shared) {
        locator.orderRepository().latestDraft(locator.currentHouseholdId(), draft -> {
            if (isFinishing()) {
                return;
            }
            Bundle args = new Bundle();
            args.putStringArrayList(ImportFragment.ARG_SHARED_URIS, shared);
            if (draft == null) {
                controller.navigate(R.id.importFragment, args);
                return;
            }
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.share_draft_title)
                    .setMessage(getString(R.string.share_draft_message, draft.label))
                    .setPositiveButton(R.string.share_draft_add, (dialog, which) -> {
                        args.putLong(com.householdsplitter.ui.parsing.ParsingArgs.ARG_ORDER_ID,
                                draft.id);
                        controller.navigate(R.id.importFragment, args);
                    })
                    .setNegativeButton(R.string.share_draft_new,
                            (dialog, which) -> controller.navigate(R.id.importFragment, args))
                    .setCancelable(false)
                    .show();
        });
    }

    /** SPEC 8.1.2: both ACTION_SEND and ACTION_SEND_MULTIPLE, single and multiple images. */
    private static ArrayList<String> sharedImageUris(Intent intent) {
        ArrayList<String> uris = new ArrayList<>();
        if (intent == null || intent.getType() == null || !intent.getType().startsWith("image/")) {
            return uris;
        }
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri single = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (single != null) {
                uris.add(single.toString());
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            List<Uri> many = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (many != null) {
                for (Uri uri : many) {
                    if (uri != null) {
                        uris.add(uri.toString());
                    }
                }
            }
        }
        return uris;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
