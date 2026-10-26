package com.hermes.android.data

import com.hermes.android.domain.storage.SpaceExplorer
import com.hermes.android.domain.storage.SpaceItem
import com.hermes.android.domain.storage.SpaceOverview
import com.hermes.android.domain.storage.SpacePlace
import com.hermes.android.domain.storage.SpaceVerdict
import com.hermes.android.runtime.linux.ProotEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The whole built-in Linux by place, for the Storage page's "Where the space goes". Deleting runs
 * from outside proot, like [StorageCleaner]: proot refuses to delete link2symlink entries.
 */
@Singleton
class StorageSpace @Inject constructor(
    private val environment: ProotEnvironment,
) : SpaceExplorer {

    /** Folder sizes from the last scan, so opening a folder doesn't walk it again. */
    @Volatile private var sizes: Map<String, Long> = emptyMap()

    override suspend fun scan(): SpaceOverview = withContext(Dispatchers.IO) {
        val result = SpaceWalk.scan(environment.rootfsDir.toPath())
        sizes = result.dirSizes
        result.overview
    }

    override suspend fun list(guestDir: String): List<SpaceItem> = withContext(Dispatchers.IO) {
        SpaceWalk.list(environment.rootfsDir.toPath(), guestDir, sizes)
    }

    override suspend fun delete(guestPaths: Collection<String>, onProgress: (Float) -> Unit): Long = withContext(Dispatchers.IO) {
        val rootfs = environment.rootfsDir.toPath()
        var freed = 0L
        guestPaths.forEachIndexed { index, guest ->
            freed += SpaceWalk.delete(rootfs, guest)
            onProgress((index + 1f) / guestPaths.size)
        }
        // Sizes changed; the next scan fills them again.
        sizes = emptyMap()
        freed
    }
}

/** The rules and walks of [StorageSpace], apart from Android so they can be tested on the JVM. */
internal object SpaceWalk {

    /** Folders below this depth are sized when opened instead of kept from the scan. */
    private const val KEPT_DEPTH = 6

    private const val LINK2SYMLINK_PREFIX = ".l2s."
    private val X_ENTRIES = Regex("""\.X\d+-lock|\.(X11|ICE|XIM|font|Test)-unix""")

    /** Folders that only hold places; they belong to none themselves. */
    private val CONTAINERS = setOf("/", "/root")

    /** Kernel mounts proot binds over these; on disk they are empty. */
    private val SKIPPED = setOf("/proc", "/sys", "/dev")

    private const val HERMES = "/root/.hermes"

    /** Exact places; the longest match wins. Anything else under /root is the user's or a program's settings. */
    private val RULES: List<Pair<String, SpacePlace>> = listOf(
        "$HERMES/hermes-agent" to SpacePlace.Hermes,
        "$HERMES/installs" to SpacePlace.Hermes,
        "$HERMES/tools" to SpacePlace.Hermes,
        "$HERMES/bin" to SpacePlace.Hermes,
        "$HERMES/uploads" to SpacePlace.Uploads,
        "$HERMES/attachments" to SpacePlace.Uploads,
        "$HERMES/hosted-room-attachments" to SpacePlace.Uploads,
        "$HERMES/images" to SpacePlace.HermesMade,
        "$HERMES/cache" to SpacePlace.HermesMade,
        "$HERMES/logs" to SpacePlace.HermesMade,
        "$HERMES/image_cache" to SpacePlace.HermesMade,
        "$HERMES/audio_cache" to SpacePlace.HermesMade,
        "$HERMES/document_cache" to SpacePlace.HermesMade,
        "$HERMES/video_cache" to SpacePlace.HermesMade,
        "$HERMES/temp_vision_images" to SpacePlace.HermesMade,
        "$HERMES/temp_video_files" to SpacePlace.HermesMade,
        "$HERMES/browser_screenshots" to SpacePlace.HermesMade,
        "$HERMES/backups" to SpacePlace.Backups,
        HERMES to SpacePlace.HermesData,
        "/root/.cache" to SpacePlace.Caches,
        "/root/.npm" to SpacePlace.Caches,
        // Hermes' own launchers (hermes, hermes-agent, hermes-acp) are linked here.
        "/root/.local/bin" to SpacePlace.System,
        "/root/.local" to SpacePlace.Installed,
        "/root/.npm-global" to SpacePlace.Installed,
        "/root/.cargo" to SpacePlace.Installed,
        "/root/.rustup" to SpacePlace.Installed,
        "/root/.bun" to SpacePlace.Installed,
        "/root/.deno" to SpacePlace.Installed,
        "/root/.nvm" to SpacePlace.Installed,
        "/root/.pyenv" to SpacePlace.Installed,
        "/root/go" to SpacePlace.Installed,
        "/tmp" to SpacePlace.Temp,
        "/var/tmp" to SpacePlace.Temp,
        "/var/log" to SpacePlace.Temp,
    ).sortedByDescending { it.first.length }

    /** The folders the rules name: emptied but never removed, since programs expect them. */
    private val RULE_DIRS = RULES.map { it.first }.toSet()

    /** The place [guest] belongs to; null for the folders that only hold places. */
    fun placeOf(guest: String): SpacePlace? {
        if (guest in CONTAINERS) return null
        RULES.firstOrNull { (prefix, _) -> guest == prefix || guest.startsWith("$prefix/") }?.let { return it.second }
        if (guest.startsWith("/root/")) {
            return if (guest.removePrefix("/root/").startsWith(".")) SpacePlace.System else SpacePlace.YourFiles
        }
        return SpacePlace.System
    }

    /** Whether [guest] may be deleted: its place may go and nothing Hermes needs sits inside it. */
    fun deletable(guest: String): Boolean {
        if (!isClean(guest)) return false
        val place = placeOf(guest) ?: return false
        if (place.verdict == SpaceVerdict.Needed) return false
        return RULES.none { (prefix, rulePlace) -> rulePlace.verdict == SpaceVerdict.Needed && prefix.startsWith("$guest/") }
    }

    private fun isClean(guest: String): Boolean =
        guest.startsWith("/") && guest.split('/').none { it == "." || it == ".." } && !guest.endsWith("/")

    fun guestOf(rootfs: Path, path: Path): String {
        val rel = rootfs.relativize(path).toString()
        return if (rel.isEmpty()) "/" else "/" + rel.replace('\\', '/')
    }

    private fun hostOf(rootfs: Path, guest: String): Path = if (guest == "/") rootfs else rootfs.resolve(guest.removePrefix("/"))

    private fun child(parent: String, name: String) = if (parent == "/") "/$name" else "$parent/$name"

    class ScanResult(val overview: SpaceOverview, val dirSizes: Map<String, Long>)

    private class Frame(val guest: String, val place: SpacePlace?, val depth: Int) {
        var size = 0L
    }

    fun scan(rootfs: Path): ScanResult {
        val totals = HashMap<SpacePlace, Long>()
        val roots = HashMap<SpacePlace, MutableList<SpaceItem>>()
        val dirSizes = HashMap<String, Long>()
        if (!Files.isDirectory(rootfs, LinkOption.NOFOLLOW_LINKS)) {
            return ScanResult(SpaceOverview(0, emptyMap(), emptyMap()), emptyMap())
        }
        val stack = ArrayDeque<Frame>()

        fun isRoot(place: SpacePlace, parent: Frame?) = parent == null || parent.place != place

        Files.walkFileTree(rootfs, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val guest = guestOf(rootfs, dir)
                if (guest in SKIPPED) return FileVisitResult.SKIP_SUBTREE
                stack.addLast(Frame(guest, placeOf(guest), stack.size))
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                // Links free nothing; a link2symlink hard link is a link to its .l2s data, counted once there.
                if (!attrs.isRegularFile) return FileVisitResult.CONTINUE
                val guest = guestOf(rootfs, file)
                val place = placeOf(guest) ?: return FileVisitResult.CONTINUE
                val size = attrs.size()
                totals[place] = (totals[place] ?: 0L) + size
                for (frame in stack) if (frame.place == place) frame.size += size
                if (isRoot(place, stack.lastOrNull()) && size > 0) {
                    roots.getOrPut(place) { ArrayList() } += SpaceItem(guest, file.fileName.toString(), size, false, place, deletable(guest))
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                val frame = stack.removeLast()
                val place = frame.place ?: return FileVisitResult.CONTINUE
                if (frame.depth <= KEPT_DEPTH) dirSizes[frame.guest] = frame.size
                if (isRoot(place, stack.lastOrNull()) && frame.size > 0) {
                    roots.getOrPut(place) { ArrayList() } +=
                        SpaceItem(frame.guest, dir.fileName.toString(), frame.size, true, place, deletable(frame.guest))
                }
                return FileVisitResult.CONTINUE
            }
        })
        val overview = SpaceOverview(
            total = totals.values.sum(),
            sizes = totals,
            roots = roots.mapValues { (_, list) -> list.sortedByDescending { it.size } },
        )
        return ScanResult(overview, dirSizes)
    }

    /** The entries of [guestDir], each sized by what belongs to its own place. */
    fun list(rootfs: Path, guestDir: String, known: Map<String, Long>): List<SpaceItem> {
        if (!isClean(guestDir)) return emptyList()
        val dir = hostOf(rootfs, guestDir)
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) return emptyList()
        val entries = dir.toFile().listFiles().orEmpty()
        return entries.mapNotNull { entry ->
            val guest = child(guestDir, entry.name)
            if (guest in SKIPPED) return@mapNotNull null
            val place = placeOf(guest) ?: return@mapNotNull null
            val path = entry.toPath()
            val attrs = runCatching {
                Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            }.getOrNull() ?: return@mapNotNull null
            when {
                attrs.isDirectory -> SpaceItem(guest, entry.name, known[guest] ?: placeSize(rootfs, path, place), true, place, deletable(guest))
                attrs.isRegularFile -> SpaceItem(guest, entry.name, attrs.size(), false, place, deletable(guest))
                else -> null
            }
        }.filter { it.size > 0 || it.isDirectory }.sortedByDescending { it.size }
    }

    /** What under [root] belongs to [place]. */
    private fun placeSize(rootfs: Path, root: Path, place: SpacePlace): Long {
        var bytes = 0L
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile && placeOf(guestOf(rootfs, file)) == place) bytes += attrs.size()
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
        })
        return bytes
    }

    /**
     * Deletes [guest] without following links. A folder a rule names is emptied, not removed.
     * link2symlink data and X server sockets stay: other paths or a running desktop rely on them.
     */
    fun delete(rootfs: Path, guest: String): Long {
        if (!deletable(guest)) return 0
        val root = hostOf(rootfs, guest)
        if (!root.normalize().startsWith(rootfs.normalize()) || root.normalize() == rootfs.normalize()) return 0
        val attrs = runCatching {
            Files.readAttributes(root, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        }.getOrNull() ?: return 0
        if (!attrs.isDirectory) {
            val name = root.fileName.toString()
            if (name.startsWith(LINK2SYMLINK_PREFIX) || X_ENTRIES.matches(name)) return 0
            return if (runCatching { Files.delete(root) }.isSuccess && attrs.isRegularFile) attrs.size() else 0
        }
        val keepRoot = guest in RULE_DIRS
        var bytes = 0L
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                if (dir != root && X_ENTRIES.matches(dir.fileName.toString())) FileVisitResult.SKIP_SUBTREE
                else FileVisitResult.CONTINUE

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val name = file.fileName.toString()
                if (name.startsWith(LINK2SYMLINK_PREFIX) || X_ENTRIES.matches(name)) return FileVisitResult.CONTINUE
                // A rule inside (Hermes' launchers in ~/.local/bin) keeps its own place.
                if (!deletable(guestOf(rootfs, file))) return FileVisitResult.CONTINUE
                if (runCatching { Files.delete(file) }.isSuccess && attrs.isRegularFile) bytes += attrs.size()
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                // Fails while anything kept is still inside, which is what keeps it.
                if (dir != root || !keepRoot) runCatching { Files.delete(dir) }
                return FileVisitResult.CONTINUE
            }
        })
        return bytes
    }
}
