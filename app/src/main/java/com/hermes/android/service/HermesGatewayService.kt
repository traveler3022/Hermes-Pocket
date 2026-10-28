package com.hermes.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.hermes.android.domain.cron.CronWake
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.runtime.DetectionResult
import com.hermes.android.runtime.RuntimeState
import com.hermes.android.i18n.tForContext
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * Keeps the gateway connection (and, for the built-in runtime, Hermes itself) alive.
 *
 * Notification design, after Aether's foreground service: nothing is shown while Hermes is
 * connected and idle. While a turn runs, the service is in the foreground with one quiet
 * "Hermes is working" card — the session and a Stop button, nothing that changes per tool
 * call — and holds a partial wake lock so the work continues with the screen off. While
 * connecting or failing, a quiet status line explains why.
 */
@AndroidEntryPoint
class HermesGatewayService : Service() {

    @Inject
    lateinit var gatewayClient: GatewayClient

    @Inject
    lateinit var hermesRuntime: com.hermes.android.runtime.HermesRuntime

    @Inject
    lateinit var agentEventObserver: AgentEventObserver

    @Inject
    lateinit var foregroundState: AppForegroundState

    @Inject
    lateinit var kanbanAlertMonitor: KanbanAlertMonitor

    @Inject
    lateinit var runtimeSelection: com.hermes.android.runtime.RuntimeSelection

    @Inject
    lateinit var cronWake: CronWake

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var connectionWatchJob: Job? = null
    private var renderJob: Job? = null
    private var cronRearmJob: Job? = null
    private var cronWakeJob: Job? = null

    /** Set while the cron alarm's wake holds Hermes up; [jobName] is the job running now. */
    private data class CronCard(val jobName: String?)
    private val cronCard = MutableStateFlow<CronCard?>(null)
    private var wakeLock: PowerManager.WakeLock? = null

    /** Startup problem or progress to show while not connected (null = derive from state). */
    private val status = MutableStateFlow<String?>(null)

    override fun onCreate() {
        super.onCreate()
        Timber.i("[GatewayService] onCreate")
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("[GatewayService] onStartCommand")
        // startForegroundService() requires startForeground() promptly, whatever comes next.
        val connecting = tr("Connecting to Hermes…", "در حال اتصال به Hermes…")
        shownKey = connecting
        promote(statusNotification(connecting))
        // ...and then take it straight back down if it doesn't match reality. MainActivity
        // restarts the service on every foreground; when the gateway is already connected
        // nothing re-emits (connect() no-ops, status stays null), so the render loop never
        // ran again and this placeholder stayed pinned as "Connecting…" forever.
        render(gatewayClient.connectionState.value, agentEventObserver.work.value, status.value, foregroundState.visible.value, cronCard.value)

        // Proactive notifications: watch gateway events for the whole life of
        // the background connection (ChatViewModel's collector dies with the
        // UI; this one doesn't). Idempotent across restarts.
        agentEventObserver.start(scope)
        kanbanAlertMonitor.start(scope)

        if (renderJob?.isActive != true) {
            renderJob = scope.launch {
                combine(
                    gatewayClient.connectionState, agentEventObserver.work, status, foregroundState.visible, cronCard,
                ) { state, work, text, visible, cron -> RenderInput(state, work, text, visible, cron) }
                    .collectLatest { (state, work, text, visible, cron) ->
                    render(state, work, text, visible, cron)
                    delay(RENDER_INTERVAL_MS) // Android drops bursts of updates; tool steps can be rapid.
                }
            }
        }

        // Idempotent, like renderJob above — and for a much more expensive reason. This
        // used to cancel whatever was in flight and begin again on every start request,
        // and the app makes several per launch. Cancelling lands mid-boot, where
        // startGateway() has spawned Alpine and is waiting on gateway.ready but has not
        // reached RuntimeState.Running yet; the next attempt therefore sees "not
        // running", calls stopProcess() on the half-booted gateway and starts a second
        // one from nothing. Tens of seconds of work on a phone, thrown away and redone,
        // because the app was asked twice to do something it was already doing.
        //
        // A finished job does not block a retry: connect() returns after one dial and
        // leaves backoff to the client's own loop, so this is active only while the
        // runtime is genuinely coming up.
        if (connectionWatchJob?.isActive != true) {
            connectionWatchJob = scope.launch {
                try {
                    status.value = null
                    ensureRuntimeGatewayStarted()
                    status.value = null
                    gatewayClient.connect(url = hermesRuntime.getWebSocketUrl())
                } catch (e: Exception) {
                    Timber.e(e, "[GatewayService] Failed to start/connect gateway")
                    setStatus(tr("Hermes is unavailable", "Hermes در دسترس نیست") + ": ${e.message ?: tr("unknown error", "خطای نامشخص")}")
                }
            }
        }

        // The cron alarm: set it again whenever Hermes connects and after every turn, since the
        // agent itself may have added or changed a job.
        if (cronRearmJob?.isActive != true) {
            cronRearmJob = scope.launch {
                merge(
                    gatewayClient.connectionState.map { it is ConnectionState.Connected }.distinctUntilChanged().filter { it },
                    agentEventObserver.work.map { it == null }.distinctUntilChanged().filter { it },
                ).collect { if (cronCard.value == null) rearmCron() } // a wake sets it when done
            }
        }

        if (intent?.action == ACTION_CRON_WAKE && cronWakeJob?.isActive != true) {
            val dueAt = intent.getLongExtra(CronWake.EXTRA_DUE_AT, 0L)
            cronWakeJob = scope.launch { runCronWake(dueAt) }
        }

        return START_NOT_STICKY
    }

    /** Hold Hermes up until it has run the due jobs, say how they went, set the next alarm. */
    private suspend fun runCronWake(dueAt: Long) {
        cronCard.value = CronCard(jobName = null)
        try {
            // Hermes that does not come up in time will not: don't keep the CPU awake for it.
            val connected = withTimeoutOrNull(CRON_CONNECT_MAX_MS) {
                gatewayClient.connectionState.first { it is ConnectionState.Connected }
            }
            if (connected == null) {
                Timber.w("[GatewayService] Cron wake: Hermes did not connect in ${CRON_CONNECT_MAX_MS / 60_000} min")
                notifyCronResult(null, false, tr("Hermes did not start", "Hermes روشن نشد"))
            } else {
                // Hermes' own ticker runs the job; this only waits for its record of the run.
                cronWake.awaitDue(dueAt, CRON_RUN_MAX_MS) { name -> cronCard.value = CronCard(name) }
                    .forEach { run ->
                        when (run.outcome) {
                            CronWake.Outcome.OK -> notifyCronResult(run.name, true, run.detail)
                            CronWake.Outcome.FAILED -> notifyCronResult(run.name, false, run.detail)
                            CronWake.Outcome.NO_RESULT -> notifyCronResult(
                                run.name, false,
                                tr("Hermes recorded no run within 30 minutes", "Hermes تا ۳۰ دقیقه اجرایی ثبت نکرد"),
                            )
                        }
                    }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "[GatewayService] Cron wake failed")
            notifyCronResult(cronCard.value?.jobName, false, e.message)
        } finally {
            withContext(NonCancellable) {
                rearmCron()
                cronCard.value = null
            }
        }
        // Woken only for the job: let the process go back to sleep, as it was.
        if (!foregroundState.visible.value && agentEventObserver.work.value == null) {
            Timber.i("[GatewayService] Cron wake done — stopping service")
            stopSelf()
        }
    }

    /**
     * What a scheduled run did, left in the shade: a job that runs while the phone sleeps must
     * never be invisible, or it looks no different from something running behind the user's back.
     */
    private fun notifyCronResult(name: String?, ok: Boolean, detail: String?) {
        val title = when {
            ok -> tr("Scheduled task done", "کار زمان‌بندی‌شده انجام شد")
            else -> tr("Scheduled task failed", "کار زمان‌بندی‌شده انجام نشد")
        }
        val text = listOfNotNull(name, detail?.takeIf { it.isNotBlank() }).joinToString(" — ")
            .ifBlank { tr("Tap to open", "برای باز کردن بزنید") }
        val notification = HermesNotifications.builder(this, CRON_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        runCatching {
            getSystemService(NotificationManager::class.java).notify(CRON_RESULT_ID_BASE + cronResults++, notification)
        }
    }

    private var cronResults = 0

    private fun cronNotification(jobName: String?): Notification {
        val text = jobName ?: tr("Starting Hermes for the job…", "در حال روشن کردن Hermes برای کار…")
        return HermesNotifications.builder(this, CRON_CHANNEL_ID)
            .setContentTitle(tr("Hermes is running a scheduled task", "Hermes در حال اجرای کار زمان‌بندی‌شده است"))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private suspend fun rearmCron() {
        try {
            cronWake.rearm()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[GatewayService] Could not set the cron alarm")
        }
    }

    /** What the card currently says, so an unchanged render is not re-posted. */
    private var shownKey: String? = null

    private data class RenderInput(val state: ConnectionState, val work: AgentWork?, val text: String?, val visible: Boolean, val cron: CronCard?)

    private fun render(state: ConnectionState, work: AgentWork?, text: String?, visible: Boolean, cron: CronCard?) {
        // The app on screen already shows all of this; the card comes up once the user
        // leaves. Not a turn's card, though: it goes up with the turn, on screen or not.
        // Taking it down on screen meant taking the foreground back from the background
        // when the user left mid-turn, which Android 12+ allows only for a few seconds
        // after the app leaves the screen (or with the battery exemption). A phone busy
        // with the built-in Linux can miss that, and Hermes was left mid-turn in a
        // background process the system may kill. On screen, entering is always allowed.
        // A scheduled run shows its card on screen too: the user sees it happen either way.
        if (visible && work == null && cron == null) {
            holdWakeLock(false)
            if (shownKey != null) {
                shownKey = null
                dismiss()
            }
            return
        }
        when {
            work != null -> {
                val reconnecting = state !is ConnectionState.Connected
                val key = "work:${work.sessions}:$reconnecting"
                if (key != shownKey) {
                    shownKey = key
                    promote(workingNotification(work, reconnecting))
                }
                holdWakeLock(true)
            }
            // Woken by the cron alarm: in the foreground with the CPU awake until the job is done.
            cron != null -> {
                val key = "cron:${cron.jobName}"
                if (key != shownKey) {
                    shownKey = key
                    promote(cronNotification(cron.jobName))
                }
                holdWakeLock(true)
            }
            state is ConnectionState.Connected -> {
                holdWakeLock(false)
                shownKey = null
                dismiss()
            }
            else -> {
                holdWakeLock(false)
                val line = text ?: when (state) {
                    is ConnectionState.Disconnected -> tr("Disconnected", "قطع شد")
                    is ConnectionState.Connecting -> tr("Connecting…", "در حال اتصال…")
                    // Show WHY — an endless "attempt N" with no reason is undebuggable from the phone.
                    is ConnectionState.Reconnecting ->
                        tr("Reconnecting (attempt ${state.attempt})", "اتصال دوباره (تلاش ${state.attempt})") +
                            (state.lastError?.let { ": $it" } ?: "…")
                    is ConnectionState.Failed -> tr("Connection failed", "اتصال ناموفق بود") + ": ${state.reason}"
                    is ConnectionState.Connected -> return
                }
                if (line != shownKey) {
                    shownKey = line
                    promote(statusNotification(line))
                }
            }
        }
    }

    private fun setStatus(text: String) {
        status.value = text
    }

    private suspend fun ensureRuntimeGatewayStarted() {
        // A fresh install has only a default runtime, not the user's choice —
        // starting it would install / boot built-in Linux for someone who wants
        // Termux. Setup asks first.
        if (!runtimeSelection.hasChosen.value) {
            setStatus(tr("Choose where Hermes runs", "انتخاب کنید Hermes کجا اجرا شود"))
            throw IllegalStateException("No runtime chosen yet")
        }
        when (val state = hermesRuntime.state.value) {
            is RuntimeState.Running -> return
            is RuntimeState.Installed -> {
                setStatus(tr("Starting Hermes…", "در حال روشن کردن Hermes…"))
                hermesRuntime.startGateway()
                return
            }
            is RuntimeState.NotDetected,
            is RuntimeState.Error -> {
                setStatus(tr("Checking the Hermes runtime…", "در حال بررسی محیط اجرای Hermes…"))
                val detected = when (val detection = hermesRuntime.detect()) {
                    is DetectionResult.Missing -> {
                        setStatus(tr("Runtime setup required", "نیاز به راه‌اندازی محیط اجرا"))
                        throw IllegalStateException(detection.title)
                    }
                    is DetectionResult.Incompatible -> {
                        setStatus(tr("Runtime incompatible", "محیط اجرا سازگار نیست"))
                        throw IllegalStateException(detection.reason)
                    }
                    is DetectionResult.Available -> detection.info
                }
                // From the detection, not hermesRuntime.state: the router's state is a flow
                // derived on another thread that has not caught up with detect() yet. It still
                // read NotDetected, so nearly every cold start failed here with "not installed
                // yet" — and a start at boot, with no chat screen to dial, left Hermes down.
                if (detected.hermesVersion != null) {
                    setStatus(tr("Starting Hermes…", "در حال روشن کردن Hermes…"))
                    hermesRuntime.startGateway()
                    return
                }
                setStatus(tr("Hermes is not installed yet", "Hermes هنوز نصب نشده است"))
                throw IllegalStateException("Hermes is not installed yet")
            }
            is RuntimeState.Detected -> {
                setStatus(tr("Hermes is not installed yet", "Hermes هنوز نصب نشده است"))
                throw IllegalStateException("Hermes is not installed yet")
            }
            RuntimeState.Detecting,
            RuntimeState.Installing -> {
                setStatus(tr("Runtime is busy…", "محیط اجرا مشغول است…"))
                throw IllegalStateException("Runtime is busy: $state")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("[GatewayService] onDestroy")
        connectionWatchJob?.cancel()
        holdWakeLock(false)
        CoroutineScope(Dispatchers.IO).launch { gatewayClient.disconnect() }
        scope.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Swiping the app away stops the idle service; a running turn keeps it (and Hermes) alive.
        if (agentEventObserver.work.value == null) {
            Timber.i("[GatewayService] onTaskRemoved — stopping service")
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification ──────────────────────────────────────────────────────

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        runCatching { manager.deleteNotificationChannel("hermes_gateway") }
        HermesNotifications.ensureChannel(
            this,
            NotificationChannel(STATUS_CHANNEL_ID, tr("Connection", "اتصال"), NotificationManager.IMPORTANCE_MIN).apply {
                description = tr("Shown only while Hermes is connecting or unreachable", "فقط وقتی Hermes در حال اتصال است یا در دسترس نیست")
                setShowBadge(false)
            },
        )
        HermesNotifications.ensureChannel(
            this,
            NotificationChannel(CRON_CHANNEL_ID, tr("Scheduled tasks", "کارهای زمان‌بندی‌شده"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = tr("Shown whenever a cron job runs, and with what it did", "هر بار که یک کرون اجرا می‌شود، همراه با نتیجه‌اش")
            },
        )
        HermesNotifications.ensureChannel(
            this,
            NotificationChannel(WORKING_CHANNEL_ID, tr("Working", "در حال کار"), NotificationManager.IMPORTANCE_LOW).apply {
                description = tr("Shown while Hermes is working on a task", "وقتی Hermes روی کاری کار می‌کند نمایش داده می‌شود")
                setShowBadge(false)
            },
        )
    }

    private fun workingNotification(work: AgentWork, reconnecting: Boolean): Notification {
        val single = work.sessions.size == 1
        val sessionId = work.sessions.keys.first()
        val sessionTitle = work.sessions.values.firstOrNull { it.isNotBlank() }
        val title = when {
            !single -> tr("Hermes is working on ${work.sessions.size} tasks", "Hermes روی ${work.sessions.size} کار کار می‌کند")
            else -> tr("Hermes is working", "Hermes در حال کار است")
        }
        // One line that holds still for the whole turn: the session (or why it
        // went quiet). Per-tool steps, the chronometer and the progress bar
        // redrew the card every few seconds, which reads as a notification
        // that keeps popping up.
        val line = when {
            reconnecting -> tr("Reconnecting…", "در حال اتصال دوباره…")
            single -> sessionTitle ?: tr("Tap to open", "برای باز کردن بزنید")
            else -> tr("Tap to open", "برای باز کردن بزنید")
        }
        return HermesNotifications.builder(this, WORKING_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(line)
            .setContentIntent(HermesNotifications.openApp(this, if (single) agentEventObserver.openableId(sessionId) else null))
            .setShowWhen(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(0, tr("Stop", "توقف"), AgentStopReceiver.pendingIntent(this, work.sessions.keys))
            .build()
    }

    private fun statusNotification(text: String): Notification =
        HermesNotifications.builder(this, STATUS_CHANNEL_ID)
            .setContentTitle("Hermes")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    /**
     * Shows [notification] as the foreground notification. Android 12+ may refuse to (re)enter
     * the foreground from the background (e.g. a scheduled job starting while the app is
     * closed and not battery-exempt); then it is still posted as a normal notification.
     */
    private fun promote(notification: Notification) {
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (e: Exception) {
            Timber.w(e, "[GatewayService] Could not enter foreground; posting normally")
            runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification) }
        }
    }

    private fun dismiss() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    /** Only while a turn runs: the built-in runtime's Hermes lives in this process. */
    private fun holdWakeLock(hold: Boolean) {
        val current = wakeLock
        if (hold && current == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hermes:working").apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_MAX_MS)
            }
        } else if (!hold && current != null) {
            if (current.isHeld) current.release()
            wakeLock = null
        }
    }

    // Notification text follows the in-app language choice, not only the
    // device locale, so it matches what the user sees inside the app.
    private fun tr(en: String, fa: String): String = tForContext(this, en, fa)

    companion object {
        private const val STATUS_CHANNEL_ID = "hermes_gateway_service"
        private const val WORKING_CHANNEL_ID = "hermes_working"
        private const val CRON_CHANNEL_ID = "hermes_cron"
        private const val CRON_RESULT_ID_BASE = 7200
        private const val NOTIFICATION_ID = 1
        private const val RENDER_INTERVAL_MS = 400L
        private const val WAKE_LOCK_MAX_MS = 60 * 60 * 1000L
        private const val CRON_CONNECT_MAX_MS = 5 * 60 * 1000L
        private const val CRON_RUN_MAX_MS = 30 * 60 * 1000L
        private const val ACTION_CRON_WAKE = "com.hermes.android.action.CRON_WAKE"

        fun start(context: Context) {
            val intent = Intent(context, HermesGatewayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** From the cron alarm: boot Hermes and fire the job due at [dueAt]. */
        fun wakeForCron(context: Context, dueAt: Long) {
            val intent = Intent(context, HermesGatewayService::class.java)
                .setAction(ACTION_CRON_WAKE)
                .putExtra(CronWake.EXTRA_DUE_AT, dueAt)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HermesGatewayService::class.java))
        }
    }
}
