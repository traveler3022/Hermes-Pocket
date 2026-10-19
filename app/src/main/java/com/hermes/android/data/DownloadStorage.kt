package com.hermes.android.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.webkit.MimeTypeMap
import com.hermes.android.domain.files.Downloads
import com.hermes.android.domain.files.SavedFile
import com.hermes.android.runtime.FileGatePrompt
import com.hermes.android.runtime.LinuxFileGate
import com.hermes.android.runtime.linux.ProotEnvironment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the Download buttons put files — the only place that decides it: the phone's
 * Download/Hermes (MediaStore), which Android's Files app shows under Downloads.
 *
 * The way out is one way. Files Hermes sends stay in app storage; only the user's tap on
 * Download copies one here, and nothing in Hermes or the built-in Linux can write or read
 * this folder. The Media screen lists what is here — the files this app saved, as Android
 * shows an app only its own entries.
 *
 * Names are kept; a different file under a taken name becomes "name (1).ext", and the same
 * file downloaded again is not written twice.
 *
 * A program passes the same check as on every other way out ([LinuxFileGate]): saved only
 * after the user confirms it, and never when it is named like a photo or a document.
 */
@Singleton
class DownloadStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileGate: LinuxFileGate,
    private val prompt: FileGatePrompt,
) : Downloads {

    private val rootfs = ProotEnvironment.rootfsDir(context)
    private val resolver get() = context.contentResolver
    private val thumbnails = LruCache<String, Bitmap>(THUMBNAIL_CACHE_SIZE)

    /**
     * Saves the file at [url] under [name]. A file of the built-in Linux (file://) is read in
     * place; anything else is fetched with [fetch] to a file on this disk first (never held in
     * memory whole). Returns where it went, for the user.
     */
    override suspend fun save(url: String, name: String, fetch: suspend (String) -> File): String =
        withContext(Dispatchers.IO) {
            val source = localSource(url) ?: fetch(url)
            val wanted = downloadFileName(url, name)
            fileGate.heldBack(source, wanted)?.let { risk ->
                if (!prompt.allowed(source, wanted, risk)) throw IOException("$wanted is a program and was not saved")
            }
            val target = freeName(wanted) { candidate ->
                val existing = ownEntry(candidate) ?: return@freeName NameState.Free
                if (sameContent(existing, source)) NameState.Same else NameState.Other
            }
            val savedName = ownEntry(target)?.let { target } ?: insert(target, source)
            "$RELATIVE_DIR/$savedName"
        }

    override suspend fun list(): List<SavedFile> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.MIME_TYPE,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_ADDED,
        )
        val files = mutableListOf<SavedFile>()
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.IS_PENDING} = 0",
            arrayOf("$RELATIVE_DIR/"),
            "${MediaStore.Downloads.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                files += SavedFile(
                    uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0)).toString(),
                    name = name,
                    mimeType = c.getString(2) ?: mimeOf(name),
                    sizeBytes = c.getLong(3),
                    addedAtMillis = c.getLong(4) * 1000,
                )
            }
        }
        files
    }

    override suspend fun thumbnail(uri: String, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$uri@$sizePx"
        thumbnails.get(key) ?: runCatching { resolver.loadThumbnail(Uri.parse(uri), Size(sizePx, sizePx), null) }
            .getOrNull()
            ?.also { thumbnails.put(key, it) }
    }

    /** A file:// URL into the rootfs, as a host file; null for anything else. */
    private fun localSource(url: String): File? {
        if (!url.startsWith("file:")) return null
        val file = runCatching { File(URI(url)).canonicalFile }.getOrNull() ?: return null
        val root = rootfs.canonicalPath + File.separator
        return file.takeIf { it.path.startsWith(root) && it.isFile }
    }

    /** This app's finished entry named [name] in Download/Hermes, if any. */
    private fun ownEntry(name: String): Pair<Uri, Long>? = resolver.query(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.SIZE),
        "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.DISPLAY_NAME} = ? AND " +
            "${MediaStore.Downloads.IS_PENDING} = 0",
        arrayOf("$RELATIVE_DIR/", name),
        null,
    )?.use { c ->
        if (c.moveToFirst()) {
            ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0)) to c.getLong(1)
        } else {
            null
        }
    }

    private fun sameContent(entry: Pair<Uri, Long>, file: File): Boolean {
        if (entry.second != file.length()) return false
        val stream = runCatching { resolver.openInputStream(entry.first) }.getOrNull() ?: return false
        stream.buffered().use { x ->
            file.inputStream().buffered().use { y ->
                while (true) {
                    val c = x.read()
                    if (c != y.read()) return false
                    if (c == -1) return true
                }
            }
        }
    }

    /**
     * Writes [source] as a new entry named [fileName]; returns the name it got. Android may
     * still rename it if another app's file holds the name, which this app can't see.
     * A failed write leaves no half file behind.
     */
    private fun insert(fileName: String, source: File): String {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeOf(fileName))
            put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_DIR)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create download entry")
        try {
            resolver.openOutputStream(itemUri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Could not open output stream")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(itemUri, null, null)
            throw e
        }
        return resolver.query(itemUri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: fileName
    }

    private fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"

    companion object {
        /** The phone folder downloads go to, as MediaStore's RELATIVE_PATH (no trailing slash). */
        const val RELATIVE_DIR = "Download/Hermes"
        private const val THUMBNAIL_CACHE_SIZE = 120
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

internal enum class NameState { Free, Same, Other }

/** [name] if free or already this file; else the first "name (n).ext" that is. */
internal fun freeName(name: String, state: (String) -> NameState): String {
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    val base = name.substring(0, dot)
    val ext = name.substring(dot)
    for (i in 0..999) {
        val candidate = if (i == 0) name else "$base ($i)$ext"
        if (state(candidate) != NameState.Other) return candidate
    }
    throw IOException("No free name for $name")
}
