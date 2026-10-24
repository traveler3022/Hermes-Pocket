package com.hermes.android.data

import com.hermes.android.domain.files.ServerUploads
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chat attachments onto the server's disk through the dashboard's streamed upload, under
 * `~/.hermes/uploads/`. The chat then attaches them by path, as for a file already there.
 * Nothing here deletes them afterwards.
 */
@Singleton
class ChatUploadRepository @Inject constructor(
    private val dashboard: DashboardApi,
) : ServerUploads {

    override val available: Boolean get() = !dashboard.inProcess

    override suspend fun upload(
        name: String,
        size: Long,
        open: () -> InputStream,
        onProgress: (sent: Long, total: Long) -> Unit,
    ): String {
        // Hermes reads an attach path as drop syntax, where a space would split it: keep the
        // name to plain characters, and a short random folder so names never collide.
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.').takeLast(100).ifEmpty { "file" }
        val serverPath = "$UPLOAD_DIR/${UUID.randomUUID().toString().take(8)}-$safe"
        val answer = dashboard.uploadStream(serverPath, safe, size, open, onProgress) as? JsonObject
        // The server's own absolute path for it (`~` expanded).
        return (answer?.get("path") as? JsonPrimitive)?.contentOrNull?.ifEmpty { null }
            ?: throw IOException("The server stored no file")
    }

    private companion object {
        const val UPLOAD_DIR = "~/.hermes/uploads"
    }
}
