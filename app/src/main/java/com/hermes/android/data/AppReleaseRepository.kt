package com.hermes.android.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.hermes.android.BuildConfig
import com.hermes.android.domain.updates.ApkDownload
import com.hermes.android.domain.updates.AppRelease
import com.hermes.android.domain.updates.AppUpdates
import com.hermes.android.domain.updates.DownloadProblem
import com.hermes.android.domain.updates.ReleaseApk
import com.hermes.android.domain.updates.TestBuild
import com.hermes.android.domain.updates.UpdateCheck
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looks up the newest published build on GitHub, and fetches its APK.
 *
 * Reads the public releases API and nothing else — no token, no account, no
 * telemetry sent. The app is distributed as a GitHub release, so that endpoint
 * is where "is there a newer one" is actually answered.
 *
 * The APK goes to the app's cache (`cache/updates/`, shared through the
 * `.shared` FileProvider) and is handed to the system installer, which asks the
 * user. Installing needs REQUEST_INSTALL_PACKAGES plus the user's "install
 * unknown apps" switch for this app; without the switch the user is sent to it.
 */
@Singleton
class AppReleaseRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppUpdates {

    private val client = OkHttpClient.Builder()
        .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** Same timeouts per read, but no limit on the whole call: an APK is tens of MB. */
    private val downloadClient = client.newBuilder()
        .readTimeout(DOWNLOAD_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The running build, without the `-debug` suffix a debug build carries. */
    override val installedVersion: String = BuildConfig.VERSION_NAME.substringBefore('-')

    override val isDebugBuild: Boolean = BuildConfig.DEBUG

    override val installedCommit: String = BuildConfig.GIT_SHA

    override suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        if (isDebugBuild) return@withContext checkTestBuild()
        val release = fetchLatest() ?: return@withContext UpdateCheck.Failed(FAILED_REACH)
        if (isNewer(release.versionName, installedVersion)) {
            UpdateCheck.Available(release)
        } else {
            UpdateCheck.UpToDate
        }
    }

    /**
     * A debug build follows the rolling `debug-latest` pre-release CI republishes on every
     * green build (build-apk.yml): its notes name the branch, commit and time, and its
     * `app-debug.apk` has this build's package and committed debug key, so it installs over.
     * Newer means "another commit"; a build without a recorded commit always gets the offer.
     */
    private fun checkTestBuild(): UpdateCheck {
        val obj = when (val found = fetchByTag(TEST_BUILD_TAG)) {
            is TagLookup.Found -> found.release
            // CI deletes and recreates the release on each build; it may be mid-swap.
            TagLookup.Missing, TagLookup.Unreachable -> return UpdateCheck.Failed(FAILED_REACH)
        }
        val build = parseTestBuild(obj.string("body").orEmpty())
            ?: return UpdateCheck.Failed(FAILED_TEST_BUILD)
        if (sameCommit(build.commit, installedCommit)) return UpdateCheck.UpToDate
        val release = obj.toRelease()?.copy(
            apk = pickTestBuildApk(obj.assets()),
            testBuild = build,
        ) ?: return UpdateCheck.Failed(FAILED_TEST_BUILD)
        return UpdateCheck.Available(release)
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
            pageUrl = string("html_url") ?: AppUpdates.PROJECT_URL,
            publishedAt = string("published_at")?.take(10),
            apk = pickReleaseApk(assets()),
        )
    }

    private fun JsonObject.assets(): List<ReleaseAsset> =
        (this["assets"] as? JsonArray)?.mapNotNull { it.toAsset() }.orEmpty()

    private fun kotlinx.serialization.json.JsonElement.toAsset(): ReleaseAsset? {
        val obj = this as? JsonObject ?: return null
        return ReleaseAsset(
            name = obj.string("name") ?: return null,
            url = obj.string("browser_download_url") ?: return null,
            size = (obj["size"] as? JsonPrimitive)?.longOrNull ?: 0L,
            contentType = obj.string("content_type"),
            uploaded = obj.string("state")?.let { it == "uploaded" } ?: true,
        )
    }

    // ── Download ─────────────────────────────────────────────────────────────

    override suspend fun download(
        apk: ReleaseApk,
        onProgress: (Long, Long) -> Unit,
    ): ApkDownload = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, UPDATES_DIR)
        // Only the app's own earlier downloads live here; a new one replaces them.
        dir.listFiles()?.forEach { it.delete() }
        if (!dir.isDirectory && !dir.mkdirs()) return@withContext ApkDownload.Failed(DownloadProblem.Storage)
        if (apk.size > 0 && dir.usableSpace < apk.size + FREE_SPACE_MARGIN_BYTES) {
            return@withContext ApkDownload.Failed(DownloadProblem.Storage)
        }
        val part = File(dir, "$APK_FILE_NAME.part")
        val target = File(dir, APK_FILE_NAME)

        val request = runCatching { Request.Builder().url(apk.url).build() }.getOrNull()
            ?: return@withContext ApkDownload.Failed(DownloadProblem.NotAnApk)
        val call = downloadClient.newCall(request)
        // A blocking read does not see cancellation; cancelling the call ends it.
        val cancelHook = coroutineContext.job.invokeOnCompletion { call.cancel() }
        val problem: DownloadProblem? = try {
            call.execute().use { response ->
                val body = response.body
                when {
                    !response.isSuccessful || body == null -> {
                        Timber.w("[Update] APK download failed: HTTP ${response.code}")
                        DownloadProblem.Network
                    }
                    !isApkContentType(body.contentType()?.toString()) -> {
                        Timber.w("[Update] APK download is ${body.contentType()}, not an APK")
                        DownloadProblem.NotAnApk
                    }
                    apk.size > 0 && body.contentLength() > 0 && body.contentLength() != apk.size -> {
                        Timber.w("[Update] APK is ${body.contentLength()} bytes, release says ${apk.size}")
                        DownloadProblem.NotAnApk
                    }
                    else -> {
                        val total = body.contentLength().takeIf { it > 0 } ?: apk.size
                        var read = 0L
                        body.byteStream().use { input ->
                            part.outputStream().use { output ->
                                val buffer = ByteArray(BUFFER_BYTES)
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    output.write(buffer, 0, n)
                                    read += n
                                    onProgress(read, total)
                                }
                            }
                        }
                        null
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Cancelling the call surfaces here as an IOException; that is a cancel, not a failure.
            coroutineContext.ensureActive()
            Timber.w(e, "[Update] APK download interrupted")
            DownloadProblem.Network
        } finally {
            cancelHook.dispose()
            if (!isActive) part.delete()
        }

        val failure = problem ?: verifyApk(part, apk)
        if (failure != null) {
            part.delete()
            return@withContext ApkDownload.Failed(failure)
        }
        if (!part.renameTo(target)) {
            part.delete()
            return@withContext ApkDownload.Failed(DownloadProblem.Storage)
        }
        ApkDownload.Ready(target)
    }

    /**
     * The file is the release APK: the size GitHub listed, a zip, and a package the
     * installer will take as this app. Anything else (a captive-portal page, a cut-off
     * download, someone else's app) never reaches the installer.
     */
    private fun verifyApk(file: File, apk: ReleaseApk): DownloadProblem? {
        val length = file.length()
        if (apk.size > 0 && length != apk.size) return DownloadProblem.NotAnApk
        if (length < MIN_APK_BYTES) return DownloadProblem.NotAnApk
        val magic = ByteArray(4)
        val readMagic = runCatching { file.inputStream().use { it.read(magic) } }.getOrDefault(-1)
        if (readMagic != 4 || !magic.contentEquals(ZIP_MAGIC)) return DownloadProblem.NotAnApk

        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val archive: PackageInfo = runCatching {
            pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
        }.getOrNull() ?: return DownloadProblem.NotAnApk
        // A debug build is `<id>.debug`; the release it fetches is `<id>`, the test build
        // it fetches is its own package.
        val releasePackage = if (apk.isTestBuild) context.packageName else context.packageName.removeSuffix(DEBUG_SUFFIX)
        if (archive.packageName != releasePackage) {
            Timber.w("[Update] APK is ${archive.packageName}, expected $releasePackage")
            return DownloadProblem.WrongApp
        }
        // Over the same package, Android refuses an APK signed by another key, after the
        // user already confirmed. Say so before, when both signatures can be read.
        if (archive.packageName == context.packageName) {
            val theirs = archive.signers()
            val ours = runCatching {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signers()
            }.getOrDefault(emptySet())
            if (theirs.isNotEmpty() && ours.isNotEmpty() && theirs.none { it in ours }) {
                Timber.w("[Update] APK is signed with another key")
                return DownloadProblem.WrongApp
            }
        }
        return null
    }

    private fun PackageInfo.signers(): Set<Signature> {
        val info = signingInfo ?: return emptySet()
        val current = info.apkContentsSigners.orEmpty().toSet()
        // A rotated key still counts: the new APK may be signed by a later key in our lineage.
        val history = if (info.hasMultipleSigners()) emptySet() else info.signingCertificateHistory.orEmpty().toSet()
        return current + history
    }

    override fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    override fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    override fun installerIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.shared", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    // ── What's New ───────────────────────────────────────────────────────────

    override suspend fun pendingWhatsNew(): AppRelease? = withContext(Dispatchers.IO) {
        // A debug build: versionCode stays put between test builds, and the debug-latest
        // notes hold only branch, commit and time — nothing worth a dialog. The user who
        // installs test builds already knows what changed.
        if (isDebugBuild) return@withContext null
        val current = BuildConfig.VERSION_CODE
        val lastSeen = prefs.getInt(KEY_WHATS_NEW_CODE, NONE)
        if (lastSeen >= current) {
            // Seen, or a downgrade: nothing to announce.
            if (lastSeen > current) markWhatsNewSeen()
            return@withContext null
        }
        // Nothing stored yet: either a fresh install, which has nothing to compare
        // against, or the first build that has this feature, which was an update.
        if (lastSeen == NONE && isFreshInstall()) {
            markWhatsNewSeen()
            return@withContext null
        }
        when (val found = fetchByTag("v$installedVersion")) {
            // Offline or GitHub down: keep it pending for the next launch.
            TagLookup.Unreachable -> null
            TagLookup.Missing -> {
                markWhatsNewSeen()
                null
            }
            is TagLookup.Found -> {
                val release = found.release.toRelease()
                if (release == null || release.notes.isBlank()) {
                    markWhatsNewSeen()
                    null
                } else {
                    release
                }
            }
        }
    }

    override fun markWhatsNewSeen() {
        prefs.edit().putInt(KEY_WHATS_NEW_CODE, BuildConfig.VERSION_CODE).apply()
    }

    private fun isFreshInstall(): Boolean = runCatching {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(true)

    private sealed interface TagLookup {
        data class Found(val release: JsonObject) : TagLookup
        data object Missing : TagLookup
        data object Unreachable : TagLookup
    }

    private fun fetchByTag(tag: String): TagLookup = try {
        val request = Request.Builder()
            .url("$RELEASE_BY_TAG_URL${Uri.encode(tag)}")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            when {
                response.code == HTTP_NOT_FOUND -> TagLookup.Missing
                !response.isSuccessful || body.isNullOrBlank() -> {
                    Timber.w("[Update] release $tag request failed: HTTP ${response.code}")
                    TagLookup.Unreachable
                }
                else -> TagLookup.Found(json.parseToJsonElement(body).jsonObject)
            }
        }
    } catch (e: Exception) {
        Timber.w(e, "[Update] could not reach GitHub for $tag")
        TagLookup.Unreachable
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.boolean(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: false

    companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/traveler3022/Hermes-Pocket/releases?per_page=20"
        private const val RELEASE_BY_TAG_URL =
            "https://api.github.com/repos/traveler3022/Hermes-Pocket/releases/tags/"
        private const val REQUEST_TIMEOUT_SECONDS = 15L
        private const val DOWNLOAD_READ_TIMEOUT_SECONDS = 30L
        private const val FAILED_REACH = "Could not reach GitHub"
        private const val FAILED_TEST_BUILD = "The test build's notes could not be read"
        private const val TEST_BUILD_TAG = "debug-latest"
        private const val HTTP_NOT_FOUND = 404

        private const val UPDATES_DIR = "updates"
        private const val APK_FILE_NAME = "hermes-pocket-update.apk"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val DEBUG_SUFFIX = ".debug"
        private const val BUFFER_BYTES = 64 * 1024
        private const val MIN_APK_BYTES = 1L shl 20
        private const val FREE_SPACE_MARGIN_BYTES = 20L shl 20
        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

        private const val PREFS_NAME = "app_updates"
        private const val KEY_WHATS_NEW_CODE = "whats_new_version_code"
        private const val NONE = -1
    }
}

/** One file attached to a GitHub release, as the API lists it. */
internal data class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long,
    val contentType: String?,
    val uploaded: Boolean = true,
)

/**
 * The APK of a release. release.yml attaches exactly one, `app-release.apk`; that name
 * wins, then any other uploaded `.apk` that is not a debug build.
 */
internal fun pickReleaseApk(assets: List<ReleaseAsset>): ReleaseApk? {
    val apks = assets.filter {
        it.uploaded && it.name.endsWith(".apk", ignoreCase = true) &&
            (it.url.startsWith("https://"))
    }
    val chosen = apks.firstOrNull { it.name.equals("app-release.apk", ignoreCase = true) }
        ?: apks.firstOrNull { !it.name.contains("debug", ignoreCase = true) }
        ?: return null
    return ReleaseApk(name = chosen.name, url = chosen.url, size = chosen.size)
}

/** The debug APK of `debug-latest`: `app-debug.apk`, else any uploaded https `.apk` with "debug" in it. */
internal fun pickTestBuildApk(assets: List<ReleaseAsset>): ReleaseApk? {
    val apks = assets.filter {
        it.uploaded && it.name.endsWith(".apk", ignoreCase = true) && it.url.startsWith("https://")
    }
    val chosen = apks.firstOrNull { it.name.equals("app-debug.apk", ignoreCase = true) }
        ?: apks.firstOrNull { it.name.contains("debug", ignoreCase = true) }
        ?: return null
    return ReleaseApk(name = chosen.name, url = chosen.url, size = chosen.size, isTestBuild = true)
}

/**
 * Reads the `debug-latest` notes build-apk.yml writes:
 * `Branch: <name>` / `Commit: <8 hex>` / `Built: <time>`. Null without a commit line.
 */
internal fun parseTestBuild(body: String): TestBuild? {
    fun field(name: String): String? =
        Regex("^\\s*$name:\\s*(.+?)\\s*$", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
            .find(body)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    val commit = field("Commit")?.lowercase()?.takeIf { COMMIT_PATTERN.matches(it) } ?: return null
    return TestBuild(branch = field("Branch"), commit = commit, built = field("Built"))
}

private val COMMIT_PATTERN = Regex("^[0-9a-f]{7,40}$")

/** Both name the same commit; one may be longer than the other. Unknown is never the same. */
internal fun sameCommit(a: String, b: String): Boolean {
    val x = a.trim().lowercase()
    val y = b.trim().lowercase()
    if (x.length < 7 || y.length < 7) return false
    return x.startsWith(y) || y.startsWith(x)
}

/**
 * Whether a download's Content-Type can be an APK. GitHub serves assets from its
 * object store as `application/octet-stream` (or the uploaded APK type); an HTML,
 * JSON or other text body is an error page or a portal, never the file.
 */
internal fun isApkContentType(contentType: String?): Boolean {
    val type = contentType?.substringBefore(';')?.trim()?.lowercase()
    if (type.isNullOrEmpty()) return true
    if (type.startsWith("text/")) return false
    return !(type.contains("json") || type.contains("html") || type.contains("xml"))
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
