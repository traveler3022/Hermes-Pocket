package com.hermes.android.domain.files

import android.graphics.Bitmap
import java.io.File

/**
 * The phone's Download/Hermes folder: where the Download buttons put a file, and what the
 * Media screen lists. Only a tap on Download writes there; Hermes itself can't reach it.
 */
interface Downloads {

    /**
     * Saves the file at [url] under [name]; anything not already on this disk is fetched with
     * [fetch] first. Returns where it went, for the user.
     */
    suspend fun save(url: String, name: String, fetch: suspend (String) -> File): String

    /** Files this app saved there, newest first. */
    suspend fun list(): List<SavedFile>

    /** A small preview of an image or video [SavedFile.uri], or null. */
    suspend fun thumbnail(uri: String, sizePx: Int): Bitmap?
}

/** A file in the phone's Download/Hermes. [uri] is its content:// URI. */
data class SavedFile(
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val addedAtMillis: Long,
) {
    val kind: SavedFileKind
        get() = when {
            mimeType.startsWith("image/") -> SavedFileKind.Photo
            mimeType.startsWith("video/") -> SavedFileKind.Video
            mimeType.startsWith("audio/") || mimeType == "application/ogg" -> SavedFileKind.Audio
            else -> SavedFileKind.File
        }
}

enum class SavedFileKind { Photo, Video, Audio, File }
