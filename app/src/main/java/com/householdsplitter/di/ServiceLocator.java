package com.householdsplitter.di;

import android.content.Context;

import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.repo.HouseholdRepository;
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

    private HouseholdRepository householdRepository;

    public ServiceLocator(Context context) {
        this.applicationContext = context.getApplicationContext();
        this.executors = new AppExecutors();
        this.database = AppDatabase.build(applicationContext);
        this.settings = new SettingsStore(applicationContext);
    }

    public Context applicationContext() {
        return applicationContext;
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
}
