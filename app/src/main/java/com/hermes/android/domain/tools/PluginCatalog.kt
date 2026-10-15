package com.hermes.android.domain.tools

/** One curated plugin of the Hermes plugin catalog, with whether this server has it. */
data class CatalogPlugin(
    val name: String,
    val title: String,
    val description: String,
    /** official or community. */
    val tier: String,
    val category: String,
    val installed: Boolean,
    val updateAvailable: Boolean,
)

/** The curated plugin catalog (dashboard `/api/dashboard/plugins/catalog`); the gateway has no RPC for it. */
interface PluginCatalog {
    suspend fun entries(): List<CatalogPlugin>
}
