package com.hermes.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * Hermes Application entry point.
 *
 * Initialized by the Android framework before any Activity/Service.
 * - Registers Hilt dependency injection
 * - Registers Timber logging
 * - Configures WorkManager with HiltWorkerFactory (for Step 6 / Step 11)
 *
 * Reference: ADR-002 (Native Compose), ADR-004 (Foreground Service + WorkManager)
 */
@HiltAndroidApp
class HermesApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        // Timber logging — plant debug tree in debug builds
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i("HermesApplication initializing")
    }

    /**
     * WorkManager configuration — uses HiltWorkerFactory so @HiltWorker
     * annotated workers get their dependencies injected.
     *
     * Logging levels use literal int constants (3 = DEBUG, 4 = INFO) instead
     * of android.util.Log.DEBUG/INFO to satisfy Phase 1.5 Rule 8 (Debug
     * Boundary — no direct android.util.Log usage, all logging via Timber).
     *
     * Reference: ADR-008 (Cron → WorkManager bridge), Phase 1 Step 11,
     *            Phase 1.5 Rule 8 (Debug Boundary)
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) LOG_LEVEL_DEBUG else LOG_LEVEL_INFO)
            .build()

    private companion object {
        // android.util.Log.DEBUG = 3, android.util.Log.INFO = 4
        // Declared here as literals to avoid importing android.util.Log
        // (Phase 1.5 Rule 8: all logging goes through Timber)
        private const val LOG_LEVEL_DEBUG = 3
        private const val LOG_LEVEL_INFO = 4
    }
}
