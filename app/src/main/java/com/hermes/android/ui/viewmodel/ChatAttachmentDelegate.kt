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
import com.hermes.android.gateway.redactCredentials
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.LinuxFileAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

internal class ChatAttachmentDelegate(
    private val gatewayClient: GatewayClient,
    private val hermesRuntime: HermesRuntime,
    private val linuxFiles: LinuxFileAccess,
    private val context: Context,
    private val downloads: Downloads,
    private val scope: CoroutineScope,
) {
    private val maxAttachBytes = 25 * 1024 * 1024
    private val attachChunkSize = 1024 * 1024

    /** Batches still to attach; [ChatUiState.isAttaching] is true while any is. */
    private val attachBatches = AtomicInteger(0)
    /** One file at a time, in the order picked: Hermes queues images in that order. */
    private val attachLock = Mutex()

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

    private fun runBatch(state: MutableStateFlow<ChatUiState>, any: Boolean, uris: suspend () -> List<Uri>) {
        if (!any || state.value.activeSessionId == null) return
        attachBatches.incrementAndGet()
        state.update { it.copy(isAttaching = true) }
        scope.launch(Dispatchers.IO) {
            try {
                val list = uris()
                attachLock.withLock {
                    for (uri in list) attachOne(state, uri)
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
                        if (total > maxAttachBytes) throw IllegalStateException("File too large (max 25 MB)")
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

    private suspend fun attachOne(state: MutableStateFlow<ChatUiState>, uri: Uri) {
        val sessionId = state.value.activeSessionId ?: return
        var jpeg: java.io.File? = null
        try {
            val resolver = context.contentResolver
            val pickedName = displayName(uri)
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

            // Built-in Linux: hand Hermes a path on its own disk, as its desktop app does.
            // Termux and remote gateways can't see the file, so they get the bytes.
            val guestPath = if (GatewayClient.isStdio(hermesRuntime.getWebSocketUrl())) {
                linuxFiles.upload(source, name)
            } else null
            val b64 = if (guestPath == null) readBase64(source) else ""

            val attachment = if (isImage) {
                // image.attach takes only session_id and path, and rejects anything else
                // (tui_gateway/contracts/prompt_voice.py: ImageAttachParams); the filename
                // hint belongs to image.attach_bytes alone.
                val params = buildJsonObject {
                    put("session_id", sessionId)
                    if (guestPath != null) {
                        put("path", guestPath)
                    } else {
                        put("content_base64", b64)
                        put("filename", name)
                    }
                }
                val method = if (guestPath != null) "image.attach" else "image.attach_bytes"
                val result = gatewayClient.request(method, jsonToElementMap(params))
                val path = ((result as? JsonObject)?.get("path") as? JsonPrimitive)?.contentOrNull
                PendingAttachment(name = name, isImage = true, gatewayPath = path, localUri = uri.toString())
            } else {
                val params = buildJsonObject {
                    put("session_id", sessionId)
                    if (guestPath != null) put("path", guestPath) else put("data_url", "data:$mime;base64,$b64")
                    put("name", name)
                }
                val result = gatewayClient.request("file.attach", jsonToElementMap(params))
                val ref = ((result as? JsonObject)?.get("ref_text") as? JsonPrimitive)?.contentOrNull
                    ?: throw IllegalStateException("Gateway returned no file reference")
                PendingAttachment(name = name, isImage = false, refText = ref, localUri = uri.toString())
            }
            state.update { it.copy(pendingAttachments = it.pendingAttachments + attachment) }
            Timber.i("[Chat] Attached $name (${guestPath ?: "uploaded"})")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "[Chat] Attach failed")
            state.update { it.copy(errorEvent = ErrorEvent.Error("Attach failed: ${e.message}")) }
        } finally {
            jpeg?.delete()
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
                    if (totalSize > maxAttachBytes) {
                        throw IllegalStateException("File too large (max 25 MB)")
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
        state.update { it.copy(
            pendingAttachments = state.value.pendingAttachments - attachment,
        ) }
        val sessionId = state.value.activeSessionId ?: return
        if (attachment.isImage && attachment.gatewayPath != null) {
            scope.launch {
                try {
                    val params = buildJsonObject {
                        put("session_id", sessionId)
                        put("path", attachment.gatewayPath)
                    }
                    gatewayClient.request("image.detach", jsonToElementMap(params))
                } catch (e: Exception) {
                    Timber.w(e, "[Chat] image.detach failed (ignored)")
                }
            }
        }
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
