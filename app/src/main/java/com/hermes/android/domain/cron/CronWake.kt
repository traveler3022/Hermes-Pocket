package com.hermes.android.domain.cron

/**
 * Cron jobs on the built-in Linux, where nothing ticks Hermes' scheduler: the app's gateway is the
 * stdio one, and the ticker lives in `hermes gateway`, which the phone never runs. The phone's own
 * alarm keeps the time instead. It wakes the app a little before the next job, and the app fires
 * the due jobs itself through the same claim the scheduler uses.
 */
interface CronWake {

    /**
     * Sets the alarm for the next enabled job, or clears it when there is none or Hermes does
     * not run on the phone. Needs the gateway connected; does nothing otherwise.
     */
    suspend fun rearm()

    /**
     * Fires every enabled job due by [dueAtMillis] (or by now, if later), one after another.
     * [onStart] gets each job's name as it begins; the result lists how each one ended.
     */
    suspend fun runDue(dueAtMillis: Long, onStart: (String) -> Unit): List<JobRun>

    /** [detail]: the job's error, or its status when that is not "ok". */
    data class JobRun(val name: String, val ok: Boolean, val detail: String?)

    companion object {
        /** Broadcast the alarm sends; the receiver turns it into a wake of the gateway service. */
        const val ACTION_ALARM = "com.hermes.android.action.CRON_ALARM"

        /** The job time the alarm was set for (epoch millis). */
        const val EXTRA_DUE_AT = "due_at"
    }
}
