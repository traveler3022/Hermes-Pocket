package com.hermes.android.ui.viewmodel

/** A starred model, pinned to the top of the chat's model sheet. */
data class ModelFavorite(val provider: String, val modelId: String)

/**
 * The ordered, capped starred-models list and its SharedPreferences encoding.
 * Plain Kotlin (no Android types) so it can be checked without the SDK.
 */
object ModelFavorites {
    const val MAX = 5

    // Model ids contain "/" and ":", so entries are tab-separated and newline-joined.
    fun decode(raw: String?): List<ModelFavorite> =
        raw.orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size == 2 && parts.all { it.isNotBlank() }) ModelFavorite(parts[0], parts[1]) else null
        }.distinct().take(MAX).toList()

    fun encode(favorites: List<ModelFavorite>): String =
        favorites.joinToString("\n") { "${it.provider}\t${it.modelId}" }

    /** Unstar when present; star (appended) when absent and under [MAX]; otherwise unchanged. */
    fun toggle(favorites: List<ModelFavorite>, favorite: ModelFavorite): List<ModelFavorite> = when {
        favorite in favorites -> favorites - favorite
        favorites.size < MAX -> favorites + favorite
        else -> favorites
    }
}
