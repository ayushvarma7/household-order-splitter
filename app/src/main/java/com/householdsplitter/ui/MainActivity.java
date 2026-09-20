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

import android.view.View;
import android.widget.Toast;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.householdsplitter.ui.setup.SetupGroupFragment;
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
    /**
     * Straight to photographing a restaurant bill, skipping the store picker.
     *
     * <p>It has its own entry point because of where it is triggered from. The store picker
     * is one tap and is the right thing inside the app, where the user has already decided
     * to add an order. From a lock screen or a quick settings tile the user is standing at a
     * table with the bill in their hand, and every screen between the tap and the camera is
     * a screen they are holding a restaurant up for.
     */
    public static final String OPEN_SCAN_BILL = "scan_bill";

    private ActivityMainBinding binding;
    private volatile boolean startDestinationResolved;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Must run before super.onCreate. Holds the app's mark on screen until the first
        // frame is ready, so launch never shows a blank white window, and hands over to the
        // main theme afterwards.
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        // Before the first view is inflated, or half the screen keeps the old accent.
        // AMETHYST returns zero and applies nothing, which is how the original scheme
        // stays byte for byte what it was.
        int overlay = ((SplitterApp) getApplication()).serviceLocator()
                .settings().palette().overlayRes();
        if (overlay != 0) {
            getTheme().applyStyle(overlay, true);
        }

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
        setUpBottomNav();

        if (savedInstanceState != null) {
            // The NavHostFragment restores its own back stack; do not re-point the graph.
            startDestinationResolved = true;
            return;
        }

        ServiceLocator locator = ((SplitterApp) getApplication()).serviceLocator();
        // SPEC 4.7: no database work on the main thread, not even a count.
        locator.executors().diskIO().execute(() -> {
            // The group the user last chose, if they ever chose one, and otherwise the
            // first that exists. An install that has only ever had one group stores
            // nothing here and so takes exactly the path it always did.
            com.householdsplitter.data.dao.HouseholdDao households =
                    locator.database().householdDao();
            long remembered = locator.settings().currentHouseholdId();
            com.householdsplitter.data.entity.Household household =
                    remembered == 0L ? null : households.getByIdSync(remembered);
            if (household == null) {
                // Either nothing was remembered, or the remembered group has since been
                // deleted. Falling back beats opening a screen about a group that is gone.
                household = households.getHouseholdSync();
            }
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
        } else if (OPEN_SCAN_BILL.equals(open)) {
            Bundle args = new Bundle();
            args.putString(com.householdsplitter.ui.parsing.ParsingArgs.ARG_STORE,
                    com.householdsplitter.core.parse.StoreKind.RESTAURANT.name());
            controller.navigate(R.id.importFragment, args);
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

    // ---- navigation drawer ---------------------------------------------------------------

    /**
     * Wires the bar across the foot of the screen.
     *
     * <p>Four destinations and nothing else at the top level. Selecting one pops back to
     * it rather than pushing, which is what stops the back stack growing by one every time
     * somebody looks at spending and comes back: a tab is a place, not a step.
     *
     * <p>The bar is hidden everywhere below the top level. Halfway through assigning an
     * order there is nowhere to go except forward or back, and a bar offering to leave the
     * order entirely is an invitation to lose the work in progress.
     */
    private void setUpBottomNav() {
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host);
        if (host == null) {
            return;
        }
        NavController controller = host.getNavController();

        // Insets are handed out from here rather than left to each view, because with a
        // bar in the way there are two different right answers and only this level knows
        // which applies.
        //
        // When the bar is showing it is the thing at the bottom of the screen, so it takes
        // the gesture inset as padding, and the fragment above it is handed insets with
        // the bottom removed: it does not reach the gesture area, and a screen that pads
        // its own footer would otherwise clear the gesture bar a second time and float its
        // buttons an inch off the floor.
        //
        // When the bar is hidden the fragment really is the thing at the bottom, and it
        // gets the insets untouched.
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            boolean barShowing = binding.bottomNav.getVisibility() == View.VISIBLE;
            binding.bottomNav.setPadding(0, 0, 0, barShowing ? bars.bottom : 0);

            WindowInsetsCompat forHost = barShowing
                    ? new WindowInsetsCompat.Builder(insets)
                            .setInsets(WindowInsetsCompat.Type.systemBars(),
                                    Insets.of(bars.left, bars.top, bars.right, 0))
                            .build()
                    : insets;
            ViewCompat.dispatchApplyWindowInsets(binding.navHost, forHost);
            return insets;
        });

        binding.bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == controller.getGraph().getStartDestinationId()
                    && controller.getCurrentDestination() != null
                    && controller.getCurrentDestination().getId() == item.getItemId()) {
                return true;
            }
            controller.navigate(item.getItemId(), null,
                    new androidx.navigation.NavOptions.Builder()
                            .setLaunchSingleTop(true)
                            .setPopUpTo(R.id.homeFragment, false)
                            .build());
            return true;
        });
        // Reselecting the tab you are already on does nothing, rather than reloading the
        // screen under the user's finger.
        binding.bottomNav.setOnItemReselectedListener(item -> { });

        controller.addOnDestinationChangedListener((c, destination, arguments) -> {
            if (binding == null) {
                return;
            }
            boolean topLevel = isTopLevel(destination.getId());
            boolean wasShowing = binding.bottomNav.getVisibility() == View.VISIBLE;
            binding.bottomNav.setVisibility(topLevel ? View.VISIBLE : View.GONE);
            if (wasShowing != topLevel) {
                // The right answer about insets has just changed, and nothing re-asks on
                // its own when a sibling's visibility does.
                ViewCompat.requestApplyInsets(binding.getRoot());
            }
            if (topLevel) {
                // Checked without firing the listener, which would navigate again.
                binding.bottomNav.getMenu().findItem(destination.getId()).setChecked(true);
            }
        });
    }

    /** The four destinations the bar offers, and the only ones it is shown on. */
    private static boolean isTopLevel(int destinationId) {
        return destinationId == R.id.homeFragment
                || destinationId == R.id.groupsFragment
                || destinationId == R.id.analyticsFragment
                || destinationId == R.id.settingsFragment;
    }

    private void navigate(int destination, Bundle args) {
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host);
        if (host != null) {
            host.getNavController().navigate(destination, args);
        }
    }

    /**
     * Switches group and returns to the top level screen.
     *
     * <p>Rebuilds the back stack rather than pushing, because everything behind the current
     * screen belongs to the group being left. Going back into another group's order would
     * be the worst kind of bug in an app about who owes what.
     */
    public void switchToGroup(com.householdsplitter.data.entity.Household group) {
        ServiceLocator locator = ((SplitterApp) getApplication()).serviceLocator();
        locator.currentHouseholdId(group.id);
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host);
        if (host != null) {
            NavController controller = host.getNavController();
            controller.navigate(R.id.homeFragment, null,
                    new androidx.navigation.NavOptions.Builder()
                            .setPopUpTo(controller.getGraph().getStartDestinationId(), true)
                            .build());
        }
        Toast.makeText(this, getString(R.string.drawer_switched, group.name),
                Toast.LENGTH_SHORT).show();
    }
}
