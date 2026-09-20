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

    @Inject
    lateinit var appForegroundState: com.hermes.android.service.AppForegroundState

    @Inject
    lateinit var connectionJournal: com.hermes.android.diagnostics.ConnectionJournal

    override fun onCreate() {
        super.onCreate()

        // Timber logging — plant debug tree in debug builds
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Kept in every build, not just debug: the connection problems worth chasing
        // happen on someone's own phone, hours from any adb cable, and logcat is gone by
        // the time they get reported. Planted before anything else runs so the very
        // first dial of this process is already on the record.
        Timber.plant(com.hermes.android.diagnostics.JournalTree(connectionJournal))
        connectionJournal.noteProcessStart()
        // Foreground tracking for proactive notifications: the event observer
        // only notifies when no Activity is visible.
        registerActivityLifecycleCallbacks(appForegroundState)
        // Zero-config completion delivery (Milestone C): a periodic sync that
        // catches task completions the live socket missed in Doze. Idempotent
        // (KEEP), so this every-launch call never resets the cadence.
        com.hermes.android.work.TaskSyncWorker.schedule(this)
        // The gateway is the slowest part of a cold start — Hermes boots under proot —
        // so it starts here instead of in MainActivity. Process init runs before the
        // Activity exists, and every millisecond of that boot spent behind the first
        // frame is a millisecond the user never waits for.
        startGatewayEarly()
        Timber.i("HermesApplication initializing")
    }

    /**
     * Starts the gateway only when this process came up for the UI.
     *
     * onCreate also runs for background process starts — TaskSyncWorker alone wakes it
     * every 15 minutes — and from Android 12 a startForegroundService() call made from
     * the background throws ForegroundServiceStartNotAllowedException. An unguarded call
     * here would buy a faster launch with a crash. Declining costs nothing: MainActivity
     * starts the service on every foreground regardless, so the slow path is simply the
     * behaviour we already had.
     */
    private fun startGatewayEarly() {
        val processState = android.app.ActivityManager.RunningAppProcessInfo()
        android.app.ActivityManager.getMyMemoryState(processState)
        val foreground = android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        if (processState.importance > foreground) return
        runCatching { com.hermes.android.service.HermesGatewayService.start(this) }
            .onFailure { Timber.w(it, "[App] early gateway start refused; MainActivity will retry") }
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
