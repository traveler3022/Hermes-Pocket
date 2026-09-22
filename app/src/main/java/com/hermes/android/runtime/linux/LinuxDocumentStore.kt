package com.hermes.android.runtime.linux

import java.io.File
import java.io.FileNotFoundException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.ArrayDeque

/**
 * The persistent part of the proot namespace, without Android's /dev, /proc and /sys binds.
 * Ported from Aether's `AlpineDocumentStore`; this runtime has no separate /workspace mount.
 */
internal class LinuxDocumentStore(private val rootfs: File) {
    val isAvailable: Boolean
        get() = Files.isDirectory(rootfs.toPath(), NOFOLLOW_LINKS) &&
            Files.isRegularFile(File(rootfs, ProotEnvironment.READY_MARKER).toPath(), NOFOLLOW_LINKS)

    fun document(documentId: String): LinuxDocument {
        if (!isAvailable) throw FileNotFoundException("Linux is not installed.")
        val guestPath = guestPath(documentId)
        val entry = resolve(guestPath, followLastLink = false)
        val symbolicLink = Files.isSymbolicLink(entry.file.toPath())
        val target = if (symbolicLink) {
            try {
                resolve(guestPath, followLastLink = true)
            } catch (_: FileNotFoundException) {
                null // Broken links can still be renamed or deleted, without following them.
            }
        } else {
            entry
        }
        return LinuxDocument(
            documentId = documentId,
            guestPath = guestPath,
            entryPath = entry.guestPath,
            file = entry.file,
            target = target?.file,
            targetPath = target?.guestPath,
            isSymbolicLink = symbolicLink,
        )
    }

    fun directory(documentId: String): LinuxDocument = document(documentId).also {
        if (!it.isDirectory) throw FileNotFoundException("Not an Linux directory.")
    }

    fun contentFile(documentId: String): File = document(documentId).let {
        if (!it.isRegularFile) throw FileNotFoundException("Not a regular Linux file.")
        it.target!!
    }

    fun children(documentId: String): List<LinuxDocument> {
        val parent = directory(documentId)
        val names = parent.target!!.list()?.toMutableSet()
            ?: throw FileNotFoundException("Unable to list the Linux directory.")
        if (parent.targetPath == "/") names.removeAll(HostMounts)
        return names.mapNotNull { name ->
            try {
                document(childId(documentId, name)).takeIf {
                    it.isDirectory || it.isRegularFile || it.isSymbolicLink
                }
            } catch (_: FileNotFoundException) {
                null // A guest process may remove an entry while it is being listed.
            }
        }
    }

    @Synchronized
    fun create(parentDocumentId: String, displayName: String, directory: Boolean): String {
        validateName(displayName)
        this.directory(parentDocumentId)
        val dot = displayName.lastIndexOf('.').takeIf { !directory && it > 0 }
        val stem = if (dot == null) displayName else displayName.substring(0, dot)
        val extension = if (dot == null) "" else displayName.substring(dot)
        for (index in 0..10_000) {
            val name = if (index == 0) displayName else "$stem ($index)$extension"
            val id = childId(parentDocumentId, name)
            val target = resolve(guestPath(id), followLastLink = false, allowMissingLeaf = true)
            try {
                if (directory) Files.createDirectory(target.file.toPath()) else Files.createFile(target.file.toPath())
                return id
            } catch (_: FileAlreadyExistsException) {
                // SAF creation must never truncate an existing file, including a broken link.
            }
        }
        throw FileNotFoundException("Unable to find an unused document name.")
    }

    @Synchronized
    fun rename(documentId: String, displayName: String): String {
        validateName(displayName)
        val source = mutableDocument(documentId)
        val newId = childId(parentId(documentId)!!, displayName)
        if (newId == documentId) return documentId
        val destination = resolve(guestPath(newId), followLastLink = false, allowMissingLeaf = true)
        Files.move(source.file.toPath(), destination.file.toPath())
        return newId
    }

    @Synchronized
    fun delete(documentId: String) {
        val source = mutableDocument(documentId)
        // walkFileTree does not follow links: deleting a folder must not delete link targets.
        Files.walkFileTree(source.file.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, error: java.io.IOException?): FileVisitResult {
                if (error != null) throw error
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    fun isChild(parentDocumentId: String, documentId: String): Boolean = try {
        val parent = directory(parentDocumentId)
        val child = document(documentId)
        // Check both names and resolved targets so a tree grant cannot escape through a link.
        isDescendant(parent.guestPath, child.guestPath) &&
            isDescendantOrSelf(parent.targetPath!!, child.targetPath ?: child.entryPath)
    } catch (_: FileNotFoundException) {
        false
    }

    fun documentPath(parentDocumentId: String?, documentId: String): List<String> {
        document(documentId)
        val parent = parentDocumentId ?: RootDocumentId
        if (parent != documentId && !isChild(parent, documentId)) {
            throw FileNotFoundException("Document is outside the requested directory.")
        }
        val result = mutableListOf(documentId)
        while (result.last() != parent) {
            result.add(parentId(result.last()) ?: throw FileNotFoundException("Invalid document path."))
        }
        return result.asReversed()
    }

    private fun mutableDocument(documentId: String): LinuxDocument = document(documentId).also {
        if (it.isProtected) throw FileNotFoundException("Cannot rename or delete an Linux mount root.")
    }

    /** Resolve links in the guest namespace, not relative to the Android filesystem root. */
    private fun resolve(
        guestPath: String,
        followLastLink: Boolean,
        allowMissingLeaf: Boolean = false,
    ): ResolvedPath {
        val pending = ArrayDeque(guestPath.split('/').filter(String::isNotEmpty))
        val resolved = mutableListOf<String>()
        var followedLinks = 0
        while (pending.isNotEmpty()) {
            when (val component = pending.removeFirst()) {
                "." -> continue
                ".." -> {
                    if (resolved.isNotEmpty()) resolved.removeAt(resolved.lastIndex)
                    continue
                }
                else -> resolved.add(component)
            }
            val path = "/" + resolved.joinToString("/")
            val file = hostFile(path)
            if (Files.isSymbolicLink(file.toPath()) && (followLastLink || pending.isNotEmpty())) {
                if (++followedLinks > 40) throw FileNotFoundException("Too many symbolic links.")
                val target = Files.readSymbolicLink(file.toPath()).toString()
                resolved.removeAt(resolved.lastIndex)
                if (target.startsWith('/')) resolved.clear()
                target.split('/').filter(String::isNotEmpty).asReversed().forEach(pending::addFirst)
            } else {
                if (!Files.exists(file.toPath(), NOFOLLOW_LINKS) && !(allowMissingLeaf && pending.isEmpty())) {
                    throw FileNotFoundException("Linux document does not exist.")
                }
                if (pending.isNotEmpty() && !Files.isDirectory(file.toPath(), NOFOLLOW_LINKS)) {
                    throw FileNotFoundException("Not an Linux directory.")
                }
            }
        }
        val path = "/" + resolved.joinToString("/")
        return ResolvedPath(path, hostFile(path))
    }

    private fun hostFile(guestPath: String): File {
        if (guestPath.substringAfter('/').substringBefore('/') in HostMounts) {
            throw FileNotFoundException("Android system mounts are not shared through SAF.")
        }
        return File(rootfs, guestPath.removePrefix("/"))
    }

    private data class ResolvedPath(val guestPath: String, val file: File)

    companion object {
        const val RootId = "linux"
        const val RootDocumentId = "linux:/"
        private val HostMounts = setOf("dev", "proc", "sys")

        fun guestPath(documentId: String): String {
            if (!documentId.startsWith(RootDocumentId) || '\u0000' in documentId) {
                throw FileNotFoundException("Invalid Linux document ID.")
            }
            val path = documentId.removePrefix("linux:")
            if (path != "/" && path.removePrefix("/").split('/').any { it.isEmpty() || it == "." || it == ".." }) {
                throw FileNotFoundException("Invalid Linux document path.")
            }
            return path
        }

        fun parentId(documentId: String): String? {
            val path = guestPath(documentId)
            return if (path == "/") null else "linux:" + path.substringBeforeLast('/').ifEmpty { "/" }
        }

        private fun childId(parentDocumentId: String, name: String): String {
            validateName(name)
            return parentDocumentId.trimEnd('/') + "/" + name
        }

        private fun validateName(name: String) {
            if (name.isEmpty() || name == "." || name == ".." || '/' in name || '\u0000' in name) {
                throw FileNotFoundException("Invalid document name.")
            }
        }

        private fun isDescendant(parent: String, child: String): Boolean =
            child != parent && child.startsWith(parent.trimEnd('/') + "/")

        private fun isDescendantOrSelf(parent: String, child: String): Boolean =
            child == parent || isDescendant(parent, child)
    }
}

internal data class LinuxDocument(
    val documentId: String,
    val guestPath: String,
    val entryPath: String,
    val file: File,
    val target: File?,
    val targetPath: String?,
    val isSymbolicLink: Boolean,
) {
    val displayName: String get() = if (guestPath == "/") "Linux" else guestPath.substringAfterLast('/')
    val isDirectory: Boolean get() = target?.isDirectory == true
    val isRegularFile: Boolean get() = target?.isFile == true
    val isProtected: Boolean get() = entryPath == "/"
    val canModify: Boolean get() = !isProtected && file.parentFile?.canWrite() == true
    val size: Long get() = if (isRegularFile) target!!.length() else 0L
    val lastModified: Long get() = target?.lastModified() ?: 0L
}
