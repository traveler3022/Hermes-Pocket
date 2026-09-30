package com.hermes.android.data

import com.hermes.android.runtime.linux.ProotEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import javax.inject.Inject
import javax.inject.Singleton

/** What a found file is, for the Media / Files / Music tabs of the Storage page. */
enum class FoundKind { Image, Video, Audio, Other }

/** The places whose files may go, and what the Folders tab calls a folder's root. */
enum class FileArea { Home, Temp, HermesCache }

data class FoundFile(
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val kind: FoundKind,
    /** The folder it sits in, inside the Linux (/root/Downloads). */
    val guestDir: String,
    /** Host path of the folder it is counted under. */
    val folder: String,
    /** In the file tabs; files under hidden folders or dependency trees only count in their folder. */
    val listed: Boolean,
)

data class FoundFolder(
    val path: String,
    /** The folder inside the Linux, e.g. /root/Downloads. */
    val guestPath: String,
    /** Its path below the area ("Downloads", "projects/app"); empty for the area itself. */
    val relative: String,
    val area: FileArea,
    val size: Long,
    val count: Int,
)

/** Telegram's CacheModel for this app: folders instead of chats, then the files by kind. */
data class FileScan(
    val folders: List<FoundFolder>,
    val media: List<FoundFile>,
    val documents: List<FoundFile>,
    val music: List<FoundFile>,
    /** Every file of each folder (listed or not), for deleting a whole folder. */
    val filesByFolder: Map<String, List<FoundFile>>,
)

/**
 * The user's own files and the temporary and cached ones, file by file, for the tabs under the
 * Storage chart. Only three kinds of place are looked at: the home folder (without its hidden
 * entries: Hermes itself, its keys and settings, every tool's config), the temp folders, and
 * Hermes' media caches. The system, the installs and the app's settings are never in a scan, so
 * nothing there can be picked. Nothing is picked for the user either: each file or folder is
 * chosen by hand, as in Telegram.
 */
@Singleton
class StorageFiles @Inject constructor(
    private val environment: ProotEnvironment,
) {

    private fun areas(): List<Pair<FileArea, File>> =
        listOf(
            FileArea.Home to environment.guestFile("/root"),
            FileArea.Temp to environment.guestFile("/tmp"),
            FileArea.Temp to environment.guestFile("/var/tmp"),
        ) + HERMES_MEDIA_CACHES.map { FileArea.HermesCache to environment.guestFile("/root/.hermes/$it") }

    suspend fun scan(): FileScan = withContext(Dispatchers.IO) {
        FoundFiles.scan(areas(), environment.rootfsDir)
    }

    /** Deletes [paths] (from a [scan]); returns the bytes freed. */
    suspend fun delete(paths: Collection<String>, onProgress: (Float) -> Unit): Long = withContext(Dispatchers.IO) {
        FoundFiles.delete(paths, areas(), onProgress)
    }

    private companion object {
        /** Hermes' media caches, current and legacy names (hermes_constants.get_hermes_dir). */
        val HERMES_MEDIA_CACHES = listOf(
            "cache/images", "cache/vision", "cache/screenshots", "cache/video", "cache/videos",
            "cache/documents", "cache/audio",
            "image_cache", "temp_vision_images", "browser_screenshots", "temp_video_files",
            "video_cache", "document_cache", "audio_cache",
        )
    }
}

/** The walking and deleting of [StorageFiles], apart from Android so they can be tested on the JVM. */
internal object FoundFiles {

    /** Telegram lists every cached file; past this many per tab the smallest are left out. */
    const val MAX_LISTED = 1000

    private const val LINK2SYMLINK_PREFIX = ".l2s."
    private val X_ENTRIES = Regex("""\.X\d+-lock|\.(X11|ICE|XIM|font|Test)-unix""")

    /** Trees whose files would bury the list; they still count in their folder. */
    private val DEPENDENCY_DIRS = setOf("node_modules", "venv", ".venv", "__pycache__", "site-packages", ".git")

    private val IMAGE = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic", "heif", "tiff", "ico")
    private val VIDEO = setOf("mp4", "webm", "mov", "m4v", "mkv", "avi", "3gp")
    private val AUDIO = setOf("mp3", "m2a", "wav", "ogg", "oga", "opus", "m4a", "aac", "flac", "amr", "mid", "midi")

    fun kindOf(name: String): FoundKind = when (name.substringAfterLast('.', "").lowercase()) {
        in IMAGE -> FoundKind.Image
        in VIDEO -> FoundKind.Video
        in AUDIO -> FoundKind.Audio
        else -> FoundKind.Other
    }

    fun scan(areas: List<Pair<FileArea, File>>, rootfs: File): FileScan {
        val files = ArrayList<FoundFile>()
        val folderArea = HashMap<String, Pair<FileArea, Path>>()
        val rootfsPath = rootfs.toPath()
        for ((area, dir) in areas) {
            val root = dir.toPath()
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) continue
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (dir == root) return FileVisitResult.CONTINUE
                    val name = dir.fileName.toString()
                    // The home folder's hidden entries are Hermes and every tool's own state.
                    if (area == FileArea.Home && dir.parent == root && name.startsWith(".")) return FileVisitResult.SKIP_SUBTREE
                    if (X_ENTRIES.matches(name)) return FileVisitResult.SKIP_SUBTREE
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (!attrs.isRegularFile || attrs.size() <= 0) return FileVisitResult.CONTINUE
                    val name = file.fileName.toString()
                    if (name.startsWith(LINK2SYMLINK_PREFIX) || X_ENTRIES.matches(name)) return FileVisitResult.CONTINUE
                    if (area == FileArea.Home && file.parent == root && name.startsWith(".")) return FileVisitResult.CONTINUE
                    val relParent = root.relativize(file.parent)
                    val segments = if (relParent.toString().isEmpty()) emptyList() else relParent.map { it.toString() }
                    val group = segments.take(2).fold(root) { path, segment -> path.resolve(segment) }
                    val groupKey = group.toString()
                    folderArea.getOrPut(groupKey) { area to root }
                    files += FoundFile(
                        path = file.toString(),
                        name = name,
                        size = attrs.size(),
                        modified = attrs.lastModifiedTime().toMillis(),
                        kind = kindOf(name),
                        guestDir = "/" + rootfsPath.relativize(file.parent).toString(),
                        folder = groupKey,
                        listed = segments.none { it.startsWith(".") || it in DEPENDENCY_DIRS },
                    )
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
            })
        }

        val byFolder = files.groupBy { it.folder }
        val folders = byFolder.map { (key, list) ->
            val (area, root) = folderArea.getValue(key)
            val path = Paths.get(key)
            FoundFolder(
                path = key,
                guestPath = "/" + rootfsPath.relativize(path).toString(),
                relative = root.relativize(path).toString(),
                area = area,
                size = list.sumOf { it.size },
                count = list.size,
            )
        }.sortedByDescending { it.size }
        // CacheModel.sortBySize: biggest first.
        val listed = files.filter { it.listed }.sortedByDescending { it.size }
        fun tab(vararg kinds: FoundKind) = listed.filter { it.kind in kinds }.take(MAX_LISTED)
        return FileScan(
            folders = folders,
            media = tab(FoundKind.Image, FoundKind.Video),
            documents = tab(FoundKind.Other),
            music = tab(FoundKind.Audio),
            filesByFolder = byFolder,
        )
    }

    /**
     * Deletes the files at [paths] and then any folder that was left empty, up to the area. A
     * path outside the areas, a hidden entry of the home folder, a link or link2symlink data is
     * refused whatever asked for it.
     */
    fun delete(paths: Collection<String>, areas: List<Pair<FileArea, File>>, onProgress: (Float) -> Unit): Long {
        val roots = areas.map { (area, dir) -> area to dir.toPath().toAbsolutePath().normalize() }
        var freed = 0L
        paths.forEachIndexed { index, raw ->
            val path = Paths.get(raw).toAbsolutePath().normalize()
            val root = roots.firstOrNull { (area, root) -> allowed(path, area, root) }?.second
            if (root != null) {
                val attrs = runCatching {
                    Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                }.getOrNull()
                if (attrs != null && attrs.isRegularFile && runCatching { Files.delete(path) }.isSuccess) {
                    freed += attrs.size()
                    var dir = path.parent
                    // Fails on the first folder that still holds something, which ends the climb.
                    while (dir != null && dir != root && dir.startsWith(root) && runCatching { Files.delete(dir) }.isSuccess) {
                        dir = dir.parent
                    }
                }
            }
            onProgress((index + 1f) / paths.size)
        }
        return freed
    }

    private fun allowed(path: Path, area: FileArea, root: Path): Boolean {
        if (!path.startsWith(root) || path == root) return false
        val first = root.relativize(path).getName(0).toString()
        if (area == FileArea.Home && first.startsWith(".")) return false
        val name = path.fileName.toString()
        return !name.startsWith(LINK2SYMLINK_PREFIX) && !X_ENTRIES.matches(name)
    }
}
