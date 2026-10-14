package com.hermes.android.data

import com.hermes.android.domain.tools.CatalogPlugin
import com.hermes.android.domain.tools.PluginCatalog
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GET `/api/dashboard/plugins/catalog`. It needs the dashboard's own session, which the built-in
 * Linux's in-process dashboard doesn't have, so there it fails and the screen says the catalog
 * isn't available; installing by URL still works over the gateway.
 */
@Singleton
class PluginCatalogRepository @Inject constructor(
    private val dashboard: DashboardApi,
) : PluginCatalog {

    override suspend fun entries(): List<CatalogPlugin> {
        val root = dashboard.call("GET", "/api/dashboard/plugins/catalog") as? JsonObject ?: return emptyList()
        return (root["entries"] as? JsonArray).orEmpty().mapNotNull { row ->
            val o = row as? JsonObject ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            CatalogPlugin(
                name = name,
                title = o.str("title") ?: name,
                description = o.str("description").orEmpty(),
                tier = o.str("tier") ?: "community",
                category = o.str("category").orEmpty(),
                installed = o.bool("installed"),
                updateAvailable = o.bool("update_available"),
            )
        }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
}
