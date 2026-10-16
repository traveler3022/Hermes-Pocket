package com.hermes.android.domain.updates

import android.content.Intent
import java.io.File

/**
 * Whether a newer build of the app is published, and fetching it.
 *
 * Checking only reports. Downloading puts the release APK in the app's own cache, and
 * installing is still the system installer's — the user confirms it there.
 */
interface AppUpdates {

    /** The running build, without the `-debug` suffix a debug build carries. */
    val installedVersion: String

    /**
     * True for a debug build. Its package id ends in `.debug`, so a release APK installs
     * beside it as a second app instead of over it. A debug build follows the rolling
     * `debug-latest` test build instead of the versioned releases.
     */
    val isDebugBuild: Boolean

    /** The commit this build came from (8 hex), or empty when the build did not record one. */
    val installedCommit: String

    suspend fun check(): UpdateCheck

    /**
     * Whether the check on launch is due: none has answered yet, or the last one that
     * answered is [AUTO_CHECK_INTERVAL_MS] old. A failed check does not count.
     */
    fun isAutoCheckDue(): Boolean

    /** A check on launch answered; the next one waits [AUTO_CHECK_INTERVAL_MS]. */
    fun markAutoChecked()

    /**
     * Downloads [apk] into the app's cache, replacing the previous download there.
     * Cancel the calling coroutine to stop it. [onProgress] gets bytes so far and the
     * total (0 when unknown).
     */
    suspend fun download(apk: ReleaseApk, onProgress: (Long, Long) -> Unit): ApkDownload

    /** Whether the user allowed this app to install other apps ("install unknown apps"). */
    fun canInstallPackages(): Boolean

    /** The system screen where the user allows this app to install apps. */
    fun installPermissionIntent(): Intent

    /** The system installer for a downloaded APK. */
    fun installerIntent(apk: File): Intent

    /**
     * The installed version's release, once, after the app was updated: null on a fresh
     * install, when it was already shown, or when the release has no notes. Offline also
     * answers null, but is asked again on the next launch. Always null in a debug build.
     */
    suspend fun pendingWhatsNew(): AppRelease?

    /** The What's New of this version was shown; never show it again. */
    fun markWhatsNewSeen()

    companion object {
        const val PROJECT_URL = "https://github.com/traveler3022/Hermes-Pocket"

        /** Three days between checks on launch. */
        const val AUTO_CHECK_INTERVAL_MS = 3L * 24 * 60 * 60 * 1000
    }
}

/** A published build of the app, as GitHub describes it. */
data class AppRelease(
    val versionName: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val publishedAt: String?,
    /** The installable APK attached to the release; null when it has none. */
    val apk: ReleaseApk? = null,
    /** Set for the rolling `debug-latest` test build, which has no version of its own. */
    val testBuild: TestBuild? = null,
)

/** What the `debug-latest` release notes say about the test build. */
data class TestBuild(
    val branch: String?,
    /** 8 hex digits, as CI writes them. */
    val commit: String,
    val built: String?,
)

/**
 * The APK asset of a release. [size] is what GitHub says, 0 when it did not say.
 * [isTestBuild]: the debug APK, which installs over this debug build (same package and key).
 */
data class ReleaseApk(
    val name: String,
    val url: String,
    val size: Long,
    val isTestBuild: Boolean = false,
)

/** What a check found. */
sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck
    data class Available(val release: AppRelease) : UpdateCheck

    /** Checked and could not answer. [reason] is safe to show a user. */
    data class Failed(val reason: String) : UpdateCheck
}

/** How a download ended. Cancelling is not a result: the coroutine is cancelled. */
sealed interface ApkDownload {
    data class Ready(val file: File) : ApkDownload
    data class Failed(val problem: DownloadProblem) : ApkDownload
}

enum class DownloadProblem {
    /** No connection, a timeout, or GitHub answered with an error. */
    Network,

    /** The bytes that arrived are not the release APK (wrong size, type or contents). */
    NotAnApk,

    /** The APK is another app's, or signed with a key this install does not have. */
    WrongApp,

    /** The phone's storage could not hold it. */
    Storage,
}
