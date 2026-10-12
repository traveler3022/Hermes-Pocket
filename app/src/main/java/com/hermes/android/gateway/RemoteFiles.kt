package com.hermes.android.gateway

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * The one way the app reads a file that lives on the Hermes server (a remote gateway or
 * Termux's dashboard): the files the agent sends (`MEDIA:`), shown, played, saved or opened.
 * The built-in Linux needs none of this; its files are on this disk.
 *
 * The same split as Hermes' own desktop (apps/desktop/src/lib/media.ts): images and other
 * files are fetched whole into a cache on disk and used from there, audio and video stream
 * through [client] with Range requests (see MediaStreams).
 *
 * [client] carries the server sign-in and the Tailscale route of the app's shared client,
 * over HTTP/1.1: on HTTP/2 the shared client's 15 s ping fails the whole connection when its
 * pong queues behind a large file on a slow link ("stream was reset: PROTOCOL_ERROR").
 *
 * A cut transfer goes on from where it stopped (Range + If-Range against the file's ETag, which
 * Hermes' FileResponse answers with 206). A cached file is checked with a one-byte ranged
 * request: unchanged costs one byte, a changed file comes back whole as the new copy. Hermes
 * ignores If-None-Match, so that is not used.
 */
@Singleton
class RemoteFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    shared: OkHttpClient,
) {
    val client: OkHttpClient = shared.newBuilder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val root by lazy { File(context.cacheDir, CACHE_DIR) }
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** Whether [url] is a file on the Hermes server (its download endpoint), not a web page. */
    fun isServerFile(url: String): Boolean = serverFileUrl(url) != null

    /**
     * The server file at [url] as a file on this disk, fetched or checked against the server.
     * When the server can't be reached, a copy fetched before is returned as it is.
     */
    suspend fun fetch(url: String): File = withContext(Dispatchers.IO) {
        val httpUrl = serverFileUrl(url) ?: throw IOException("Not a file on the Hermes server")
        val key = cacheKey(httpUrl)
        locks.getOrPut(key) { Mutex() }.withLock { fetchLocked(httpUrl, File(root, key)) }
    }

    private suspend fun fetchLocked(url: HttpUrl, entry: File): File {
        if (!entry.isDirectory && !entry.mkdirs()) throw IOException("Could not create the file cache")
        val copy = Copy(File(entry, serverFileName(url)), File(entry, ETAG))
        val part = Copy(File(entry, PART), File(entry, PART_ETAG))

        val etag = copy.etag()
        if (copy.file.isFile && etag != null) {
            val current = try {
                checkCurrent(url, etag, part)
            } catch (e: IOException) {
                Timber.w("[Files] Check failed, using the copy on disk: ${e.message}")
                true
            }
            if (current) {
                entry.setLastModified(System.currentTimeMillis())
                return copy.file
            }
        }

        var attempt = 0
        while (!part.complete) {
            coroutineContext.ensureActive()
            try {
                resume(url, part)
            } catch (e: ServerFileException) {
                throw e
            } catch (e: IOException) {
                attempt++
                if (attempt > MAX_RETRIES) throw e
                Timber.w("[Files] Transfer cut at ${part.file.length()} bytes (${e.message}); resuming, try $attempt")
                delay(1000L shl (attempt - 1))
            }
        }
        copy.file.delete()
        if (!part.file.renameTo(copy.file)) throw IOException("Could not keep ${copy.file.name}")
        copy.etagFile.delete()
        part.etagFile.renameTo(copy.etagFile)
        entry.setLastModified(System.currentTimeMillis())
        trim(keep = entry)
        return copy.file
    }

    /** A file and the ETag its bytes came with; [complete] once all of them are here. */
    private class Copy(val file: File, val etagFile: File) {
        var complete = false
        fun etag(): String? = etagFile.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
        fun startOver(etag: String?) {
            file.delete()
            if (etag != null) etagFile.writeText(etag) else etagFile.delete()
        }
    }

    /**
     * One-byte request with If-Range: 206 means the copy on disk is current. A 200 means the
     * file changed; its body is the new copy, written into [part] (finished later if cut).
     */
    private suspend fun checkCurrent(url: HttpUrl, etag: String, part: Copy): Boolean {
        val request = Request.Builder().url(url).header("Range", "bytes=0-0").header("If-Range", etag).build()
        client.newCall(request).execute().use { response ->
            if (response.code == 206) return true
            checkStatus(response)
            part.startOver(response.header("ETag"))
            try {
                writeBody(response, part.file, append = false)
                part.complete = true
            } catch (e: IOException) {
                Timber.w("[Files] New copy cut at ${part.file.length()} bytes (${e.message})")
            }
            return false
        }
    }

    /**
     * Fetches the rest of [part]: a ranged request when some of it is here with the ETag it
     * came with, the whole file otherwise (also when the file changed in between: If-Range
     * makes the server send all of it with a 200).
     */
    private suspend fun resume(url: HttpUrl, part: Copy) {
        val etag = part.etag()
        if (etag == null) part.file.delete()
        val have = if (part.file.isFile) part.file.length() else 0L
        val builder = Request.Builder().url(url)
        if (have > 0 && etag != null) builder.header("Range", "bytes=$have-").header("If-Range", etag)
        client.newCall(builder.build()).execute().use { response ->
            when (response.code) {
                206 -> writeBody(response, part.file, append = true)
                416 -> {
                    // Asked past the end: all of it is here already, or the file shrank.
                    if (response.header("Content-Range")?.substringAfter('/')?.toLongOrNull() != have) {
                        part.startOver(null)
                        throw IOException("Server refused the range")
                    }
                }
                else -> {
                    checkStatus(response)
                    part.startOver(response.header("ETag"))
                    writeBody(response, part.file, append = false)
                }
            }
        }
        part.complete = true
    }

    private suspend fun writeBody(response: Response, part: File, append: Boolean) {
        val body = response.body ?: throw IOException("Empty response")
        val expected = body.contentLength()
        var written = 0L
        body.byteStream().use { input ->
            FileOutputStream(part, append).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    written += read
                }
            }
        }
        if (expected >= 0 && written != expected) {
            throw IOException("Got $written of $expected bytes")
        }
    }

    private fun checkStatus(response: Response) {
        if (!response.isSuccessful) throw ServerFileException("HTTP ${response.code}")
    }

    /** Oldest entries go once the cache passes [MAX_CACHE_BYTES]; [keep] (just fetched) stays. */
    private fun trim(keep: File) {
        val entries = root.listFiles()?.filter { it.isDirectory } ?: return
        var total = entries.sumOf { dir -> dir.listFiles()?.sumOf { it.length() } ?: 0L }
        if (total <= MAX_CACHE_BYTES) return
        for (dir in entries.sortedBy { it.lastModified() }) {
            if (total <= MAX_CACHE_BYTES) break
            if (dir == keep || locks[dir.name]?.isLocked == true) continue
            total -= dir.listFiles()?.sumOf { it.length() } ?: 0L
            dir.deleteRecursively()
        }
    }

    /** Not worth another try: the server answered, and said no. */
    private class ServerFileException(message: String) : IOException(message)

    companion object {
        private const val CACHE_DIR = "server-files"
        private const val ETAG = ".etag"
        private const val PART = ".part"
        private const val PART_ETAG = ".part.etag"
        private const val MAX_RETRIES = 4
        private const val MAX_CACHE_BYTES = 512L * 1024 * 1024
        private const val DOWNLOAD_PATH = "/api/files/download"

        /** [url] when it is the server's download endpoint with a file path, else null. */
        fun serverFileUrl(url: String): HttpUrl? =
            url.toHttpUrlOrNull()?.takeIf {
                it.encodedPath.endsWith(DOWNLOAD_PATH) && !it.queryParameter("path").isNullOrBlank()
            }

        /** The cache folder for [url]: the server and the file, never the token a URL may carry. */
        internal fun cacheKey(url: HttpUrl): String {
            val identity = "${url.scheme}://${url.host}:${url.port}${url.encodedPath}?path=${url.queryParameter("path")}"
            return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
                .joinToString("") { "%02x".format(it) }.take(32)
        }

        /** The server file's own name, safe as a file name here (viewers and other apps go by it). */
        internal fun serverFileName(url: HttpUrl): String =
            url.queryParameter("path").orEmpty().substringAfterLast('/').substringAfterLast('\\')
                .filter { it.code >= 32 && it != '/' && it != '\\' }
                .trim().trimStart('.')
                .ifBlank { "file" }
                .take(120)
    }
}
