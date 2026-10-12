package com.hermes.android.domain.updates

/** Whether a newer build of the app is published. It only reports; installing is the user's. */
interface AppUpdates {

    /** The running build, without the `-debug` suffix a debug build carries. */
    val installedVersion: String

    suspend fun check(): UpdateCheck

    companion object {
        const val PROJECT_URL = "https://github.com/traveler3022/Hermes-Pocket"
    }
}

/** A published build of the app, as GitHub describes it. */
data class AppRelease(
    val versionName: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val publishedAt: String?,
)

/** What a check found. */
sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck
    data class Available(val release: AppRelease) : UpdateCheck

    /** Checked and could not answer. [reason] is safe to show a user. */
    data class Failed(val reason: String) : UpdateCheck
}
