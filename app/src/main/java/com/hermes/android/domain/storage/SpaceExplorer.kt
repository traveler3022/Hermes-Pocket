package com.hermes.android.domain.storage

/**
 * Everything the built-in Linux holds, sorted into places the user can tell apart, so a big
 * folder is never a mystery: what it is, how much it takes, and whether it may go.
 */
interface SpaceExplorer {

    suspend fun scan(): SpaceOverview

    /** What [guestDir] holds, biggest first; each size counts only what belongs to that entry's place. */
    suspend fun list(guestDir: String): List<SpaceItem>

    /** Deletes [guestPaths]; anything Hermes needs is refused whatever asked for it. Returns the bytes freed. */
    suspend fun delete(guestPaths: Collection<String>, onProgress: (Float) -> Unit): Long
}

enum class SpaceVerdict { Safe, Careful, Needed }

/** The places of the built-in Linux, in the order the page lists them. */
enum class SpacePlace(val verdict: SpaceVerdict) {
    YourFiles(SpaceVerdict.Safe),
    Uploads(SpaceVerdict.Safe),
    HermesMade(SpaceVerdict.Safe),
    Caches(SpaceVerdict.Safe),
    Temp(SpaceVerdict.Safe),
    Installed(SpaceVerdict.Careful),
    Backups(SpaceVerdict.Careful),
    Hermes(SpaceVerdict.Needed),
    HermesData(SpaceVerdict.Needed),
    System(SpaceVerdict.Needed),
}

data class SpaceItem(
    /** Its path inside the Linux, e.g. /root/.cache/huggingface. */
    val guestPath: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val place: SpacePlace,
    /** False for what Hermes needs, and for a folder with something Hermes needs inside. */
    val deletable: Boolean,
)

data class SpaceOverview(
    /** Everything in the built-in Linux. */
    val total: Long,
    val sizes: Map<SpacePlace, Long>,
    /** The top folders and files of each place, biggest first. */
    val roots: Map<SpacePlace, List<SpaceItem>>,
) {
    fun size(verdict: SpaceVerdict): Long = sizes.filterKeys { it.verdict == verdict }.values.sum()
}
