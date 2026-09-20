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

import android.graphics.Typeface;
import android.view.View;
import android.widget.Toast;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.householdsplitter.databinding.ItemDrawerGroupBinding;
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
        setUpDrawer();
        // Back closes the drawer before it does anything else, which is what every Android
        // user already expects and the only reading of back that is never surprising.
        getOnBackPressedDispatcher().addCallback(this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (isDrawerOpen()) {
                            closeDrawer();
                        } else {
                            setEnabled(false);
                            getOnBackPressedDispatcher().onBackPressed();
                            setEnabled(true);
                        }
                    }
                });

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
     * Opens the drawer. Called by the top level screen's hamburger.
     *
     * <p>Only that screen offers it. A drawer reachable from the middle of assigning an
     * order would make the back gesture ambiguous, and the way out of a detail screen
     * should be the way you came in.
     */
    public void openDrawer() {
        refreshDrawer();
        // Unlock first. openDrawer is a no-op while the lock mode says closed, which is
        // how the hamburger managed to do nothing at all on the first attempt.
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        binding.drawerLayout.openDrawer(GravityCompat.START);
    }

    public boolean isDrawerOpen() {
        return binding != null && binding.drawerLayout.isDrawerOpen(GravityCompat.START);
    }

    public void closeDrawer() {
        if (binding != null) {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    /** Wires the drawer's fixed actions once. The group list is rebuilt on each open. */
    private void setUpDrawer() {
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        // The drawer belongs to the top level screen only. Locking it everywhere else is
        // what stops an edge swipe halfway through assigning an order from pulling out a
        // menu, and stops back meaning two different things on the same screen.
        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host);
        if (navHost != null) {
            navHost.getNavController().addOnDestinationChangedListener(
                    (controller, destination, arguments) -> {
                        if (binding == null) {
                            return;
                        }
                        boolean topLevel = destination.getId() == R.id.homeFragment;
                        binding.drawerLayout.setDrawerLockMode(topLevel
                                ? DrawerLayout.LOCK_MODE_UNLOCKED
                                : DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
                        if (!topLevel && isDrawerOpen()) {
                            closeDrawer();
                        }
                    });
        }

        // Insets by hand: the drawer sits behind the status bar and the gesture bar, and
        // the rule in this project is padding for a container, margin for a control.
        ViewCompat.setOnApplyWindowInsetsListener(binding.navDrawer.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.navDrawer.drawerScroll.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });

        binding.navDrawer.drawerNewGroup.setOnClickListener(v -> {
            closeDrawer();
            navigate(R.id.setupGroupFragment, SetupGroupFragment.argsForAnotherGroup());
        });
        binding.navDrawer.drawerSpending.setOnClickListener(v -> go(R.id.analyticsFragment));
        binding.navDrawer.drawerPeople.setOnClickListener(v -> go(R.id.setupMembersFragment));
        binding.navDrawer.drawerRules.setOnClickListener(v -> go(R.id.rulesFragment));
        binding.navDrawer.drawerSettings.setOnClickListener(v -> go(R.id.settingsFragment));
        // Export lives on the Settings screen, which is where the overflow item pointed
        // too. The drawer names it separately because that is what people look for.
        binding.navDrawer.drawerExport.setOnClickListener(v -> go(R.id.settingsFragment));
    }

    private void go(int destination) {
        closeDrawer();
        navigate(destination, null);
    }

    private void navigate(int destination, Bundle args) {
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host);
        if (host != null) {
            host.getNavController().navigate(destination, args);
        }
    }

    /**
     * Rebuilds the group list from the database each time the drawer opens.
     *
     * <p>Cheap, and it means a group renamed on another screen is never shown under its old
     * name. Reading on the disk executor because SPEC 4.7 does not make an exception for
     * counts.
     */
    private void refreshDrawer() {
        ServiceLocator locator = ((SplitterApp) getApplication()).serviceLocator();
        long current = locator.currentHouseholdId();
        locator.executors().diskIO().execute(() -> {
            List<com.householdsplitter.data.entity.Household> groups =
                    locator.database().householdDao().getAllSync();
            com.householdsplitter.data.entity.Household active =
                    locator.database().householdDao().getByIdSync(current);
            int people = active == null ? 0
                    : locator.database().memberDao().getActiveSync(current).size();
            locator.executors().mainThread().execute(() -> {
                if (binding == null || isFinishing()) {
                    return;
                }
                binding.navDrawer.drawerGroupName.setText(active == null ? getString(R.string.app_name)
                        : active.name);
                binding.navDrawer.drawerGroupMeta.setText(
                        getResources().getString(R.string.drawer_people_count, people));
                bindGroups(groups, current);
            });
        });
    }

    private void bindGroups(List<com.householdsplitter.data.entity.Household> groups,
                            long current) {
        binding.navDrawer.drawerGroupList.removeAllViews();
        for (com.householdsplitter.data.entity.Household group : groups) {
            ItemDrawerGroupBinding row = ItemDrawerGroupBinding.inflate(
                    getLayoutInflater(), binding.navDrawer.drawerGroupList, false);
            row.groupName.setText(group.name);
            row.groupInitial.setText(group.name.isEmpty()
                    ? "?" : group.name.substring(0, 1).toUpperCase());
            boolean isCurrent = group.id == current;
            // A tick as well as emphasis, so which group you are in does not depend on
            // noticing a weight change.
            row.groupTick.setVisibility(isCurrent ? View.VISIBLE : View.GONE);
            row.groupName.setTypeface(null, isCurrent ? Typeface.BOLD : Typeface.NORMAL);
            row.getRoot().setOnClickListener(v -> {
                closeDrawer();
                if (!isCurrent) {
                    switchToGroup(group);
                }
            });
            binding.navDrawer.drawerGroupList.addView(row.getRoot());
        }
    }

    /**
     * Switches group and returns to the top level screen.
     *
     * <p>Rebuilds the back stack rather than pushing, because everything behind the current
     * screen belongs to the group being left. Going back into another group's order would
     * be the worst kind of bug in an app about who owes what.
     */
    private void switchToGroup(com.householdsplitter.data.entity.Household group) {
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
