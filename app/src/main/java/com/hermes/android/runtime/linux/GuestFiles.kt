package com.hermes.android.runtime.linux

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * The Linux filesystem as seen from inside the guest. Paths are guest paths ("/root/notes.txt");
 * symlinks are followed the way the guest would follow them, so absolute link targets stay
 * inside [rootfs] instead of escaping to the host.
 */
class GuestFiles(private val rootfs: File) {

    val isReady: Boolean get() = File(rootfs, ProotEnvironment.READY_MARKER).isFile

    val freeBytes: Long get() = rootfs.usableSpace

    /** Host file for [guestPath] after resolving every symlink along the way. */
    fun hostFile(guestPath: String): File {
        val pending = ArrayDeque(split(guestPath))
        val resolved = ArrayList<String>()
        var hops = 0
        while (pending.isNotEmpty()) {
            val name = pending.removeFirst()
            if (name == "..") {
                resolved.removeLastOrNull()
                continue
            }
            val candidate = File(rootfs, (resolved + name).joinToString("/"))
            if (!Files.isSymbolicLink(candidate.toPath())) {
                resolved += name
                continue
            }
            if (++hops > MAX_LINK_HOPS) throw FileNotFoundException("Too many symlinks: $guestPath")
            val target = guestTarget(Files.readSymbolicLink(candidate.toPath()).toString())
            if (target.startsWith("/")) resolved.clear()
            split(target).asReversed().forEach(pending::addFirst)
        }
        if (resolved.firstOrNull() in SystemMounts) throw FileNotFoundException("Not shared: $guestPath")
        return File(rootfs, resolved.joinToString("/"))
    }

    /** [rootfs] as a host path, as given and with symlinks resolved (/data/user/0 → /data/data). */
    private val hostRoots: List<String> by lazy {
        listOf(rootfs.absolutePath, runCatching { rootfs.canonicalPath }.getOrDefault(rootfs.absolutePath)).distinct()
    }

    /**
     * proot's link2symlink (every hard link in the guest: git objects, uv) writes host paths
     * into its links, which the guest sees without the rootfs prefix. Read as guest paths they
     * led nowhere, so such files were missing from the Files app.
     */
    private fun guestTarget(target: String): String {
        for (root in hostRoots) {
            if (target == root) return ROOT
            if (target.startsWith("$root/")) return target.substring(root.length)
        }
        return target
    }

    fun exists(guestPath: String): Boolean = hostFile(guestPath).exists()

    fun isDirectory(guestPath: String): Boolean = hostFile(guestPath).isDirectory

    /** Child names of a directory, without the host's system mounts and proot's bookkeeping. */
    fun list(guestPath: String): List<String> {
        val dir = hostFile(guestPath)
        val names = dir.list() ?: throw FileNotFoundException("Cannot list $guestPath")
        return names.filterNot { name ->
            name.startsWith(LINK2SYMLINK_PREFIX) ||
                (guestPath == ROOT && (name in SystemMounts || name == ProotEnvironment.READY_MARKER))
        }.sorted()
    }

    fun create(parent: String, displayName: String, directory: Boolean): String {
        val dir = hostFile(parent)
        if (!dir.isDirectory) throw FileNotFoundException("Not a directory: $parent")
        val name = freeName(dir, cleanName(displayName))
        val file = File(dir, name)
        val ok = if (directory) file.mkdir() else file.createNewFile()
        if (!ok) throw IOException("Cannot create $name")
        return child(parent, name)
    }

    fun rename(guestPath: String, displayName: String): String {
        requireNotRoot(guestPath)
        val parent = parentOf(guestPath)
        val name = cleanName(displayName)
        val source = hostFile(parent).resolve(nameOf(guestPath))
        val target = hostFile(parent).resolve(name)
        if (target.exists()) throw IOException("$name already exists")
        Files.move(source.toPath(), target.toPath())
        return child(parent, name)
    }

    /** Deletes the entry itself; a symlink is removed, never the directory it points to. */
    fun delete(guestPath: String) {
        requireNotRoot(guestPath)
        val entry = hostFile(parentOf(guestPath)).resolve(nameOf(guestPath)).toPath()
        deleteTree(entry)
    }

    private fun deleteTree(path: Path) {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            Files.newDirectoryStream(path).use { children -> children.forEach(::deleteTree) }
        }
        Files.delete(path)
    }

    private fun requireNotRoot(guestPath: String) {
        if (guestPath == ROOT) throw IOException("The root cannot be changed")
    }

    private fun freeName(dir: File, name: String): String {
        if (!File(dir, name).exists()) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val base = name.substring(0, dot)
        val ext = name.substring(dot)
        return (1..MAX_NAME_TRIES).asSequence()
            .map { "$base ($it)$ext" }
            .firstOrNull { !File(dir, it).exists() }
            ?: throw IOException("No free name for $name")
    }

    companion object {
        const val ROOT = "/"
        private const val MAX_LINK_HOPS = 40
        private const val MAX_NAME_TRIES = 999
        private const val LINK2SYMLINK_PREFIX = ".l2s."
        private val SystemMounts = setOf("dev", "proc", "sys")

        fun split(path: String): List<String> = path.split('/').filter { it.isNotEmpty() && it != "." }

        /** Canonical guest path for a document id; rejects anything that climbs with "..". */
        fun normalize(path: String): String {
            val parts = split(path)
            if (!path.startsWith("/") || ".." in parts) throw FileNotFoundException("Bad path: $path")
            return "/" + parts.joinToString("/")
        }

        fun child(parent: String, name: String): String = if (parent == ROOT) "/$name" else "$parent/$name"

        fun parentOf(path: String): String = path.substringBeforeLast('/').ifEmpty { ROOT }

        fun nameOf(path: String): String = path.substringAfterLast('/')

        fun cleanName(name: String): String {
            val clean = name.replace('/', '_').trim()
            if (clean.isEmpty() || clean == "." || clean == "..") throw IOException("Invalid name: $name")
            return clean
        }
    }
}
