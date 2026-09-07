package com.householdsplitter.di;

import android.content.Context;

import com.householdsplitter.util.AppExecutors;

/**
 * Hand-written dependency wiring. SPEC 4.8 offers Hilt or a ServiceLocator and requires
 * one to be picked and used everywhere; this project picks this one.
 *
 * <p>Reasoning: the codebase is Java-only by PROMPT hard rule 1, and Hilt would add a
 * Kotlin-based Gradle plugin plus a second annotation processor to a single-Activity app
 * with a handful of repositories. This is all Java, has no generated indirection to step
 * through in a debugger, and lets the JVM tests in :core construct what they need directly.
 */
public class ServiceLocator {

    private final Context applicationContext;
    private final AppExecutors executors;

    public ServiceLocator(Context context) {
        this.applicationContext = context.getApplicationContext();
        this.executors = new AppExecutors();
    }

    public Context applicationContext() {
        return applicationContext;
    }

    public AppExecutors executors() {
        return executors;
    }
}
