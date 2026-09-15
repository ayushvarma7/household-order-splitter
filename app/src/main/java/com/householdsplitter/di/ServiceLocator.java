package com.householdsplitter.di;

import android.content.Context;

import com.householdsplitter.backup.AutoBackupService;
import com.householdsplitter.backup.BackupService;
import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.repo.HouseholdRepository;
import com.householdsplitter.data.repo.OrderRepository;
import com.householdsplitter.data.repo.SettlementRepository;
import com.householdsplitter.core.parse.StoreKind;
import com.householdsplitter.parse.ParserFactory;
import com.householdsplitter.quality.MissDiagnosisService;
import com.householdsplitter.quality.ParserQualityService;
import com.householdsplitter.suggest.AssignmentMemoryService;
import com.householdsplitter.suggest.RuleService;
import com.householdsplitter.parse.ReceiptParser;
import com.householdsplitter.export.WorkbookService;
import com.householdsplitter.prefs.SettingsStore;
import com.householdsplitter.util.AppExecutors;

/**
 * Hand-written dependency wiring. SPEC 4.8 offers Hilt or a ServiceLocator and requires one
 * to be picked and used everywhere; this project picks this one.
 *
 * <p>Reasoning: the codebase is Java-only by PROMPT hard rule 1, and Hilt would add a
 * Kotlin-based Gradle plugin plus a second annotation processor to a single-Activity app
 * with a handful of repositories. This is all Java, has no generated indirection to step
 * through in a debugger, and lets the JVM tests in :core construct what they need directly.
 */
public class ServiceLocator {

    private final Context applicationContext;
    private final AppExecutors executors;
    private final AppDatabase database;
    private final SettingsStore settings;

    /**
     * The household the user is working in. There is exactly one (SPEC 1.5), so caching its
     * id here saves threading it through every nav argument. It is set by MainActivity at
     * launch and by S1 the moment a household is created; a fresh install leaves it at 0.
     */
    private volatile long currentHouseholdId;

    private HouseholdRepository householdRepository;
    private OrderRepository orderRepository;
    private AssignmentMemoryService assignmentMemory;
    private WorkbookService workbookService;
    private SettlementRepository settlementRepository;
    private RuleService ruleService;
    private BackupService backupService;
    private AutoBackupService autoBackupService;

    public ServiceLocator(Context context) {
        this.applicationContext = context.getApplicationContext();
        this.executors = new AppExecutors();
        this.database = AppDatabase.build(applicationContext);
        this.settings = new SettingsStore(applicationContext);
    }

    public Context applicationContext() {
        return applicationContext;
    }

    public long currentHouseholdId() {
        return currentHouseholdId;
    }

    /**
     * Sets the group being looked at, and remembers it across restarts.
     *
     * <p>The field stays because everything reads it on the main thread and a preference
     * read per call would be silly. Writing through means the choice is not lost when the
     * process is, which is the difference between switching groups and merely filtering.
     */
    public void currentHouseholdId(long value) {
        this.currentHouseholdId = value;
        settings.currentHouseholdId(value);
    }

    public AppExecutors executors() {
        return executors;
    }

    public AppDatabase database() {
        return database;
    }

    public SettingsStore settings() {
        return settings;
    }

    public synchronized HouseholdRepository householdRepository() {
        if (householdRepository == null) {
            householdRepository = new HouseholdRepository(
                    database.householdDao(),
                    database.memberDao(),
                    database.assignmentDao(),
                    executors);
        }
        return householdRepository;
    }

    public synchronized OrderRepository orderRepository() {
        if (orderRepository == null) {
            orderRepository = new OrderRepository(
                    database.orderDao(),
                    database.orderImageDao(),
                    database.lineItemDao(),
                    database.participantDao(),
                    database.assignmentDao(),
                    database.memberDao(),
                    database.discardedRowDao(),
                    executors);
        }
        return orderRepository;
    }

    public synchronized SettlementRepository settlementRepository() {
        if (settlementRepository == null) {
            settlementRepository = new SettlementRepository(database.settlementDao(), executors);
        }
        return settlementRepository;
    }

    public synchronized WorkbookService workbookService() {
        if (workbookService == null) {
            workbookService = new WorkbookService(orderRepository(), settlementRepository(),
                    settings, executors, applicationContext.getContentResolver());
        }
        return workbookService;
    }

    public synchronized BackupService backupService() {
        if (backupService == null) {
            backupService = new BackupService(database, executors,
                    applicationContext.getContentResolver());
        }
        return backupService;
    }

    public synchronized AutoBackupService autoBackupService() {
        if (autoBackupService == null) {
            autoBackupService = new AutoBackupService(backupService(), settings, executors,
                    applicationContext.getContentResolver());
        }
        return autoBackupService;
    }

    public synchronized RuleService ruleService() {
        if (ruleService == null) {
            ruleService = new RuleService(database.memberRuleDao(), executors);
        }
        return ruleService;
    }

    public synchronized AssignmentMemoryService assignmentMemory() {
        if (assignmentMemory == null) {
            assignmentMemory = new AssignmentMemoryService(
                    database.assignmentMemoryDao(), executors);
        }
        return assignmentMemory;
    }

    /**
     * SPEC 7.14.2 and 8.10.1: the parser is chosen by a setting, and both implementations
     * satisfy the same interface. In the standard flavour the cloud option is not on the
     * classpath at all, so this always returns the on-device one.
     */
    /**
     * Built on demand. It holds no state, and the report it produces is recomputed from the
     * database every time rather than cached, so there is nothing to keep alive.
     */
    /**
     * Re-reads an order's screenshots to explain a row the reader missed. Built on demand:
     * it opens an ML Kit recogniser per diagnosis and closes it again.
     */
    public MissDiagnosisService missDiagnosisService() {
        return new MissDiagnosisService(applicationContext, database.orderDao(),
                database.orderImageDao(), database.lineItemDao(), executors());
    }

    public ParserQualityService parserQualityService() {
        return new ParserQualityService(database.orderDao(), database.lineItemDao(),
                database.discardedRowDao(), executors());
    }

    public ReceiptParser receiptParser() {
        return receiptParser(StoreKind.WALMART);
    }

    /**
     * The reader for one store's pages.
     *
     * <p>Built per parse rather than held as a singleton, because the store is chosen per
     * order. A cached parser would read the second order with the first order's vocabulary.
     */
    public ReceiptParser receiptParser(StoreKind store) {
        return ParserFactory.create(applicationContext, store);
    }
}
