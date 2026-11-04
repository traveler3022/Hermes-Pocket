package com.hermes.android.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hermes.android.domain.cron.CronWake
import com.hermes.android.domain.cron.CronWake.JobRun
import com.hermes.android.domain.cron.CronWake.Outcome
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.RuntimeType
import com.hermes.android.runtime.linux.ProotLinuxRuntime
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CronWake] on the phone's [AlarmManager]: one exact alarm, always for the nearest job, set
 * [LEAD_MS] early so Hermes has booted by the job's minute. The jobs come from Hermes' own cron
 * store (`cron/jobs.json` of every profile; cron/jobs.py), read from the rootfs: while the full
 * gateway runs a job, the app's own gateway is down and cannot be asked.
 */
@Singleton
class CronWakeScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gatewayClient: GatewayClient,
    private val runtime: HermesRuntime,
    private val linux: ProotLinuxRuntime,
) : CronWake {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    // Job times already waited for: a job Hermes skipped keeps its time, and its alarm
    // would otherwise fire again at once, forever.
    private val prefs = context.getSharedPreferences("cron_wake", Context.MODE_PRIVATE)

    /** One job of Hermes' cron store, as far as the alarm and the result need it. */
    private data class Job(
        val key: String,
        val name: String,
        val active: Boolean,
        val nextRunMillis: Long?,
        val lastRunAt: String?,
        val lastStatus: String?,
        val lastError: String?,
        val claimed: Boolean,
    )

    override suspend fun rearm() {
        if (runtime.type != RuntimeType.PROOT_LINUX) {
            cancel()
            return
        }
        val jobs = try {
            list()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[CronWake] Could not read the cron store; alarm left as it was")
            return
        }
        val now = System.currentTimeMillis()
        val next = upcoming(jobs, now).minOfOrNull { it.nextRunMillis ?: Long.MAX_VALUE }
        if (next == null) {
            cancel()
            Timber.i("[CronWake] No upcoming job; alarm cleared")
            return
        }
        schedule(next, now)
    }

    override suspend fun runDue(dueAtMillis: Long, maxWaitMillis: Long, onWaiting: (String) -> Unit): List<JobRun> {
        if (runtime.type != RuntimeType.PROOT_LINUX) return emptyList()
        val start = System.currentTimeMillis()
        val cutoff = maxOf(dueAtMillis, start) + SLACK_MS
        val pending = upcoming(list(), start).filter { (it.nextRunMillis ?: Long.MAX_VALUE) <= cutoff }
            .associateBy { it.key }.toMutableMap()
        if (pending.isEmpty()) {
            Timber.i("[CronWake] No job due any more; Hermes not started")
            return emptyList()
        }
        Timber.i("[CronWake] ${pending.size} job(s) due; running Hermes' full gateway")
        // The app's gateway is stopped for the run: a client still dialling it would only spin.
        gatewayClient.disconnect()
        val runs = mutableListOf<JobRun>()
        linux.withFullGateway { gateway ->
            var ready = false
            while (pending.isNotEmpty() && System.currentTimeMillis() - start < maxWaitMillis) {
                onWaiting(pending.values.first().name)
                delay(POLL_MS)
                if (!ready) ready = gateway.isReady()
                val ended = !gateway.isAlive
                if (ended || (!ready && System.currentTimeMillis() - start > START_MAX_MS)) {
                    val outcome = if (ready) Outcome.NO_RESULT else Outcome.NOT_STARTED
                    val detail = if (ended) gateway.lastLogLine() else null
                    Timber.w("[CronWake] Full gateway ${if (ended) "ended" else "not up in time"}: $detail")
                    // A run it recorded just before ending still counts: read once more below.
                    moveDecided(pending, runs)
                    pending.values.forEach { runs += JobRun(it.name, outcome, detail) }
                    pending.clear()
                    break
                }
                moveDecided(pending, runs)
            }
        }
        pending.values.forEach { runs += JobRun(it.name, Outcome.NO_RESULT, null) }
        prefs.edit().putLong(KEY_HANDLED_UNTIL, cutoff).apply()
        return runs
    }

    /** Moves every [pending] job Hermes has decided on, by its store, into [runs]. */
    private suspend fun moveDecided(pending: MutableMap<String, Job>, runs: MutableList<JobRun>) {
        val current = try {
            list().associateBy { it.key }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[CronWake] Could not read the cron store")
            return
        }
        for (before in pending.values.toList()) {
            val now = current[before.key]
            val run = when {
                // A one-shot that removed itself after its run.
                now == null -> JobRun(before.name, Outcome.OK, null)
                now.lastRunAt != before.lastRunAt -> JobRun(
                    before.name,
                    if (now.lastStatus == null || now.lastStatus == "ok") Outcome.OK else Outcome.FAILED,
                    now.lastError ?: now.lastStatus?.takeIf { it != "ok" },
                )
                // Hermes moved the time on with no run under way and none recorded: it passed
                // over this one. (A run it starts claims the job and moves the time in one write,
                // and clears the claim with the record of the run; cron/jobs.py.)
                now.nextRunMillis != before.nextRunMillis && !now.claimed -> JobRun(before.name, Outcome.SKIPPED, null)
                else -> null
            } ?: continue
            Timber.i("[CronWake] Job ${before.key}: ${run.outcome}")
            runs += run
            pending.remove(before.key)
        }
    }

    /** Active jobs with a time not waited for yet; one missed by up to [CATCH_UP_MS] still counts. */
    private fun upcoming(jobs: List<Job>, now: Long): List<Job> {
        val handled = prefs.getLong(KEY_HANDLED_UNTIL, 0L)
        return jobs.filter { job ->
            val at = job.nextRunMillis
            job.active && at != null && at > handled && at > now - CATCH_UP_MS
        }
    }

    /** Every profile's jobs: Hermes' full gateway ticks them all (gateway/run_bootstrap.py). */
    private suspend fun list(): List<Job> = withContext(Dispatchers.IO) {
        val home = linux.hostFileForGuestPath(HERMES_HOME) ?: return@withContext emptyList()
        val profiles = File(home, "profiles").listFiles()?.filter { it.isDirectory }.orEmpty()
        (listOf(home) + profiles).flatMap { dir ->
            val store = File(dir, "cron/jobs.json")
            if (store.isFile) parse(store, dir.name) else emptyList()
        }
    }

    /** Hermes writes the store whole (a rename), so a read sees one version or the other. */
    private fun parse(store: File, scope: String): List<Job> {
        val jobs = (Json.parseToJsonElement(store.readText()) as? JsonObject)?.get("jobs") as? JsonArray
            ?: return emptyList()
        return jobs.mapNotNull { item ->
            val job = item as? JsonObject ?: return@mapNotNull null
            fun str(key: String) = (job[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            val id = str("id") ?: return@mapNotNull null
            Job(
                key = "$scope/$id",
                name = str("name") ?: id,
                active = (job["enabled"] as? JsonPrimitive)?.booleanOrNull != false && str("state") == "scheduled",
                nextRunMillis = str("next_run_at")?.let(::parseMillis),
                lastRunAt = str("last_run_at"),
                lastStatus = str("last_status"),
                lastError = str("last_error"),
                claimed = job["fire_claim"] is JsonObject,
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
        const val HERMES_HOME = "/root/.hermes"

        /** Hermes' full gateway under proot boots in about a minute; wake that much and more before the job. */
        const val LEAD_MS = 2 * 60_000L

        /** How long the full gateway may take to come up (the user's call: five minutes). */
        const val START_MAX_MS = 5 * 60_000L

        /** Jobs due this close after the alarm's job are waited for in the same wake. */
        const val SLACK_MS = 30_000L

        /** How often the cron store is read while waiting; Hermes' ticker runs every 60 s. */
        const val POLL_MS = 15_000L

        /** Hermes' own catch-up window tops out at two hours (cron/jobs.py, _MAX_GRACE_SECONDS). */
        const val CATCH_UP_MS = 2 * 60 * 60_000L
    }
}
