package com.hermes.android.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import com.hermes.android.runtime.linux.GuestFiles
import com.hermes.android.runtime.linux.LinuxFilesProvider
import com.hermes.android.runtime.linux.ProotEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLDecoder

/**
 * Where the chat's Download button puts files — the only place that decides it.
 *
 * With the built-in Linux installed: [GUEST_DIR] inside it. That is the folder the Files app
 * shows under Hermes (LinuxFilesProvider opens on /root), the path the in-app viewers read,
 * and one Hermes itself can reach, so all of them see the same file at the same path. It is
 * app storage: it survives restarts and updates, not an uninstall.
 *
 * Without it (Termux or a remote gateway) there is no such folder: the phone's
 * Download/Hermes (MediaStore) instead, which Android's Files app shows under Downloads.
 *
 * Names are kept; a different file under a taken name becomes "name (1).ext", and the same
 * file downloaded again is not written twice.
 */
class DownloadStorage(private val context: Context) {

    private val rootfs = ProotEnvironment.rootfsDir(context)
    private val guestFiles = GuestFiles(rootfs)

    /** The host folder downloads go to, or null when the built-in Linux isn't installed. */
    fun linuxDir(): File? = if (guestFiles.isReady) guestFiles.hostFile(GUEST_DIR) else null

    /**
     * Saves the file at [url] under [name]. A file of the built-in Linux (file://) is copied
     * on disk; anything else is fetched with [fetch]. Returns where it went, for the user.
     */
    suspend fun save(url: String, name: String, fetch: suspend (String) -> ByteArray): String =
        withContext(Dispatchers.IO) {
            val fileName = downloadFileName(url, name)
            val dir = linuxDir()
            if (dir == null) {
                saveToPhoneDownloads(fileName, fetch(url))
                return@withContext "Download/Hermes/$fileName"
            }
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Could not create $GUEST_DIR")
            val source = localSource(url)
            val target = if (source != null) {
                freeTarget(dir, fileName) { sameFile(it, source) }.also { if (!it.exists()) copy(source, it) }
            } else {
                val bytes = fetch(url)
                freeTarget(dir, fileName) { it.length() == bytes.size.toLong() && it.readBytes().contentEquals(bytes) }
                    .also { if (!it.exists()) writeAtomically(it) { out -> out.writeBytes(bytes) } }
            }
            LinuxFilesProvider.notifyChildrenChanged(context, GUEST_DIR)
            "$GUEST_DIR/${target.name}"
        }

    /** A file:// URL into the rootfs, as a host file; null for anything else. */
    private fun localSource(url: String): File? {
        if (!url.startsWith("file:")) return null
        val file = runCatching { File(URI(url)).canonicalFile }.getOrNull() ?: return null
        val root = rootfs.canonicalPath + File.separator
        return file.takeIf { it.path.startsWith(root) && it.isFile }
    }

    private fun copy(source: File, target: File) = writeAtomically(target) { out -> source.copyTo(out, overwrite = true) }

    private fun writeAtomically(target: File, write: (File) -> Unit) {
        val partial = File(target.parentFile, ".${target.name}.part")
        try {
            write(partial)
            if (!partial.renameTo(target)) throw IOException("Could not save ${target.name}")
        } finally {
            partial.delete()
        }
    }

    private fun sameFile(a: File, b: File): Boolean {
        if (a.length() != b.length()) return false
        a.inputStream().buffered().use { x ->
            b.inputStream().buffered().use { y ->
                while (true) {
                    val c = x.read()
                    if (c != y.read()) return false
                    if (c == -1) return true
                }
            }
        }
    }

    private fun saveToPhoneDownloads(fileName: String, bytes: ByteArray) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Hermes")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create download entry")
        resolver.openOutputStream(itemUri)?.use { out -> out.write(bytes) }
            ?: throw IOException("Could not open output stream")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(itemUri, values, null, null)
    }

    companion object {
        /** Guest path of the download folder inside the built-in Linux. */
        const val GUEST_DIR = "/root/Downloads"
    }
}

/**
 * The name a download is saved under: [name] when it has one, else the URL's file name.
 * A name without an extension takes the file's own (an image's alt text "chart" → chart.png).
 */
internal fun downloadFileName(url: String, name: String): String {
    val urlName = urlFileName(url)
    val chosen = name.substringAfterLast('/').substringAfterLast('\\').trim().ifBlank { urlName }
        .filter { it != '/' && it != '\\' && it.code >= 32 }
        .trim().trimStart('.')
        .ifBlank { "hermes_file" }
    val ext = urlName.substringAfterLast('.', "")
    return if ('.' !in chosen && ext.isNotEmpty() && ext.length <= 10) "$chosen.$ext" else chosen
}

/** File name in [url]: the last path segment, or the `path` query of a gateway download link. */
private fun urlFileName(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull()
    val queryPath = uri?.rawQuery?.split('&')?.firstOrNull { it.startsWith("path=") }
        ?.let { runCatching { URLDecoder.decode(it.removePrefix("path="), "UTF-8") }.getOrNull() }
    val path = queryPath ?: uri?.path ?: url
    return path.substringBefore('?').substringAfterLast('/')
}

/** [name] in [dir] if free or already this file ([same]); else the first free "name (n).ext". */
internal fun freeTarget(dir: File, name: String, same: (File) -> Boolean): File {
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    val base = name.substring(0, dot)
    val ext = name.substring(dot)
    for (i in 0..999) {
        val file = File(dir, if (i == 0) name else "$base ($i)$ext")
        if (!file.exists() || (file.isFile && same(file))) return file
    }
    throw IOException("No free name for $name")
}
