package com.householdsplitter.di;

import android.content.Context;

import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.repo.HouseholdRepository;
import com.householdsplitter.data.repo.OrderRepository;
import com.householdsplitter.data.repo.SettlementRepository;
import com.householdsplitter.parse.ParserFactory;
import com.householdsplitter.suggest.AssignmentMemoryService;
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

    public void currentHouseholdId(long value) {
        this.currentHouseholdId = value;
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
    public ReceiptParser receiptParser() {
        return ParserFactory.create(applicationContext, settings.useCloudParser());
    }
}
