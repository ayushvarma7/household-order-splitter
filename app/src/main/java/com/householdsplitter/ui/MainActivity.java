package com.householdsplitter.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
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

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState != null) {
            // The NavHostFragment restores its own back stack; do not re-point the graph.
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
                    return;
                }
                NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host);
                if (host == null) {
                    return;
                }
                NavController controller = host.getNavController();
                NavGraph graph = controller.getNavInflater().inflate(R.navigation.nav_graph);
                graph.setStartDestination(
                        hasHousehold ? R.id.homeFragment : R.id.setupGroupFragment);
                controller.setGraph(graph);

                // SPEC 8.1.2: the primary real-world path. Screenshot the Walmart app, share
                // it here, and land on the import screen with the images already loaded.
                ArrayList<String> shared = sharedImageUris(getIntent());
                if (!shared.isEmpty() && hasHousehold) {
                    Bundle args = new Bundle();
                    args.putStringArrayList(ImportFragment.ARG_SHARED_URIS, shared);
                    controller.navigate(R.id.importFragment, args);
                }
            });
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
