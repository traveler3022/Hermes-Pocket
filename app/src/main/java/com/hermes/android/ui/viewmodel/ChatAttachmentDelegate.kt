package com.hermes.android.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.hermes.android.data.DownloadStorage
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import com.hermes.android.gateway.StdioGatewayHub
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.linux.LinuxUploads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import kotlinx.serialization.json.contentOrNull

internal class ChatAttachmentDelegate(
    private val gatewayClient: GatewayClient,
    private val hermesRuntime: HermesRuntime,
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val maxAttachBytes = 25 * 1024 * 1024
    private val attachChunkSize = 1024 * 1024
    private val linuxUploads by lazy { LinuxUploads(context) }

    fun attachFromUri(state: MutableStateFlow<ChatUiState>, uri: Uri) {
        val sessionId = state.value.activeSessionId ?: return
        if (state.value.isAttaching) return
        state.update { it.copy(isAttaching = true) }
        scope.launch(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val name = resolver.query(uri, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0 && c.moveToFirst()) c.getString(i) else null
                } ?: uri.lastPathSegment ?: "attachment"
                val mime = resolver.getType(uri) ?: "application/octet-stream"

                // Built-in Linux: hand Hermes a path on its own disk, as its desktop app does.
                // Termux and remote gateways can't see the file, so they get the bytes.
                val guestPath = if (StdioGatewayHub.handles(hermesRuntime.getWebSocketUrl())) {
                    linuxUploads.guestPath(uri, name)
                } else null
                val b64 = if (guestPath == null) readBase64(uri) else ""

                val newAttachments: List<PendingAttachment> = if (mime == "application/pdf") {
                    val params = buildJsonObject {
                        put("session_id", sessionId)
                        if (guestPath != null) put("path", guestPath) else put("content_base64", b64)
                        put("filename", name)
                    }
                    val result = gatewayClient.request(GatewayMethods.PDF_ATTACH, jsonToElementMap(params))
                        as? JsonObject ?: throw IllegalStateException("Gateway returned no result")
                    val pages = result["pages"] as? JsonArray
                        ?: throw IllegalStateException("PDF attach returned no pages")
                    pages.mapIndexedNotNull { idx, pageEl ->
                        val page = pageEl as? JsonObject ?: return@mapIndexedNotNull null
                        val path = (page["path"] as? JsonPrimitive)?.contentOrNull
                        PendingAttachment(
                            name = "$name (p.${idx + 1})",
                            isImage = true,
                            gatewayPath = path,
                            localUri = uri.toString(),
                        )
                    }
                } else if (mime.startsWith("image/")) {
                    val params = buildJsonObject {
                        put("session_id", sessionId)
                        if (guestPath != null) put("path", guestPath) else put("content_base64", b64)
                        put("filename", name)
                    }
                    val method = if (guestPath != null) "image.attach" else "image.attach_bytes"
                    val result = gatewayClient.request(method, jsonToElementMap(params))
                    val path = ((result as? JsonObject)?.get("path") as? JsonPrimitive)?.contentOrNull
                    listOf(PendingAttachment(name = name, isImage = true, gatewayPath = path, localUri = uri.toString()))
                } else {
                    val params = buildJsonObject {
                        put("session_id", sessionId)
                        if (guestPath != null) put("path", guestPath) else put("data_url", "data:$mime;base64,$b64")
                        put("name", name)
                    }
                    val result = gatewayClient.request("file.attach", jsonToElementMap(params))
                    val ref = ((result as? JsonObject)?.get("ref_text") as? JsonPrimitive)?.contentOrNull
                        ?: throw IllegalStateException("Gateway returned no file reference")
                    listOf(PendingAttachment(name = name, isImage = false, refText = ref, localUri = uri.toString()))
                }
                state.update { it.copy(
                    pendingAttachments = state.value.pendingAttachments + newAttachments,
                    isAttaching = false,
                ) }
                Timber.i("[Chat] Attached ${newAttachments.size} item(s) from $name (${guestPath ?: "uploaded"})")
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Attach failed")
                state.update { it.copy(
                    errorEvent = ErrorEvent.Error("Attach failed: ${e.message}"),
                    isAttaching = false,
                ) }
            }
        }
    }

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
        if (StdioGatewayHub.handles(ws)) {
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
                url.startsWith("http") -> gatewayClient.downloadFile(url)
                else -> return@withContext null
            }
            if (bytes.size > maxPreviewBytes || bytes.contains(0.toByte())) null else bytes.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.w(e, "[Chat] Preview file unreadable: $path")
            null
        }
    }

    private val maxPreviewBytes = 5 * 1024 * 1024

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

    private val downloadStorage by lazy { DownloadStorage(context) }

    fun downloadFile(state: MutableStateFlow<ChatUiState>, url: String, filename: String) {
        scope.launch {
            try {
                val savedAs = downloadStorage.save(url, filename) { gatewayClient.downloadFile(it) }
                Timber.i("[Chat] Downloaded $url -> $savedAs")
                state.update { it.copy(errorEvent = ErrorEvent.Warning("Saved to $savedAs")) }
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Download failed: $url")
                state.update { it.copy(errorEvent = ErrorEvent.Error("Download failed: ${e.message}")) }
            }
        }
    }

    private fun jsonToElementMap(obj: JsonObject): Map<String, kotlinx.serialization.json.JsonElement> = obj.toMap()
}
