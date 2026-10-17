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
    suspend fun update(jobId: String, name: String, schedule: String, prompt: String)

    /** Runs the job now and waits for it to finish. */
    suspend fun runNow(jobId: String): RunOutcome

    /** [status] is the job's `last_status` after the run ("ok", "error", "delivery_failed"…), null if unknown. */
    data class RunOutcome(val status: String?, val error: String?)
}
