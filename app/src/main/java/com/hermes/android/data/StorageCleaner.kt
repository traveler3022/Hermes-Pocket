package com.hermes.android.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.Process
import android.os.StatFs
import com.hermes.android.runtime.linux.ProotEnvironment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The kinds of leftovers the Storage screen shows, in the chart's colour order (Telegram's
 * CacheControlActivity sections). Each is something that is made again on its own when needed.
 */
enum class JunkKind {
    Photos, Videos, Documents, Audio, Temp, Browser, Packages, AppCache, Other, Logs,
}

/** One pass over the app's storage: what each kind of leftover takes, and the phone around it. */
data class StorageScan(
    val sizes: Map<JunkKind, Long>,
    /** Everything the app keeps (code, data, cache, the built-in Linux); -1 when Android won't say. */
    val appBytes: Long,
    val deviceTotal: Long,
    val deviceFree: Long,
) {
    val junkBytes: Long get() = sizes.values.sum()
}

/**
 * Finds and clears leftovers across the whole app: its Android cache, the built-in Linux's temp
 * folders and package caches, Hermes' media caches, browser caches, and logs.
 *
 * Only what gets rebuilt or fetched again is touched. Chats, memories, keys, installed tools and
 * the user's own files (Downloads, Uploads, projects) are never in a source. Everything runs from
 * outside proot, like the updater's cache cleanup: proot refuses to delete link2symlink entries.
 */
@Singleton
class StorageCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val environment: ProotEnvironment,
) {

    suspend fun scan(): StorageScan = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val sizes = sources().mapValues { (_, list) -> list.sumOf { JunkFiles.measure(it, now) } }
        val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        StorageScan(
            sizes = sizes,
            appBytes = appBytes(),
            deviceTotal = stat?.totalBytes ?: -1,
            deviceFree = stat?.availableBytes ?: -1,
        )
    }

    /** Clears [kinds]; [onProgress] gets 0..1 as sources finish. Returns the bytes freed. */
    suspend fun clear(kinds: Set<JunkKind>, onProgress: (Float) -> Unit): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val chosen = sources().filterKeys { it in kinds }.values.flatten()
        var freed = 0L
        chosen.forEachIndexed { index, source ->
            freed += runCatching { JunkFiles.clear(source, now) }
                .onFailure { Timber.w(it, "[Storage] Could not clear %s", source) }
                .getOrDefault(0L)
            onProgress((index + 1f) / chosen.size)
        }
        freed
    }

    private fun appBytes(): Long = runCatching {
        val manager = context.getSystemService(StorageStatsManager::class.java) ?: return@runCatching -1L
        val stats = manager.queryStatsForPackage(context.applicationInfo.storageUuid, context.packageName, Process.myUserHandle())
        // dataBytes already holds the cache and the external app folders.
        stats.appBytes + stats.dataBytes
    }.getOrDefault(-1L)

    private fun sources(): Map<JunkKind, List<JunkSource>> {
        fun guest(path: String) = environment.guestFile(path)
        // Hermes' caches, each under cache/<name> and under the older top-level name it migrates
        // from (hermes_constants.get_hermes_dir). Kept for an hour: a turn may be reading one.
        fun hermesCaches(vararg dirs: Pair<String, String>) = dirs.flatMap { (current, legacy) ->
            listOf(
                JunkSource.Contents(guest("$HERMES/cache/$current"), keepRecent = true),
                JunkSource.Contents(guest("$HERMES/$legacy"), keepRecent = true),
            )
        }
        val browserProfiles = listOf(guest("$HERMES/chrome-profile"), guest("/root/.config"))
        return mapOf(
            JunkKind.Photos to hermesCaches("images" to "image_cache", "vision" to "temp_vision_images", "screenshots" to "browser_screenshots"),
            JunkKind.Videos to hermesCaches("video" to "temp_video_files", "videos" to "video_cache"),
            JunkKind.Documents to hermesCaches("documents" to "document_cache"),
            JunkKind.Audio to hermesCaches("audio" to "audio_cache"),
            JunkKind.Temp to listOf(
                // The guest's /tmp is a folder in app storage: no reboot ever empties it.
                JunkSource.Contents(guest("/tmp"), keepRecent = true),
                JunkSource.Contents(guest("/var/tmp"), keepRecent = true),
                // proot's own PROOT_TMP_DIR.
                JunkSource.Contents(File(environment.baseDir, "tmp"), keepRecent = true),
            ),
            JunkKind.Browser to JunkFiles.chromiumCaches(browserProfiles) + listOf(
                // Chromium keeps the default profile's cache under XDG_CACHE_HOME, not the profile.
                JunkSource.Contents(guest("/root/.cache/chromium"), keepRecent = true),
                JunkSource.Contents(guest("/root/.cache/google-chrome"), keepRecent = true),
            ),
            JunkKind.Packages to listOf(
                // Only the downloaded packages: the APKINDEX files next to them are what `apk add`
                // resolves against without an update first.
                JunkSource.Children(guest("/etc/apk/cache"), keepRecent = false) { it.name.endsWith(".apk") },
                JunkSource.Children(guest("/var/cache/apk"), keepRecent = false) { it.name.endsWith(".apk") },
                JunkSource.Contents(guest("/root/.cache/uv"), keepRecent = false),
                JunkSource.Contents(guest("$HERMES/cache/uv"), keepRecent = false),
                JunkSource.Contents(guest("/root/.cache/pip"), keepRecent = false),
                JunkSource.Contents(guest("/root/.npm/_cacache"), keepRecent = false),
            ),
            JunkKind.AppCache to (listOf(context.cacheDir) + context.externalCacheDirs.filterNotNull())
                .map { JunkSource.Contents(it, keepRecent = true) },
            JunkKind.Other to hermesCaches("web" to "web_cache", "exec" to "exec_spill", "delegation" to "delegation_cache") + listOf(
                // Half-finished downloads of Hermes' package manager.
                JunkSource.Contents(guest("$HERMES/cache/partials"), keepRecent = true),
                // .git folders the core repair set aside, and a Linux install that never finished.
                JunkSource.Children(guest("$HERMES/hermes-agent"), keepRecent = false) { it.name.startsWith(".git-old-") },
                JunkSource.Children(environment.baseDir, keepRecent = true) { it.name == "rootfs.staging" },
            ),
            JunkKind.Logs to listOf(
                JunkSource.Logs(guest("$HERMES/logs"), recursive = true),
                JunkSource.Logs(guest(HERMES), recursive = false),
                JunkSource.Logs(guest("/var/log"), recursive = true),
                JunkSource.Logs(File(context.filesDir, "diagnostics"), recursive = false),
            ),
        )
    }

    private companion object {
        const val HERMES = "/root/.hermes"
    }
}

/** A place leftovers sit in, and how much of it may go. */
internal sealed interface JunkSource {
    /** Everything under [dir]; the folder itself stays. */
    data class Contents(val dir: File, val keepRecent: Boolean) : JunkSource

    /** The entries of [dir] that [match] picks, each removed whole. */
    data class Children(val dir: File, val keepRecent: Boolean, val match: (File) -> Boolean) : JunkSource

    /** Logs under [dir]: a live `*.log` is emptied (its writer keeps appending), rotated ones deleted. */
    data class Logs(val dir: File, val recursive: Boolean) : JunkSource
}

/** The file rules of [StorageCleaner], apart from Android so they can be tested on the JVM. */
internal object JunkFiles {

    /** A file written within this long may be in use by a running agent, desktop or the app. */
    const val RECENT_MS = 60 * 60 * 1000L

    /** proot's link2symlink keeps a hard link's data in `.l2s.*`; other paths may point at it. */
    private const val LINK2SYMLINK_PREFIX = ".l2s."

    /** X servers find each other through these; a running desktop needs them. */
    private val X_ENTRIES = Regex("""\.X\d+-lock|\.(X11|ICE|XIM|font|Test)-unix""")

    private val LIVE_LOG = Regex(""".+\.log""")
    /** A live log or one of its rotations (.log.1, .log.old, .log-20260929, gzipped or not). */
    private val ANY_LOG = Regex(""".+\.log(\.\d+|\.old|-\d{8})?(\.gz)?""")

    /** Chromium's own cache folders; a profile folder is the one holding Preferences or Local State. */
    private val CHROMIUM_CACHES = setOf(
        "Cache", "Code Cache", "GPUCache", "ShaderCache", "GrShaderCache",
        "GraphiteDawnCache", "DawnGraphiteCache", "DawnWebGPUCache",
    )
    private const val PROFILE_SEARCH_DEPTH = 4

    fun measure(source: JunkSource, now: Long): Long = visit(source, now, delete = false)

    fun clear(source: JunkSource, now: Long): Long = visit(source, now, delete = true)

    /** Every Chromium cache folder in the profiles under [roots]. */
    fun chromiumCaches(roots: List<File>): List<JunkSource> {
        val found = ArrayList<JunkSource>()
        fun search(dir: File, depth: Int) {
            if (depth > PROFILE_SEARCH_DEPTH || !isRealDirectory(dir.toPath())) return
            val children = dir.listFiles() ?: return
            val isProfile = children.any { it.name == "Preferences" || it.name == "Local State" }
            for (child in children) {
                if (isProfile && child.name in CHROMIUM_CACHES) {
                    found += JunkSource.Contents(child, keepRecent = true)
                } else {
                    search(child, depth + 1)
                }
            }
        }
        roots.forEach { search(it, 0) }
        return found
    }

    private fun visit(source: JunkSource, now: Long, delete: Boolean): Long = when (source) {
        is JunkSource.Contents -> tree(source.dir.toPath(), source.keepRecent, now, delete, keepRoot = true)
        is JunkSource.Children -> {
            val entries = if (isRealDirectory(source.dir.toPath())) source.dir.listFiles().orEmpty() else emptyArray()
            entries.filter(source.match).sumOf { tree(it.toPath(), source.keepRecent, now, delete, keepRoot = false) }
        }
        is JunkSource.Logs -> logs(source, delete)
    }

    /**
     * Walks [root] without following links. A symlinked root is left alone entirely: removing it
     * could unhook a folder the system points somewhere on purpose.
     */
    private fun tree(root: Path, keepRecent: Boolean, now: Long, delete: Boolean, keepRoot: Boolean): Long {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return 0
        if (keepRoot && !isRealDirectory(root)) return 0
        if (Files.isSymbolicLink(root)) return 0
        val cutoff = if (keepRecent) now - RECENT_MS else Long.MAX_VALUE
        var bytes = 0L
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                if (dir != root && X_ENTRIES.matches(dir.fileName.toString())) FileVisitResult.SKIP_SUBTREE
                else FileVisitResult.CONTINUE

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val name = file.fileName.toString()
                val removable = !name.startsWith(LINK2SYMLINK_PREFIX) && !X_ENTRIES.matches(name) &&
                    (attrs.isSymbolicLink || attrs.isRegularFile) && attrs.lastModifiedTime().toMillis() < cutoff
                if (removable) {
                    // A link frees nothing; its target (a .l2s file included) is never followed.
                    val size = if (attrs.isRegularFile) attrs.size() else 0L
                    if (!delete) {
                        bytes += size
                    } else if (runCatching { Files.delete(file) }.isSuccess) {
                        bytes += size
                    }
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                // Fails while anything kept is still inside, which is what keeps it.
                if (delete && (dir != root || !keepRoot)) runCatching { Files.delete(dir) }
                return FileVisitResult.CONTINUE
            }
        })
        return bytes
    }

    private fun logs(source: JunkSource.Logs, delete: Boolean): Long {
        val root = source.dir.toPath()
        if (!isRealDirectory(root)) return 0
        var bytes = 0L
        Files.walkFileTree(root, emptySet(), if (source.recursive) Int.MAX_VALUE else 1, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val name = file.fileName.toString()
                if (!attrs.isRegularFile || name.startsWith(LINK2SYMLINK_PREFIX) || !ANY_LOG.matches(name)) {
                    return FileVisitResult.CONTINUE
                }
                val size = attrs.size()
                val done = when {
                    !delete -> true
                    LIVE_LOG.matches(name) -> runCatching {
                        FileChannel.open(file, StandardOpenOption.WRITE).use { it.truncate(0) }
                    }.isSuccess
                    else -> runCatching { Files.delete(file) }.isSuccess
                }
                if (done) bytes += size
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
        })
        return bytes
    }

    private fun isRealDirectory(path: Path): Boolean = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
}
