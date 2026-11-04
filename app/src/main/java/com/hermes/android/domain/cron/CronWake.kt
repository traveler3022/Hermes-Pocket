package com.hermes.android.domain.cron

/**
 * Cron jobs on the built-in Linux, where Hermes only runs while the app does. The app keeps the
 * phone's alarm on the next job's time; when it goes off, Hermes' full gateway runs the job (only
 * it can; it takes the place of the app's own Hermes meanwhile), and the app tells the user how
 * it went.
 */
interface CronWake {

    /**
     * Sets the alarm for the next enabled job, or clears it when there is none or Hermes does
     * not run on the phone. Reads Hermes' cron store directly, so the gateway need not be up.
     */
    suspend fun rearm()

    /**
     * Runs Hermes' full gateway until it has run every enabled job due by [dueAtMillis] (or by
     * now, if later), up to [maxWaitMillis], and returns what Hermes recorded for each. The app's
     * own Hermes is down meanwhile; nothing is started when no job is due. [onWaiting] gets the
     * name of the job still awaited.
     */
    suspend fun runDue(dueAtMillis: Long, maxWaitMillis: Long, onWaiting: (String) -> Unit): List<JobRun>

    enum class Outcome {
        OK,
        FAILED,

        /** Hermes passed over this time without running it (it was too late to catch up). */
        SKIPPED,

        /** Hermes' full gateway did not come up in time, or ended before it was up. */
        NOT_STARTED,

        /** No result in the time allowed, or the gateway ended during the run. */
        NO_RESULT,
    }

    /** [detail]: Hermes' error for a failed run; for NOT_STARTED / NO_RESULT, Hermes' last log line if it ended. */
    data class JobRun(val name: String, val outcome: Outcome, val detail: String?)

    companion object {
        /** Broadcast the alarm sends; the receiver turns it into a wake of the gateway service. */
        const val ACTION_ALARM = "com.hermes.android.action.CRON_ALARM"

        /** The job time the alarm was set for (epoch millis). */
        const val EXTRA_DUE_AT = "due_at"
    }
}
