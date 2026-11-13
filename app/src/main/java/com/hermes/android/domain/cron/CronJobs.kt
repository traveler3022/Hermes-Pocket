package com.hermes.android.domain.cron

/**
 * Editing and running an existing cron job, the way the dashboard's job page does it.
 * cron.manage covers list/add/remove/pause/resume only.
 */
interface CronJobs {

    /**
     * Changes [name], [schedule] and, when not blank, [prompt] in place: the job keeps its id,
     * run history, skills, delivery target and model pin. Throws with the server's reason
     * when a value is refused (for example a malformed schedule).
     */
    suspend fun update(
        jobId: String,
        name: String,
        schedule: String,
        prompt: String,
        /** Null leaves the job's model as it is. */
        model: ModelPin? = null,
        /** Null leaves the job's missed-run rule as it is. */
        catchUp: CatchUp? = null,
    )

    /** The job's own missed-run rule (`catch_up`); [CatchUp.DEFAULT] when it has none. */
    suspend fun catchUp(jobId: String): CatchUp

    /** Stops the job's run in progress; false when nothing was running. The schedule stays. */
    suspend fun cancelRun(jobId: String): Boolean

    /** Runs the job now and waits for it to finish. */
    suspend fun runNow(jobId: String): RunOutcome

    /** Which model the job runs on. */
    sealed interface ModelPin {
        /** Follow the main agent's model (the pin is released). */
        data object Main : ModelPin
        data class Fixed(val provider: String, val model: String) : ModelPin
    }

    /** What a run missed while the server was down does: Hermes' default, one catch-up run, or skip. */
    enum class CatchUp { DEFAULT, RUN_ONCE, SKIP }

    /** [status] is the job's `last_status` after the run ("ok", "error", "delivery_failed"…), null if unknown. */
    data class RunOutcome(val status: String?, val error: String?)
}
