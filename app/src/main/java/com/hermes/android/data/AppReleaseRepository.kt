package com.hermes.android.data

import com.hermes.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

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

/**
 * Looks up the newest published build on GitHub.
 *
 * Reads the public releases API and nothing else — no token, no account, no
 * telemetry sent. The app is distributed as a GitHub release, so that endpoint
 * is where "is there a newer one" is actually answered.
 *
 * It only reports; installing is left to the browser and the system installer.
 * Downloading an APK in-process would mean holding REQUEST_INSTALL_PACKAGES,
 * which is a permission worth more than the taps it saves.
 */
@Singleton
class AppReleaseRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** The running build, without the `-debug` suffix a debug build carries. */
    val installedVersion: String = BuildConfig.VERSION_NAME.substringBefore('-')

    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        val release = fetchLatest() ?: return@withContext UpdateCheck.Failed(FAILED_REACH)
        if (isNewer(release.versionName, installedVersion)) {
            UpdateCheck.Available(release)
        } else {
            UpdateCheck.UpToDate
        }
    }

    /**
     * The newest release that is not a pre-release.
     *
     * `/releases/latest` is not used: CI republishes a rolling `debug-latest`
     * pre-release on every build, and that endpoint would surface those as
     * though they were versioned releases. Listing and filtering leaves only
     * the tagged ones.
     */
    private fun fetchLatest(): AppRelease? = try {
        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful || body.isNullOrBlank()) {
                Timber.w("[Update] releases request failed: HTTP ${response.code}")
                null
            } else {
                json.parseToJsonElement(body).jsonArray
                    .asSequence()
                    .map { it.jsonObject }
                    .filterNot { it.boolean("prerelease") || it.boolean("draft") }
                    .mapNotNull { it.toRelease() }
                    .firstOrNull()
            }
        }
    } catch (e: Exception) {
        Timber.w(e, "[Update] could not reach GitHub")
        null
    }

    private fun JsonObject.toRelease(): AppRelease? {
        val tag = string("tag_name") ?: return null
        return AppRelease(
            versionName = tag.removePrefix("v"),
            title = string("name")?.takeIf { it.isNotBlank() } ?: tag,
            notes = string("body").orEmpty().trim(),
            pageUrl = string("html_url") ?: PROJECT_URL,
            publishedAt = string("published_at")?.take(10),
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.boolean(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false

    companion object {
        const val PROJECT_URL = "https://github.com/traveler3022/Hermes-Pocket"
        private const val RELEASES_URL =
            "https://api.github.com/repos/traveler3022/Hermes-Pocket/releases?per_page=20"
        private const val REQUEST_TIMEOUT_SECONDS = 15L
        private const val FAILED_REACH = "Could not reach GitHub"
    }
}

/**
 * Compares two dotted version strings numerically.
 *
 * String comparison gets this wrong in the case that matters: "1.10.0" sorts
 * before "1.9.0" alphabetically, so an update would go unnoticed exactly when
 * the minor version rolls past nine. Missing segments count as zero, so "1.2"
 * and "1.2.0" are the same version. Anything unparseable is treated as not
 * newer — a check that cannot read the version must not nag.
 */
internal fun isNewer(candidate: String, installed: String): Boolean {
    val left = candidate.numericParts()
    val right = installed.numericParts()
    if (left.isEmpty() || right.isEmpty()) return false
    for (i in 0 until maxOf(left.size, right.size)) {
        val a = left.getOrElse(i) { 0 }
        val b = right.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}

private fun String.numericParts(): List<Int> =
    trim().removePrefix("v")
        .takeWhile { it.isDigit() || it == '.' }
        .split('.')
        .mapNotNull { it.toIntOrNull() }
