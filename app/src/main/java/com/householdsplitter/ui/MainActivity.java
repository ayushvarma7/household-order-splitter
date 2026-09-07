package com.householdsplitter.ui;

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
            boolean hasHousehold = locator.database().householdDao().countSync() > 0;
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
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
