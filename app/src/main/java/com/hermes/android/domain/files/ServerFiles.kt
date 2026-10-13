package com.hermes.android.domain.files

import java.io.File

/** Files on the Hermes server, read through its sign-in and kept in a disk cache. */
interface ServerFiles {

    /** Whether [url] points at a file on the Hermes server. */
    fun isServerFile(url: String): Boolean

    /** The server file at [url] as a file on this disk, fetched when not cached yet. */
    suspend fun fetch(url: String): File
}
