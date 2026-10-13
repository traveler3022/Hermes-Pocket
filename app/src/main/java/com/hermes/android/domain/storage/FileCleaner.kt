package com.hermes.android.domain.storage

/** The files under the Storage chart, file by file, and deleting the ones the user picked. */
interface FileCleaner {

    suspend fun scan(): FileScan

    /** Deletes [paths] (from a [scan]); returns the bytes freed. */
    suspend fun delete(paths: Collection<String>, onProgress: (Float) -> Unit): Long
}

/** What a found file is, for the Media / Files / Music tabs of the Storage page. */
enum class FoundKind { Image, Video, Audio, Other }

/** The places whose files may go, and what the Folders tab calls a folder's root. */
enum class FileArea { Home, Temp, HermesCache }

data class FoundFile(
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val kind: FoundKind,
    /** The folder it sits in, inside the Linux (/root/Downloads). */
    val guestDir: String,
    /** Host path of the folder it is counted under. */
    val folder: String,
    /** In the file tabs; files under hidden folders or dependency trees only count in their folder. */
    val listed: Boolean,
)

data class FoundFolder(
    val path: String,
    /** The folder inside the Linux, e.g. /root/Downloads. */
    val guestPath: String,
    /** Its path below the area ("Downloads", "projects/app"); empty for the area itself. */
    val relative: String,
    val area: FileArea,
    val size: Long,
    val count: Int,
)

/** Telegram's CacheModel for this app: folders instead of chats, then the files by kind. */
data class FileScan(
    val folders: List<FoundFolder>,
    val media: List<FoundFile>,
    val documents: List<FoundFile>,
    val music: List<FoundFile>,
    /** Every file of each folder (listed or not), for deleting a whole folder. */
    val filesByFolder: Map<String, List<FoundFile>>,
)
