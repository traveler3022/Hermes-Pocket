package com.hermes.android.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hermes.android.domain.cron.CronWake
import com.hermes.android.domain.cron.CronWake.JobRun
import com.hermes.android.domain.cron.CronWake.Outcome
import com.hermes.android.gateway.ConnectionState
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.RuntimeType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
 * [LEAD_MS] early so Hermes has booted by the job's minute. Hermes' own ticker runs the job; the
 * app only reads `cron.manage list`: `next_run_at` for the alarm, and `last_run_at` /
 * `last_status` / `last_error` / `last_fire_error` to learn how a run went.
 */
@Singleton
class CronWakeScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gatewayClient: GatewayClient,
    private val runtime: HermesRuntime,
) : CronWake {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    // Job times already waited for: a job Hermes skipped keeps its time, and its alarm
    // would otherwise fire again at once, forever.
    private val prefs = context.getSharedPreferences("cron_wake", Context.MODE_PRIVATE)

    /** One row of `cron.manage list`, as far as the alarm and the result need it. */
    private data class Job(
        val id: String,
        val name: String,
        val active: Boolean,
        val nextRunMillis: Long?,
        val lastRunAt: String?,
        val lastStatus: String?,
        val lastError: String?,
        val lastFireError: String?,
    )

    override suspend fun rearm() {
        if (runtime.type != RuntimeType.PROOT_LINUX) {
            cancel()
            return
        }
        if (gatewayClient.connectionState.value !is ConnectionState.Connected) return
        val now = System.currentTimeMillis()
        val next = upcoming(list(), now).minOfOrNull { it.nextRunMillis ?: Long.MAX_VALUE }
        if (next == null) {
            cancel()
            Timber.i("[CronWake] No upcoming job; alarm cleared")
            return
        }
        schedule(next, now)
    }

    override suspend fun awaitDue(dueAtMillis: Long, maxWaitMillis: Long, onWaiting: (String) -> Unit): List<JobRun> {
        if (runtime.type != RuntimeType.PROOT_LINUX) return emptyList()
        val start = System.currentTimeMillis()
        val cutoff = maxOf(dueAtMillis, start) + SLACK_MS
        val pending = upcoming(list(), start).filter { (it.nextRunMillis ?: Long.MAX_VALUE) <= cutoff }
            .associateBy { it.id }.toMutableMap()
        Timber.i("[CronWake] Waiting for ${pending.size} job(s)")
        val runs = mutableListOf<JobRun>()
        while (pending.isNotEmpty() && System.currentTimeMillis() - start < maxWaitMillis) {
            onWaiting(pending.values.first().name)
            delay(POLL_MS)
            val current = try {
                list().associateBy { it.id }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[CronWake] Could not read the cron list")
                continue
            }
            for (before in pending.values.toList()) {
                val now = current[before.id]
                val run = when {
                    // A one-shot that removed itself after its run.
                    now == null -> JobRun(before.name, Outcome.OK, null)
                    now.lastRunAt != before.lastRunAt -> JobRun(
                        before.name,
                        if (now.lastStatus == null || now.lastStatus == "ok") Outcome.OK else Outcome.FAILED,
                        now.lastError ?: now.lastStatus?.takeIf { it != "ok" },
                    )
                    // Hermes held the job back (e.g. no gateway for it) and said why.
                    now.lastFireError != null && now.lastFireError != before.lastFireError ->
                        JobRun(before.name, Outcome.FAILED, now.lastFireError)
                    else -> null
                } ?: continue
                Timber.i("[CronWake] Job ${before.id}: ${run.outcome}")
                runs += run
                pending.remove(before.id)
            }
        }
        pending.values.forEach { runs += JobRun(it.name, Outcome.NO_RESULT, null) }
        prefs.edit().putLong(KEY_HANDLED_UNTIL, cutoff).apply()
        return runs
    }

    /** Active jobs with a time not waited for yet; one missed by up to [CATCH_UP_MS] still counts. */
    private fun upcoming(jobs: List<Job>, now: Long): List<Job> {
        val handled = prefs.getLong(KEY_HANDLED_UNTIL, 0L)
        return jobs.filter { job ->
            val at = job.nextRunMillis
            job.active && at != null && at > handled && at > now - CATCH_UP_MS
        }
    }

    private suspend fun list(): List<Job> {
        val result = gatewayClient.request(
            GatewayMethods.CRON_MANAGE,
            // Disabled too: a finished one-shot turns disabled, and its result is still wanted.
            mapOf("action" to JsonPrimitive("list"), "include_disabled" to JsonPrimitive(true)),
            trackSession = false,
        ) as? JsonObject ?: return emptyList()
        val jobs = result["jobs"] as? JsonArray ?: return emptyList()
        return jobs.mapNotNull { item ->
            val job = item as? JsonObject ?: return@mapNotNull null
            fun str(key: String) = (job[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            val id = str("job_id") ?: return@mapNotNull null
            Job(
                id = id,
                name = str("name") ?: id,
                active = str("enabled") != "false" && str("state") == "scheduled",
                nextRunMillis = str("next_run_at")?.let(::parseMillis),
                lastRunAt = str("last_run_at"),
                lastStatus = str("last_status"),
                lastError = str("last_error"),
                lastFireError = str("last_fire_error"),
            )
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

        /** Jobs due this close after the alarm's job are waited for in the same wake. */
        const val SLACK_MS = 30_000L

        /** How often the cron list is read while waiting; Hermes' ticker runs every 60 s. */
        const val POLL_MS = 15_000L

        /** Hermes' own catch-up window tops out at two hours (cron/jobs.py, _MAX_GRACE_SECONDS). */
        const val CATCH_UP_MS = 2 * 60 * 60_000L
    }
}
