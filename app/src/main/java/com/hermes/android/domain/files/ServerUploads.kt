package com.hermes.android.domain.files

import java.io.InputStream

/**
 * Puts a file from this phone on the Hermes server's disk, streamed over HTTP
 * (`/api/files/upload-stream`), so a chat attachment goes by path instead of as base64
 * inside one WebSocket frame, which OkHttp caps at 16 MiB.
 */
interface ServerUploads {

    /** False on the built-in Linux: it has no web server, and its files go by path anyway. */
    val available: Boolean

    /**
     * Streams [open]'s bytes ([size] of them, or -1 when unknown) to a new file named after
     * [name] on the server, calling [onProgress] with the bytes sent so far and the total.
     * Returns the file's absolute path on the server. Throws IOException when the server
     * refuses or the link breaks; cancelling the caller stops the upload.
     */
    suspend fun upload(
        name: String,
        size: Long,
        open: () -> InputStream,
        onProgress: (sent: Long, total: Long) -> Unit,
    ): String
}
