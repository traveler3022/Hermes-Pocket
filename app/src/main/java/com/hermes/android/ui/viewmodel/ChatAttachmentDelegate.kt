package com.hermes.android.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import com.hermes.android.runtime.HermesRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

internal class ChatAttachmentDelegate(
    private val gatewayClient: GatewayClient,
    private val hermesRuntime: HermesRuntime,
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val maxAttachBytes = 25 * 1024 * 1024
    private val attachChunkSize = 1024 * 1024

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

                val b64 = StringBuilder()
                var totalSize = 0
                resolver.openInputStream(uri)?.use { stream ->
                    val buffer = ByteArray(attachChunkSize)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        totalSize += read
                        if (totalSize > maxAttachBytes) {
                            throw IllegalStateException("File too large (max 25 MB)")
                        }
                        val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                        b64.append(Base64.encodeToString(chunk, Base64.NO_WRAP))
                    }
                } ?: throw IllegalStateException("Cannot read file")

                if (totalSize == 0) {
                    throw IllegalStateException("File is empty")
                }

                val newAttachments: List<PendingAttachment> = if (mime == "application/pdf") {
                    val params = buildJsonObject {
                        put("session_id", sessionId)
                        put("content_base64", b64.toString())
                        put("filename", name)
                    }
                    val result = gatewayClient.request(GatewayMethods.PDF_ATTACH, jsonToElementMap(params))
                        as? JsonObject ?: throw IllegalStateException("Gateway returned no result")
                    val pages = result["pages"] as? JsonArray
                        ?: throw IllegalStateException("PDF attach returned no pages")
                    pages.mapIndexedNotNull { idx, pageEl ->
                        val page = pageEl as? JsonObject ?: return@mapIndexedNotNull null
                        val path = (page["path"] as? JsonPrimitive)?.content
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
                        put("content_base64", b64.toString())
                        put("filename", name)
                    }
                    val result = gatewayClient.request("image.attach_bytes", jsonToElementMap(params))
                    val path = ((result as? JsonObject)?.get("path") as? JsonPrimitive)?.content
                    listOf(PendingAttachment(name = name, isImage = true, gatewayPath = path, localUri = uri.toString()))
                } else {
                    val params = buildJsonObject {
                        put("session_id", sessionId)
                        put("data_url", "data:$mime;base64,${b64}")
                        put("name", name)
                    }
                    val result = gatewayClient.request("file.attach", jsonToElementMap(params))
                    val ref = ((result as? JsonObject)?.get("ref_text") as? JsonPrimitive)?.content
                        ?: throw IllegalStateException("Gateway returned no file reference")
                    listOf(PendingAttachment(name = name, isImage = false, refText = ref, localUri = uri.toString()))
                }
                state.update { it.copy(
                    pendingAttachments = state.value.pendingAttachments + newAttachments,
                    isAttaching = false,
                ) }
                Timber.i("[Chat] Attached ${newAttachments.size} item(s) from $name (size=${totalSize})")
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Attach failed")
                state.update { it.copy(
                    errorEvent = ErrorEvent.Error("Attach failed: ${e.message}"),
                    isAttaching = false,
                ) }
            }
        }
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
        val base = ws.replaceFirst("ws://", "http://").replaceFirst("wss://", "https://")
            .substringBefore("/api/ws")
        val token = ws.substringAfter("token=", "").substringBefore('&')
        val encoded = java.net.URLEncoder.encode(path, "UTF-8")
        return buildString {
            append(base).append("/api/files/download?path=").append(encoded)
            if (token.isNotEmpty()) append("&token=").append(token)
        }
    }

    fun downloadFile(state: MutableStateFlow<ChatUiState>, url: String, filename: String) {
        scope.launch {
            val derivedName = filename.ifBlank {
                url.substringAfterLast('/').substringBefore('?')
            }
            val safeName = derivedName.ifBlank { "hermes_file" }
                .let { name -> name.filter { it != '/' && it != '\\' } }
                .ifBlank { "hermes_file" }
                .let { if (!it.contains('.')) "$it.jpg" else it }
            try {
                val bytes = gatewayClient.downloadFile(url)
                val resolver = context.contentResolver
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/Hermes")
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                }
                val itemUri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create download entry")
                resolver.openOutputStream(itemUri)?.use { out -> out.write(bytes) }
                    ?: error("Could not open output stream")
                values.clear()
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
                Timber.i("[Chat] Downloaded $safeName (${bytes.size} bytes) -> Downloads/Hermes")
                state.update { it.copy(errorEvent = ErrorEvent.Warning("Saved to Downloads/Hermes: $safeName")) }
            } catch (e: Exception) {
                Timber.e(e, "[Chat] Download failed: $safeName")
                state.update { it.copy(errorEvent = ErrorEvent.Error("Download failed: ${e.message}")) }
            }
        }
    }

    private fun jsonToElementMap(obj: JsonObject): Map<String, kotlinx.serialization.json.JsonElement> = obj.toMap()
}
