package com.hermes.android.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import android.util.Base64
import android.webkit.MimeTypeMap
import com.hermes.android.domain.files.Downloads
import com.hermes.android.domain.files.ServerUploads
import com.hermes.android.gateway.redactCredentials
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.LinuxFileAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.contentOrNull

/** File name start of a long paste sent as an attachment; its chip says "Pasted text". */
internal const val PASTED_TEXT_PREFIX = "pasted_content_"

internal class ChatAttachmentDelegate(
    private val gatewayClient: GatewayClient,
    private val hermesRuntime: HermesRuntime,
    private val linuxFiles: LinuxFileAccess,
    private val context: Context,
    private val downloads: Downloads,
    private val scope: CoroutineScope,
    private val uploads: ServerUploads?,
) {
    /** The most `/api/files/upload-stream` takes (web_server.py `_MANAGED_FILE_MAX_BYTES`). */
    private val maxUploadBytes = 100L * MB
    /** Inline base64 rides one WebSocket frame, which OkHttp refuses past 16 MiB: 12 MB of file. */
    private val maxInlineBytes = 12L * MB
    /** A pasted image is copied into the cache first, at most this big. */
    private val maxAttachBytes = 100 * 1024 * 1024
    private val attachChunkSize = 1024 * 1024

    /** Batches still to attach; [ChatUiState.isAttaching] is true while any is. */
    private val attachBatches = AtomicInteger(0)
    /** One file at a time, in the order picked: Hermes queues images in that order. */
    private val attachLock = Mutex()
    /** The file being uploaded now, by its chip's id, so removing the chip cancels it. */
    private val uploadJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

    /** Attaches [uris] one after another, each the way a single picked file is. */
    fun attachFromUris(state: MutableStateFlow<ChatUiState>, uris: List<Uri>) =
        runBatch(state, uris.isNotEmpty()) { uris }

    /**
     * Images pasted into the composer (the clipboard or a keyboard's image). Their read grant
     * is only lent for a while, so each is copied into the cache first; [grant] (the pasted
     * content) is held until then, since a keyboard's grant ends when it is collected.
     */
    fun attachPasted(state: MutableStateFlow<ChatUiState>, uris: List<Uri>, grant: Any) =
        runBatch(state, uris.isNotEmpty()) {
            try {
                uris.mapNotNull { uri ->
                    try {
                        copyToCache(uri)
                    } catch (e: Exception) {
                        Timber.e(e, "[Chat] Pasted image unreadable")
                        state.update { it.copy(errorEvent = ErrorEvent.Error("Attach failed: ${e.message}")) }
                        null
                    }
                }
            } finally {
                java.lang.ref.Reference.reachabilityFence(grant)
            }
        }

    /**
     * Where the camera writes a photo for the composer: a new file in the app's cache, handed
     * out through our FileProvider. It stays there, as the chat shows it from that uri.
     */
    fun newCameraPhotoUri(): Uri? = try {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val file = java.io.File(attachDir(), "IMG_$stamp.jpg")
        file.createNewFile()
        FileProvider.getUriForFile(context, "${context.packageName}.shared", file)
    } catch (e: Exception) {
        Timber.e(e, "[Chat] No file for the camera photo")
        null
    }

    /**
     * Text pasted past the composer's limit, sent as a `.txt` attachment the way Hermes desktop
     * does (large-paste.ts, same `pasted_content_<stamp>.txt` name). False when there is no chat
     * to attach to or the file can't be written: the text then stays in the box.
     */
    fun attachPastedText(state: MutableStateFlow<ChatUiState>, text: String): Boolean {
        if (state.value.activeSessionId == null) return false
        val uri = try {
            val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val file = java.io.File(attachDir(), "$PASTED_TEXT_PREFIX$stamp.txt")
            file.writeText(text)
            FileProvider.getUriForFile(context, "${context.packageName}.shared", file)
        } catch (e: Exception) {
            Timber.e(e, "[Chat] No file for the pasted text")
            return false
        }
        attachFromUris(state, listOf(uri))
        return true
    }

    private fun runBatch(state: MutableStateFlow<ChatUiState>, any: Boolean, uris: suspend () -> List<Uri>) {
        if (!any || state.value.activeSessionId == null) return
        attachBatches.incrementAndGet()
        state.update { it.copy(isAttaching = true) }
        scope.launch(Dispatchers.IO) {
            try {
                val list = uris()
                // Every picked file shows at once, waiting its turn; removing one cancels it.
                val staged = list.map { uri ->
                    uri to PendingAttachment(
                        name = displayName(uri),
                        isImage = false,
                        localUri = uri.toString(),
                        progress = 0f,
                    )
                }
                state.update { it.copy(pendingAttachments = it.pendingAttachments + staged.map { s -> s.second }) }
                attachLock.withLock {
                    for ((uri, placeholder) in staged) {
                        if (state.value.pendingAttachments.none { it.id == placeholder.id }) continue
                        val job = launch { attachOne(state, uri, placeholder) }
                        uploadJobs[placeholder.id] = job
                        job.join()
                        uploadJobs.remove(placeholder.id)
                    }
                }
            } finally {
                attachBatches.decrementAndGet()
                // Read inside update: a batch started meanwhile keeps it on.
                state.update { it.copy(isAttaching = attachBatches.get() > 0) }
            }
        }
    }

    /** A new folder under the cache's `attach/` for one file, so names never collide. */
    private fun attachDir(): java.io.File =
        java.io.File(context.cacheDir, "attach/" + java.util.UUID.randomUUID().toString()).apply { mkdirs() }

    /** [uri]'s bytes in our cache, under its own name, as a FileProvider uri; at most [maxAttachBytes]. */
    private fun copyToCache(uri: Uri): Uri {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        val name = displayName(uri).replace('/', '_').replace('\u0000', '_').takeLast(120)
            .takeUnless { it.isBlank() || it == "." || it == ".." } ?: "image"
        val file = java.io.File(attachDir(), withImageExtension(name, mime))
        try {
            val input = resolver.openInputStream(uri) ?: throw IllegalStateException("Cannot read file")
            input.use { stream ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(attachChunkSize)
                    var total = 0L
                    while (true) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > maxAttachBytes) throw IllegalStateException("File too large (max 100 MB)")
                        out.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: Exception) {
            // Only our own half-written copy.
            file.delete()
            throw e
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.shared", file)
    }

    private fun displayName(uri: Uri): String =
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i) else null
            }
        } catch (e: Exception) {
            null
        } ?: uri.lastPathSegment ?: "attachment"

    private suspend fun attachOne(state: MutableStateFlow<ChatUiState>, uri: Uri, placeholder: PendingAttachment) {
        val sessionId = state.value.activeSessionId ?: return dropPlaceholder(state, placeholder)
        var jpeg: java.io.File? = null
        try {
            val resolver = context.contentResolver
            val pickedName = placeholder.name
            val pickedMime = resolver.getType(uri) ?: "application/octet-stream"

            // Hermes takes only the image types a model reads (cli._IMAGE_EXTENSIONS), and
            // HEIC isn't one: a phone camera's HEIC photo goes as a JPEG instead.
            jpeg = if (isHeic(pickedMime, pickedName)) heicToJpeg(uri) else null
            val source = jpeg?.let { Uri.fromFile(it) } ?: uri
            val name = if (jpeg != null) pickedName.substringBeforeLast('.') + ".jpg" else withImageExtension(pickedName, pickedMime)
            val mime = if (jpeg != null) "image/jpeg" else pickedMime
            // Hermes queues as images only its own image types and rejects the rest (4016).
            // Everything else, PDFs included, goes through file.attach as on the desktop, and
            // the agent opens it with its tools: pdf.attach needs poppler's pdftoppm, which the
            // built-in Linux doesn't have.
            val isImage = name.substringAfterLast('.', "").lowercase() in hermesImageExtensions

            // A path on Hermes' own disk where there is one: the built-in Linux gets the file
            // copied in, as its desktop app does; remote and Termux get it streamed over HTTP,
            // with its progress on the chip. Only when that route fails do the bytes go inline.
            val serverPath = if (GatewayClient.isStdio(hermesRuntime.getWebSocketUrl())) {
                linuxFiles.upload(source, name)
            } else if (uploads != null && uploads.available) {
                uploadOverHttp(state, placeholder, uploads, source, name)
            } else null
            val b64 = if (serverPath == null) readBase64(source) else ""

            // The upload is done; the attach itself is short and always runs to its answer. Left
            // half-way, Hermes would still queue an image the chat no longer shows, and send it
            // with the next message.
            val cancelled = currentCoroutineContext().job
            val attachment = withContext(NonCancellable) {
                if (isImage) {
                    // image.attach takes only session_id and path, and rejects anything else
                    // (tui_gateway/contracts/prompt_voice.py: ImageAttachParams); the filename
                    // hint belongs to image.attach_bytes alone.
                    val params = buildJsonObject {
                        put("session_id", sessionId)
                        if (serverPath != null) {
                            put("path", serverPath)
                        } else {
                            put("content_base64", b64)
                            put("filename", name)
                        }
                    }
                    val method = if (serverPath != null) "image.attach" else "image.attach_bytes"
                    val result = gatewayClient.request(method, jsonToElementMap(params))
                    val path = ((result as? JsonObject)?.get("path") as? JsonPrimitive)?.contentOrNull
                    placeholder.copy(name = name, isImage = true, gatewayPath = path, progress = null)
                } else {
                    val params = buildJsonObject {
                        put("session_id", sessionId)
                        if (serverPath != null) put("path", serverPath) else put("data_url", "data:$mime;base64,$b64")
                        put("name", name)
                    }
                    val result = gatewayClient.request("file.attach", jsonToElementMap(params))
                    val ref = ((result as? JsonObject)?.get("ref_text") as? JsonPrimitive)?.contentOrNull
                        ?: throw IllegalStateException("Gateway returned no file reference")
                    placeholder.copy(name = name, isImage = false, refText = ref, progress = null)
                }
            }
            if (cancelled.isCancelled || state.value.pendingAttachments.none { it.id == placeholder.id }) {
                // Removed while it was being attached: take it back off Hermes' queue too.
                withContext(NonCancellable) { detachImage(sessionId, attachment) }
                dropPlaceholder(state, placeholder)
                return
            }
            state.update { s ->
                s.copy(pendingAttachments = s.pendingAttachments.map { if (it.id == placeholder.id) attachment else it })
            }
            Timber.i("[Chat] Attached $name (${serverPath ?: "inline"})")
        } catch (e: CancellationException) {
            dropPlaceholder(state, placeholder)
            throw e
        } catch (e: Exception) {
            dropPlaceholder(state, placeholder)
            // Removed by the user mid-upload: no error to show.
            if (!currentCoroutineContext().isActive) return
            Timber.e(e, "[Chat] Attach failed")
            state.update { it.copy(errorEvent = ErrorEvent.Error("Attach failed: ${e.message}")) }
        } finally {
            jpeg?.delete()
        }
    }

    /**
     * Streams [source] to the server and returns its path there, showing the share sent on
     * [placeholder]'s chip. Null when this server can't take it that way (an older Hermes, a
     * refused path) and the file is small enough to go inline instead.
     */
    private suspend fun uploadOverHttp(
        state: MutableStateFlow<ChatUiState>,
        placeholder: PendingAttachment,
        uploads: ServerUploads,
        source: Uri,
        name: String,
    ): String? {
        val size = fileSize(source)
        if (size > maxUploadBytes) throw IllegalStateException("File too large (max ${maxUploadBytes / MB} MB)")
        var shownPercent = -1
        return try {
            uploads.upload(
                name = name,
                size = size,
                open = { context.contentResolver.openInputStream(source) ?: throw java.io.IOException("Cannot read file") },
                onProgress = { sent, total ->
                    if (total <= 0) return@upload
                    val percent = (sent * 100 / total).toInt().coerceIn(0, 100)
                    // One state update per percent, not per buffer.
                    if (percent != shownPercent) {
                        shownPercent = percent
                        setProgress(state, placeholder.id, percent / 100f)
                    }
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            // A cancelled call surfaces as an IOException; that is a cancel, not a failure.
            currentCoroutineContext().ensureActive()
            if (size in 1..maxInlineBytes) {
                Timber.w(e, "[Chat] Upload failed; sending $name inline")
                null
            } else {
                throw e
            }
        }
    }

    private fun setProgress(state: MutableStateFlow<ChatUiState>, id: String, progress: Float) {
        state.update { s ->
            s.copy(pendingAttachments = s.pendingAttachments.map { if (it.id == id) it.copy(progress = progress) else it })
        }
    }

    private fun dropPlaceholder(state: MutableStateFlow<ChatUiState>, placeholder: PendingAttachment) {
        state.update { s -> s.copy(pendingAttachments = s.pendingAttachments.filterNot { it.id == placeholder.id }) }
    }

    /** The bytes behind [uri], or -1 when the provider doesn't say. */
    private fun fileSize(uri: Uri): Long =
        runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }
            .getOrNull()?.takeIf { it >= 0 } ?: -1L

    /** Takes [attachment]'s image back off Hermes' queue for the next turn. */
    private suspend fun detachImage(sessionId: String, attachment: PendingAttachment) {
        val path = attachment.gatewayPath ?: return
        if (!attachment.isImage) return
        try {
            val params = buildJsonObject {
                put("session_id", sessionId)
                put("path", path)
            }
            gatewayClient.request("image.detach", jsonToElementMap(params))
        } catch (e: Exception) {
            Timber.w(e, "[Chat] image.detach failed (ignored)")
        }
    }

    /** [name] with the extension its image [mime] implies when it has none Hermes knows, e.g. a gallery item "IMG_0042". */
    private fun withImageExtension(name: String, mime: String): String {
        if (!mime.startsWith("image/") || name.substringAfterLast('.', "").lowercase() in hermesImageExtensions) return name
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.lowercase()
        return if (ext != null && ext in hermesImageExtensions) "$name.$ext" else name
    }

    /** Hermes' image types (hermes_cli/cli_terminal_input.py `_IMAGE_EXTENSIONS`). */
    private val hermesImageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "tiff", "tif", "svg", "ico")

    private fun isHeic(mime: String, name: String): Boolean =
        mime.startsWith("image/heic") || mime.startsWith("image/heif") ||
            name.substringAfterLast('.', "").lowercase() in setOf("heic", "heif")

    /** The HEIC image as a JPEG in the cache, at most [maxImageSide] px on its long side. */
    private fun heicToJpeg(uri: Uri): java.io.File {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > maxImageSide) {
                val scale = maxImageSide.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
        val file = java.io.File.createTempFile("attach", ".jpg", context.cacheDir)
        try {
            file.outputStream().use {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) {
                    throw IllegalStateException("Could not convert HEIC image")
                }
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            bitmap.recycle()
        }
        return file
    }

    private val maxImageSide = 4096

    /** The file's bytes as base64, for a gateway that can't open it by path. */
    private fun readBase64(uri: Uri): String {
        // One continuous encoder: encoding each read separately put "=" padding
        // mid-string whenever a read was not a multiple of 3 bytes, corrupting the file.
        val encoded = java.io.ByteArrayOutputStream()
        var totalSize = 0
        context.contentResolver.openInputStream(uri)?.use { stream ->
            android.util.Base64OutputStream(encoded, Base64.NO_WRAP).use { encoder ->
                val buffer = ByteArray(attachChunkSize)
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    totalSize += read
                    if (totalSize > maxInlineBytes) {
                        throw IllegalStateException("File too large (max ${maxInlineBytes / MB} MB)")
                    }
                    encoder.write(buffer, 0, read)
                }
            }
        } ?: throw IllegalStateException("Cannot read file")
        if (totalSize == 0) {
            throw IllegalStateException("File is empty")
        }
        return encoded.toString(Charsets.US_ASCII.name())
    }

    fun removeAttachment(state: MutableStateFlow<ChatUiState>, attachment: PendingAttachment) {
        state.update { s -> s.copy(pendingAttachments = s.pendingAttachments.filterNot { it.id == attachment.id }) }
        // Still uploading or waiting: stop it; its own attach, if under way, takes it back off.
        if (attachment.progress != null) {
            uploadJobs[attachment.id]?.cancel()
            return
        }
        val sessionId = state.value.activeSessionId ?: return
        scope.launch { detachImage(sessionId, attachment) }
    }


    fun resolveMediaUrl(raw: String): String {
        if (raw.startsWith("http://") || raw.startsWith("https://") ||
            raw.startsWith("content://") || raw.startsWith("data:")
        ) return raw
        val path = if (raw.startsWith("file://")) raw.removePrefix("file://") else raw
        if (!path.startsWith("/") && !path.startsWith("~")) return raw
        val ws = hermesRuntime.getWebSocketUrl()
        // The built-in runtime has no web server: its files are read straight from the rootfs.
        if (GatewayClient.isStdio(ws)) {
            return hermesRuntime.hostFileForGuestPath(path)?.let { Uri.fromFile(it).toString() } ?: raw
        }
        val base = ws.replaceFirst("ws://", "http://").replaceFirst("wss://", "https://")
            .substringBefore("/api/ws")
        val token = ws.substringAfter("token=", "").substringBefore('&')
        val encoded = java.net.URLEncoder.encode(path, "UTF-8")
        return buildString {
            append(base).append("/api/files/download?path=").append(encoded)
            if (token.isNotEmpty()) append("&token=").append(token)
        }
    }

    /**
     * The text of an HTML file the agent wrote, for an inline `::preview`: a relative path is
     * in the chat's [cwd], as on the desktop (local-preview.ts). Null when it can't be read,
     * is a web URL, is binary or is too large; the preview then shows the file card.
     */
    suspend fun readPreviewFile(file: String, cwd: String?): String? = withContext(Dispatchers.IO) {
        val path = previewPath(file, cwd) ?: return@withContext null
        try {
            val url = resolveMediaUrl(path)
            val bytes = when {
                url.startsWith("file:") -> {
                    val local = java.io.File(Uri.parse(url).path ?: return@withContext null)
                    if (!local.isFile || local.length() > maxPreviewBytes) return@withContext null
                    local.readBytes()
                }
                url.startsWith("http") -> {
                    val file = gatewayClient.fetchFile(url)
                    if (file.length() > maxPreviewBytes) return@withContext null
                    file.readBytes()
                }
                else -> return@withContext null
            }
            if (bytes.size > maxPreviewBytes || bytes.contains(0.toByte())) null else bytes.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.w(e, "[Chat] Preview file unreadable: $path")
            null
        }
    }

    private val maxPreviewBytes = 5 * 1024 * 1024

    /**
     * The text of an HTML file on the remote gateway, for the inline HTML card. WebView can't
     * load it by URL: a tailnet host is reached only through the app's Tailscale proxy, which
     * OkHttp uses and WebView doesn't, and the gateway serves files as downloads. Null when it
     * can't be read, is binary or is too large.
     */
    suspend fun readServerHtml(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = gatewayClient.fetchFile(url)
            if (file.length() > maxPreviewBytes) return@withContext null
            val bytes = file.readBytes()
            if (bytes.contains(0.toByte())) null else bytes.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.w(e, "[Chat] HTML file unreadable: ${redactCredentials(url)}")
            null
        }
    }

    /**
     * A URI another app can read a `::preview` file from, for Share: the built-in Linux's own
     * documents provider (no copy), or a copy in the cache for a remote gateway's file.
     */
    suspend fun previewShareUri(file: String, cwd: String?): Uri? = withContext(Dispatchers.IO) {
        val path = previewPath(file, cwd)?.removePrefix("file://")
            ?.replaceFirst(Regex("^~(?=/|$)"), "/root") ?: return@withContext null
        try {
            if (GatewayClient.isStdio(hermesRuntime.getWebSocketUrl())) {
                if (hermesRuntime.hostFileForGuestPath(path)?.isFile != true) return@withContext null
                return@withContext linuxFiles.documentUri(path)
            }
            val fetched = gatewayClient.fetchFile(resolveMediaUrl(path))
            val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
            val copy = java.io.File(dir, path.substringAfterLast('/'))
            fetched.copyTo(copy, overwrite = true)
            FileProvider.getUriForFile(context, "${context.packageName}.shared", copy)
        } catch (e: Exception) {
            Timber.w(e, "[Chat] Preview file can't be shared: $path")
            null
        }
    }

    /** Where a `::preview` file is: as written when absolute, else in [cwd]; null for a web URL or no cwd. */
    fun previewPath(file: String, cwd: String?): String? {
        val raw = file.trim().removeSurrounding("`")
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) return null
        return when {
            raw.startsWith("/") || raw.startsWith("~") || raw.startsWith("file://") -> raw
            cwd.isNullOrBlank() -> null
            else -> cwd.trimEnd('/') + "/" + raw.removePrefix("./")
        }
    }

    fun downloadFile(state: MutableStateFlow<ChatUiState>, url: String, filename: String) {
        scope.launch {
            try {
                val savedAs = downloads.save(url, filename) { gatewayClient.fetchFile(it) }
                Timber.i("[Chat] Downloaded ${redactCredentials(url)} -> $savedAs")
                state.update { it.copy(errorEvent = ErrorEvent.Warning("Saved to $savedAs")) }
            } catch (e: Exception) {
                // A remote gateway's link carries its token, and errors are kept in the connection journal.
                Timber.e(e, "[Chat] Download failed: ${redactCredentials(url)}")
                state.update { it.copy(errorEvent = ErrorEvent.Error("Download failed: ${e.message}")) }
            }
        }
    }

    private fun jsonToElementMap(obj: JsonObject): Map<String, kotlinx.serialization.json.JsonElement> = obj.toMap()
}

private const val MB = 1024L * 1024L
