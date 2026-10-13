package com.hermes.android.service

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.hermes.android.domain.notify.AppVisibility
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether any app Activity is currently started (visible).
 *
 * Used by [AgentEventObserver] to decide between "the user is looking at the
 * chat, don't notify" and "the user is away, surface a notification". Counting
 * started activities (not resumed) matches the platform definition of
 * user-visible, and survives configuration changes because the new activity's
 * onStart fires before the old one's onStop.
 */
@Singleton
class AppForegroundState @Inject constructor() : Application.ActivityLifecycleCallbacks, AppVisibility {

    private val startedCount = AtomicInteger(0)

    override val isForeground: Boolean
        get() = startedCount.get() > 0

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    override fun onActivityStarted(activity: Activity) {
        _visible.value = startedCount.incrementAndGet() > 0
    }

    override fun onActivityStopped(activity: Activity) {
        // Guard against going negative if callbacks were registered after an
        // activity already started (process-restart edge).
        _visible.value = startedCount.updateAndGet { if (it > 0) it - 1 else 0 } > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
