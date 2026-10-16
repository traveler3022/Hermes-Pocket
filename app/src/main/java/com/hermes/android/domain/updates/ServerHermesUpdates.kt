package com.hermes.android.domain.updates

/**
 * `hermes update` on the machine Hermes runs on (a server or Termux), through its dashboard,
 * as the dashboard's own update button does. The built-in Linux updates itself instead
 * (HermesRuntime.updateHermes).
 */
interface ServerHermesUpdates {

    /** Asks the server whether it is behind; [Check.message] says why it can't update itself. */
    suspend fun check(): Check

    /** Starts `hermes update` in the background on the server. */
    suspend fun start(): Start

    /** Where the run [actionId] (from [Start.Started]) is now. */
    suspend fun status(actionId: String?): Status

    data class Check(
        val currentVersion: String?,
        /** False also when the check could not run; [behind] is then null. */
        val updateAvailable: Boolean,
        val behind: Int?,
        val canApply: Boolean,
        val message: String?,
        val updateCommand: String?,
    )

    sealed class Start {
        data class Started(val actionId: String?) : Start()
        data class Refused(val message: String) : Start()
    }

    sealed class Status {
        /** Still running, or no verdict yet (the dashboard may be restarting itself). */
        data class Running(val lastLine: String?) : Status()
        data class Succeeded(val version: String?) : Status()
        data class Failed(val reason: String) : Status()
    }
}
