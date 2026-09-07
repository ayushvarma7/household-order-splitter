package com.householdsplitter.ui.settings;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.snackbar.Snackbar;
import com.householdsplitter.BuildConfig;
import com.householdsplitter.R;
import com.householdsplitter.backup.BackupService;
import com.householdsplitter.core.calc.AllocationMode;
import com.householdsplitter.databinding.FragmentSettingsBinding;
import com.householdsplitter.export.ExportService;
import com.householdsplitter.export.WorkbookService;
import com.householdsplitter.parse.ParserFactory;
import com.householdsplitter.prefs.SettingsStore;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.common.Insets;

/** S14. SPEC 7.14. */
public class SettingsFragment extends BaseFragment {

    private FragmentSettingsBinding binding;
    private SettingsStore settings;
    private BackupService backup;
    private ExportService exportService;
    private WorkbookService workbookService;
    private ActivityResultLauncher<String> createWorkbook;

    private ActivityResultLauncher<String> createBackup;
    private ActivityResultLauncher<String[]> openBackup;
    private ActivityResultLauncher<String> createCsv;
    private String pendingCsv;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createBackup = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"), this::doExport);
        openBackup = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), this::askMergeOrReplace);
        createWorkbook = registerForActivityResult(
                new ActivityResultContracts.CreateDocument(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                uri -> {
                    if (uri == null) {
                        return;
                    }
                    // Hold on to the grant, so the app can keep rewriting this same file
                    // later without asking again.
                    try {
                        requireContext().getContentResolver().takePersistableUriPermission(uri,
                                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                        | android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException notPersistable) {
                        // Some providers do not offer one. It still works for this session.
                    }
                    writeWorkbook(uri);
                });
        createCsv = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
                    if (uri != null && pendingCsv != null) {
                        exportService.writeToUri(uri, pendingCsv, result -> toast(result.isOk()
                                ? R.string.export_saved : R.string.export_failed));
                    }
                    pendingCsv = null;
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        settings = locator().settings();

        Insets.padTop(binding.toolbar);
        Insets.padBottomScrollable(binding.scroll);
        backup = new BackupService(locator().database(), locator().executors(),
                requireContext().getContentResolver());
        exportService = new ExportService(locator().orderRepository(), settings,
                locator().executors(), requireContext().getContentResolver());
        workbookService = locator().workbookService();

        binding.toolbar.setNavigationOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        // SPEC 7.14.1
        binding.allocationProportional.setChecked(
                settings.allocationMode() == AllocationMode.PROPORTIONAL);
        binding.allocationEqual.setChecked(settings.allocationMode() == AllocationMode.EQUAL);
        binding.allocationGroup.setOnCheckedChangeListener((group, checkedId) ->
                settings.allocationMode(checkedId == R.id.allocationEqual
                        ? AllocationMode.EQUAL : AllocationMode.PROPORTIONAL));

        // SPEC 7.14.2 and 8.10.3: without a key the cloud option disables itself and says why.
        boolean cloudAvailable = ParserFactory.isCloudAvailable();
        binding.parserCloud.setEnabled(cloudAvailable);
        binding.parserCloud.setChecked(cloudAvailable && settings.useCloudParser());
        binding.parserOnDevice.setChecked(!binding.parserCloud.isChecked());
        binding.parserNote.setText(cloudAvailable
                ? getString(R.string.settings_parser_cloud_available)
                : ParserFactory.unavailableReason());
        binding.parserGroup.setOnCheckedChangeListener((group, checkedId) ->
                settings.useCloudParser(checkedId == R.id.parserCloud));

        // SPEC 7.14.3
        binding.currencyInput.setText(settings.currencySymbol());
        binding.currencySave.setOnClickListener(v -> {
            String symbol = binding.currencyInput.getText() == null
                    ? "" : binding.currencyInput.getText().toString().trim();
            if (!symbol.isEmpty()) {
                settings.currencySymbol(symbol);
                toast(R.string.settings_saved);
            }
        });

        // The Excel workbook.
        binding.workbookAuto.setChecked(settings.workbookAutoUpdate());
        binding.workbookAuto.setOnCheckedChangeListener((button, checked) ->
                settings.workbookAutoUpdate(checked));
        binding.workbookStatus.setText(workbookService.hasDestination()
                ? R.string.settings_workbook_set : R.string.settings_workbook_none);
        binding.workbookUpdateButton.setEnabled(workbookService.hasDestination());
        binding.workbookChooseButton.setOnClickListener(v ->
                createWorkbook.launch("household-orders.xlsx"));
        binding.workbookUpdateButton.setOnClickListener(v -> writeWorkbook(null));

        // SPEC 7.14.4
        binding.backupButton.setOnClickListener(v -> createBackup.launch("order-splitter-backup.json"));
        binding.restoreButton.setOnClickListener(v ->
                openBackup.launch(new String[]{"application/json", "text/plain", "*/*"}));

        // SPEC 7.11.3
        binding.exportAllButton.setOnClickListener(v -> exportAll());

        // SPEC 9.5
        binding.clearSuggestionsButton.setOnClickListener(v ->
                locator().assignmentMemory().clear(locator().currentHouseholdId(),
                        ignored -> toast(R.string.settings_suggestions_cleared)));

        // Standing rules, with a live count so the card says whether any are in force.
        binding.rulesButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.rulesFragment));
        locator().ruleService().observe(locator().currentHouseholdId())
                .observe(getViewLifecycleOwner(), rules -> {
                    if (binding == null) {
                        return;
                    }
                    int count = rules == null ? 0 : rules.size();
                    binding.rulesCount.setText(count == 0
                            ? getString(R.string.settings_rules_none)
                            : getString(R.string.settings_rules_count, count));
                });

        // SPEC 7.14.5
        binding.wipeButton.setOnClickListener(v -> confirmWipe());

        // SPEC 7.14.6
        binding.aboutText.setText(getString(R.string.settings_about,
                BuildConfig.VERSION_NAME));
    }

    private void exportAll() {
        locator().householdRepository().observeHousehold().observe(getViewLifecycleOwner(),
                household -> {
                    if (household == null) {
                        return;
                    }
                    exportService.buildCsvForAll(household.id, household.name, result -> {
                        if (result.isOk()) {
                            pendingCsv = result.value();
                            createCsv.launch("all-orders-split.csv");
                        } else {
                            toast(R.string.export_failed);
                        }
                    });
                });
    }

    /** @param target a newly chosen file, or null to rewrite the one already saved */
    private void writeWorkbook(Uri target) {
        locator().householdRepository().observeHousehold().observe(getViewLifecycleOwner(),
                household -> {
                    if (household == null) {
                        return;
                    }
                    com.householdsplitter.util.Callback<
                            com.householdsplitter.util.Result<WorkbookService.Written>> done =
                            result -> {
                                if (binding == null) {
                                    return;
                                }
                                if (result.isOk()) {
                                    binding.workbookStatus.setText(R.string.settings_workbook_set);
                                    binding.workbookUpdateButton.setEnabled(true);
                                    Snackbar.make(binding.getRoot(), getString(
                                                    R.string.workbook_written,
                                                    result.value().sheetCount,
                                                    result.value().orderCount),
                                            Snackbar.LENGTH_LONG).show();
                                } else {
                                    Snackbar.make(binding.getRoot(),
                                            String.valueOf(result.error()),
                                            Snackbar.LENGTH_LONG).show();
                                }
                            };
                    if (target == null) {
                        workbookService.refresh(household.id, household.name, done);
                    } else {
                        workbookService.writeTo(household.id, household.name, target, done);
                    }
                });
    }

    private void doExport(Uri uri) {
        if (uri == null) {
            return;
        }
        backup.export(uri, result -> toast(result.isOk()
                ? R.string.export_saved : R.string.export_failed));
    }

    /** SPEC 7.14.4: the merge-or-replace choice. */
    private void askMergeOrReplace(Uri uri) {
        if (uri == null) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.restore_title)
                .setMessage(R.string.restore_message)
                .setPositiveButton(R.string.restore_replace, (d, w) -> restore(uri, true))
                .setNeutralButton(R.string.restore_merge, (d, w) -> restore(uri, false))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void restore(Uri uri, boolean replace) {
        backup.restore(uri, replace, result -> {
            if (result.isOk()) {
                toast(R.string.restore_done);
            } else if (binding != null) {
                Snackbar.make(binding.getRoot(), String.valueOf(result.error()),
                        Snackbar.LENGTH_LONG).show();
            }
        });
    }

    /** SPEC 7.14.5: behind a typed confirmation, not just a tap. */
    private void confirmWipe() {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_single_input, null, false);
        EditText field = content.findViewById(R.id.dialogInput);
        com.google.android.material.textfield.TextInputLayout layout =
                content.findViewById(R.id.dialogInputLayout);
        layout.setHint(getString(R.string.wipe_confirm_hint));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.wipe_title)
                .setMessage(R.string.wipe_message)
                .setView(content)
                .setPositiveButton(R.string.wipe_action, (d, w) -> {
                    String typed = field.getText() == null ? "" : field.getText().toString().trim();
                    if (!typed.equalsIgnoreCase(getString(R.string.wipe_confirm_word))) {
                        toast(R.string.wipe_not_confirmed);
                        return;
                    }
                    backup.wipe(result -> {
                        settings.clear();
                        locator().currentHouseholdId(0L);
                        requireActivity().finishAffinity();
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        dialog.show();
    }

    private void toast(int messageRes) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), messageRes, Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
