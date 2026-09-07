package com.householdsplitter.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * The three threads the app uses. SPEC 4.7.
 *
 * <p>No {@code AsyncTask}, and no work on the main thread. Room read methods return
 * LiveData and deliver on their own; every write goes through {@link #diskIO()} and every
 * OCR pass through {@link #parsing()} (SPEC 4.6, SPEC 7.5.2).
 */
public final class AppExecutors {

    private final Executor diskIO;
    private final Executor parsing;
    private final Executor mainThread;

    public AppExecutors() {
        this(Executors.newSingleThreadExecutor(),
                Executors.newFixedThreadPool(2),
                new MainThreadExecutor());
    }

    public AppExecutors(Executor diskIO, Executor parsing, Executor mainThread) {
        this.diskIO = diskIO;
        this.parsing = parsing;
        this.mainThread = mainThread;
    }

    /** Serialised, so ordered writes stay ordered. */
    public Executor diskIO() {
        return diskIO;
    }

    /** SPEC 7.5.2: OCR runs here, never on the main thread. */
    public Executor parsing() {
        return parsing;
    }

    public Executor mainThread() {
        return mainThread;
    }

    private static final class MainThreadExecutor implements Executor {

        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(Runnable command) {
            handler.post(command);
        }
    }
}
