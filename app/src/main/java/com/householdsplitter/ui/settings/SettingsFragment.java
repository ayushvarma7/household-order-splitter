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
import androidx.appcompat.app.AppCompatDelegate;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.snackbar.Snackbar;
import com.householdsplitter.BuildConfig;
import android.widget.Toast;

import com.householdsplitter.R;
import com.householdsplitter.backup.AutoBackupService;
import com.householdsplitter.backup.BackupService;
import com.householdsplitter.core.calc.AllocationMode;
import com.householdsplitter.databinding.FragmentSettingsBinding;
import com.householdsplitter.export.ExportService;
import com.householdsplitter.export.WorkbookService;
import com.householdsplitter.parse.ParserFactory;
import com.householdsplitter.prefs.SettingsStore;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.common.Insets;
import com.householdsplitter.ui.common.StateColors;

/** S14. SPEC 7.14. */
public class SettingsFragment extends BaseFragment {

    /** Seven, which is Android's own gesture for revealing developer options. */
    private static final int TAPS_TO_REVEAL = 7;

    private FragmentSettingsBinding binding;
    private SettingsStore settings;
    private BackupService backup;
    private ExportService exportService;
    private WorkbookService workbookService;
    private ActivityResultLauncher<String> createWorkbook;

    private ActivityResultLauncher<String> createBackup;
    private ActivityResultLauncher<String[]> openBackup;
    private ActivityResultLauncher<String> createCsv;
    private ActivityResultLauncher<android.net.Uri> chooseBackupFolder;
    private String pendingCsv;
    private AutoBackupService autoBackup;

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
        chooseBackupFolder = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(), uri -> {
                    if (uri == null) {
                        return;
                    }
                    // Without a persisted grant the folder is unusable the next time the
                    // app starts, which would make the backups stop without saying so.
                    try {
                        requireContext().getContentResolver().takePersistableUriPermission(uri,
                                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                        | android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException notPersistable) {
                        // Some providers do not offer one. It still works for this session.
                    }
                    autoBackup.useFolder(uri, result -> {
                        if (binding == null) {
                            return;
                        }
                        if (result.isOk()) {
                            toastText(getString(R.string.settings_autobackup_done,
                                    result.value()));
                        } else {
                            toastText(getString(R.string.settings_autobackup_failed,
                                    result.error()));
                        }
                        renderAutoBackup();
                    });
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

        renderTheme();
        binding.themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int mode = checkedId == R.id.themeLight
                    ? AppCompatDelegate.MODE_NIGHT_NO
                    : checkedId == R.id.themeDark
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (mode == settings.themeMode()) {
                return;
            }
            settings.themeMode(mode);
            // Recreates the Activity, which is how the new theme reaches every screen.
            AppCompatDelegate.setDefaultNightMode(mode);
        });

        autoBackup = locator().autoBackupService();
        binding.autoBackupChooseButton.setOnClickListener(v -> chooseBackupFolder.launch(null));
        binding.autoBackupNowButton.setOnClickListener(v -> autoBackup.backupNow(result -> {
            if (binding == null) {
                return;
            }
            toastText(result.isOk()
                    ? getString(R.string.settings_autobackup_done, result.value())
                    : getString(R.string.settings_autobackup_failed, result.error()));
            renderAutoBackup();
        }));
        binding.autoBackupSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!button.isPressed()) {
                return;
            }
            settings.autoBackupEnabled(checked);
            renderAutoBackup();
        });
        renderAutoBackup();

        // SPEC 7.14.5
        binding.wipeButton.setOnClickListener(v -> confirmWipe());

        // SPEC 7.14.6
        bindParserReportGesture();
        binding.aboutText.setText(getString(R.string.settings_about,
                BuildConfig.VERSION_NAME));
    }

    private void renderTheme() {
        int mode = settings.themeMode();
        binding.themeGroup.check(mode == AppCompatDelegate.MODE_NIGHT_NO ? R.id.themeLight
                : mode == AppCompatDelegate.MODE_NIGHT_YES ? R.id.themeDark
                : R.id.themeSystem);
    }

    /**
     * The status line is the only place an absent backup becomes visible, since the backup
     * itself is deliberately silent. So it says plainly when nothing is being kept.
     */
    private void renderAutoBackup() {
        if (binding == null) {
            return;
        }
        boolean hasFolder = autoBackup.hasFolder();
        boolean enabled = settings.autoBackupEnabled();
        binding.autoBackupSwitch.setChecked(enabled);
        binding.autoBackupSwitch.setEnabled(hasFolder);
        binding.autoBackupNowButton.setEnabled(hasFolder);
        binding.autoBackupChooseButton.setText(hasFolder
                ? R.string.settings_autobackup_change : R.string.settings_autobackup_choose);

        if (!hasFolder) {
            binding.autoBackupStatus.setText(R.string.settings_autobackup_off);
            binding.autoBackupStatus.setTextColor(
                    StateColors.content(requireContext(), StateColors.State.WARNING));
            return;
        }
        if (!enabled) {
            binding.autoBackupStatus.setText(R.string.settings_autobackup_paused);
            binding.autoBackupStatus.setTextColor(
                    StateColors.content(requireContext(), StateColors.State.WARNING));
            return;
        }
        long last = settings.lastAutoBackupAt();
        if (last <= 0L) {
            binding.autoBackupStatus.setText(R.string.settings_autobackup_never);
            binding.autoBackupStatus.setTextColor(
                    StateColors.content(requireContext(), StateColors.State.NEUTRAL));
            return;
        }
        autoBackup.countBackups(count -> {
            if (binding == null) {
                return;
            }
            if (count == null || count < 0) {
                binding.autoBackupStatus.setText(R.string.settings_autobackup_unreadable);
                binding.autoBackupStatus.setTextColor(
                        StateColors.content(requireContext(), StateColors.State.DANGER));
                return;
            }
            String when = android.text.format.DateUtils.getRelativeTimeSpanString(
                    last, System.currentTimeMillis(),
                    android.text.format.DateUtils.DAY_IN_MILLIS).toString();
            binding.autoBackupStatus.setText(getString(R.string.settings_autobackup_last,
                    when.toLowerCase(java.util.Locale.getDefault()),
                    getResources().getQuantityString(
                            R.plurals.settings_autobackup_count, count, count)));
            binding.autoBackupStatus.setTextColor(
                    StateColors.content(requireContext(), StateColors.State.SUCCESS));
        });
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

    /** For messages that carry a value, such as which file a backup went to. */
    private void toastText(String message) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /**
     * The parser report, behind a deliberate gesture rather than a button.
     *
     * <p>Hidden rather than locked, and the distinction is the answer to whether this needs
     * a password. There is no account here, no server and no second device: the data never
     * leaves the phone, and the phone already has a lock screen. A password would be a new
     * secret to store and lose in exchange for protection that is already in place. What
     * this actually needs is to stay out of the way of a housemate who opened the app to
     * find out what they owe, and seven taps on the About line does that.
     */
    private void bindParserReportGesture() {
        final int[] taps = {0};
        binding.aboutText.setOnClickListener(v -> {
            taps[0]++;
            if (taps[0] == TAPS_TO_REVEAL) {
                Toast.makeText(requireContext(), R.string.parser_report_revealed,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (taps[0] > TAPS_TO_REVEAL) {
                showParserReport();
            }
        });
    }

    private void showParserReport() {
        locator().parserQualityService().report(locator().currentHouseholdId(), report -> {
            if (!isAdded()) {
                return;
            }
            ParserReportSheet.of(report, locator().settings().currencySymbol())
                    .show(getChildFragmentManager(), "parser-report");
        });
    }
}
