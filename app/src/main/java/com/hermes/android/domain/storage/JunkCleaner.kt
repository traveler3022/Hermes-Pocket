package com.hermes.android.domain.storage

/** The app's own leftovers for the Storage page: what each kind takes, and clearing chosen kinds. */
interface JunkCleaner {

    suspend fun scan(): StorageScan

    /** Clears [kinds]; [onProgress] gets 0..1 as sources finish. Returns the bytes freed. */
    suspend fun clear(kinds: Set<JunkKind>, onProgress: (Float) -> Unit): Long
}

/**
 * The kinds of leftovers the Storage screen shows, in the chart's colour order (Telegram's
 * CacheControlActivity sections). Each is something that is made again on its own when needed.
 */
enum class JunkKind {
    Photos, Videos, Documents, Audio, Temp, Browser, Packages, AppCache, Other, Logs,
}

/** One pass over the app's storage: what each kind of leftover takes, and the phone around it. */
data class StorageScan(
    val sizes: Map<JunkKind, Long>,
    /** Everything the app keeps (code, data, cache, the built-in Linux); -1 when Android won't say. */
    val appBytes: Long,
    val deviceTotal: Long,
    val deviceFree: Long,
) {
    val junkBytes: Long get() = sizes.values.sum()
}
