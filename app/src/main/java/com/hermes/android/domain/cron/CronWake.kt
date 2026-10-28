package com.hermes.android.domain.cron

/**
 * Cron jobs on the built-in Linux, where Hermes only runs while the app does. Hermes' own
 * scheduler runs the jobs (the app's gateway script starts its ticker); the app only keeps the
 * phone's alarm on the next job's time, so the phone boots Hermes a little before it, and tells
 * the user afterwards what Hermes recorded.
 */
interface CronWake {

    /**
     * Sets the alarm for the next enabled job, or clears it when there is none or Hermes does
     * not run on the phone. Needs the gateway connected; does nothing otherwise.
     */
    suspend fun rearm()

    /**
     * Waits until Hermes has run every enabled job due by [dueAtMillis] (or by now, if later), up
     * to [maxWaitMillis], and returns what Hermes recorded for each. [onWaiting] gets the name of
     * the job still awaited. Changes nothing in Hermes; it only reads the cron list.
     */
    suspend fun awaitDue(dueAtMillis: Long, maxWaitMillis: Long, onWaiting: (String) -> Unit): List<JobRun>

    enum class Outcome { OK, FAILED, NO_RESULT }

    /** [detail]: Hermes' error for the run, when there is one. */
    data class JobRun(val name: String, val outcome: Outcome, val detail: String?)

    companion object {
        /** Broadcast the alarm sends; the receiver turns it into a wake of the gateway service. */
        const val ACTION_ALARM = "com.hermes.android.action.CRON_ALARM"

        /** The job time the alarm was set for (epoch millis). */
        const val EXTRA_DUE_AT = "due_at"
    }
}
