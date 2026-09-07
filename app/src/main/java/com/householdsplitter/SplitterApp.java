package com.householdsplitter;

import android.app.Application;

import com.householdsplitter.di.ServiceLocator;

/**
 * Application entry point.
 *
 * <p>SPEC 1.6 and PROMPT hard rule 2: nothing is seeded here or anywhere else. A fresh
 * install has no household, no members, no orders and no item knowledge. Every name in the
 * system is typed by the user.
 */
public class SplitterApp extends Application {

    private ServiceLocator serviceLocator;

    @Override
    public void onCreate() {
        super.onCreate();
        serviceLocator = new ServiceLocator(this);
    }

    public ServiceLocator serviceLocator() {
        return serviceLocator;
    }
}
