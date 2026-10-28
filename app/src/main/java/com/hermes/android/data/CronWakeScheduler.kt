package com.hermes.android.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hermes.android.domain.cron.CronJobs
import com.hermes.android.domain.cron.CronWake
import com.hermes.android.domain.cron.CronWake.JobRun
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.RuntimeType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import timber.log.Timber
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CronWake] on the phone's [AlarmManager]: one exact alarm, always for the nearest job, set
 * [LEAD_MS] early so Hermes has booted by the job's minute. The jobs and their `next_run_at` come
 * from `cron.manage list`; a job is fired with the dashboard's trigger route ([CronJobs.runNow]),
 * which takes the same durable claim as Hermes' own ticker and moves `next_run_at` on.
 */
@Singleton
class CronWakeScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gatewayClient: GatewayClient,
    private val runtime: HermesRuntime,
    private val cronJobs: CronJobs,
) : CronWake {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    // Job times already handled: a job whose run failed (and so kept its time) is not
    // picked again, or its alarm would fire at once, forever.
    private val prefs = context.getSharedPreferences("cron_wake", Context.MODE_PRIVATE)

    private data class Due(val id: String, val name: String, val atMillis: Long)

    override suspend fun rearm() {
        if (runtime.type != RuntimeType.PROOT_LINUX) {
            cancel()
            return
        }
        if (gatewayClient.connectionState.value !is ConnectionState.Connected) return
        val now = System.currentTimeMillis()
        val next = upcoming(now).minOfOrNull { it.atMillis }
        if (next == null) {
            cancel()
            Timber.i("[CronWake] No upcoming job; alarm cleared")
            return
        }
        schedule(next, now)
    }

    override suspend fun runDue(dueAtMillis: Long, onStart: (String) -> Unit): List<JobRun> {
        if (runtime.type != RuntimeType.PROOT_LINUX) return emptyList()
        val now = System.currentTimeMillis()
        val cutoff = maxOf(dueAtMillis, now) + SLACK_MS
        val due = upcoming(now).filter { it.atMillis <= cutoff }.sortedBy { it.atMillis }
        Timber.i("[CronWake] ${due.size} job(s) due")
        val runs = due.map { job ->
            onStart(job.name)
            try {
                val outcome = cronJobs.runNow(job.id)
                Timber.i("[CronWake] Job ${job.id} ran: ${outcome.status ?: "?"}")
                val ok = outcome.status == null || outcome.status == "ok"
                JobRun(job.name, ok, outcome.error ?: outcome.status?.takeIf { !ok })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 409: already running or claimed elsewhere. Either way it is not ours to repeat.
                Timber.w(e, "[CronWake] Job ${job.id} did not run")
                JobRun(job.name, false, e.message)
            }
        }
        prefs.edit().putLong(KEY_HANDLED_UNTIL, cutoff).apply()
        return runs
    }

    /** Enabled jobs with a time not handled yet; one missed by up to [CATCH_UP_MS] still counts. */
    private suspend fun upcoming(now: Long): List<Due> {
        val handled = prefs.getLong(KEY_HANDLED_UNTIL, 0L)
        val result = gatewayClient.request(
            GatewayMethods.CRON_MANAGE,
            mapOf("action" to JsonPrimitive("list")),
            trackSession = false,
        ) as? JsonObject ?: return emptyList()
        val jobs = result["jobs"] as? JsonArray ?: return emptyList()
        return jobs.mapNotNull { item ->
            val job = item as? JsonObject ?: return@mapNotNull null
            fun str(key: String) = (job[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            val id = str("job_id") ?: return@mapNotNull null
            if (str("enabled") == "false" || str("state") == "paused") return@mapNotNull null
            val at = str("next_run_at")?.let(::parseMillis) ?: return@mapNotNull null
            Due(id, str("name") ?: id, at).takeIf { at > handled && at > now - CATCH_UP_MS }
        }
    }

    private fun schedule(dueAt: Long, now: Long) {
        val triggerAt = maxOf(dueAt - LEAD_MS, now)
        val pending = pendingIntent(dueAt)
        // USE_EXACT_ALARM (13+) and SCHEDULE_EXACT_ALARM (12) are granted at install; the
        // 12 one can be taken back in Settings, and then the alarm may come some minutes late.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            Timber.w("[CronWake] Exact alarms not allowed; inexact alarm set")
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
        Timber.i("[CronWake] Alarm set ${(triggerAt - now) / 1000}s from now for the job at $dueAt")
    }

    private fun cancel() {
        val intent = Intent(CronWake.ACTION_ALARM).setPackage(context.packageName)
        PendingIntent.getBroadcast(context, REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            ?.let { alarms.cancel(it) }
    }

    private fun pendingIntent(dueAt: Long): PendingIntent {
        val intent = Intent(CronWake.ACTION_ALARM)
            .setPackage(context.packageName)
            .putExtra(CronWake.EXTRA_DUE_AT, dueAt)
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun parseMillis(iso: String): Long? =
        runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()

    private companion object {
        const val REQUEST_CODE = 7101
        const val KEY_HANDLED_UNTIL = "handled_until"

        /** Hermes under proot boots in under two minutes; wake that much before the job. */
        const val LEAD_MS = 2 * 60_000L

        /** Jobs due this close after the alarm's job run in the same wake. */
        const val SLACK_MS = 30_000L

        /** Hermes' own catch-up window tops out at two hours (cron/jobs.py, _MAX_GRACE_SECONDS). */
        const val CATCH_UP_MS = 2 * 60 * 60_000L
    }
}
