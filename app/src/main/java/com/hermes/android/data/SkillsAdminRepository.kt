package com.hermes.android.data

import com.hermes.android.domain.tools.SkillScan
import com.hermes.android.domain.tools.SkillState
import com.hermes.android.domain.tools.SkillsAdmin
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** `/api/skills`, `/api/skills/toggle` and `/api/skills/hub/{uninstall,update,scan}` (web_routers/skills.py). */
@Singleton
class SkillsAdminRepository @Inject constructor(
    private val dashboard: DashboardApi,
) : SkillsAdmin {

    override suspend fun states(): List<SkillState> =
        (dashboard.call("GET", "/api/skills") as? JsonArray).orEmpty().mapNotNull { row ->
            val o = row as? JsonObject ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            SkillState(
                name = name,
                category = o.str("category"),
                enabled = (o["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true,
                provenance = o.str("provenance"),
            )
        }

    override suspend fun setEnabled(name: String, enabled: Boolean) {
        dashboard.call("PUT", "/api/skills/toggle", buildJsonObject {
            put("name", name)
            put("enabled", enabled)
        })
    }

    override suspend fun uninstall(name: String) {
        dashboard.call("POST", "/api/skills/hub/uninstall", buildJsonObject { put("name", name) })
    }

    override suspend fun updateAll() {
        dashboard.call("POST", "/api/skills/hub/update", buildJsonObject {})
    }

    override suspend fun scan(identifier: String): SkillScan {
        val o = dashboard.call(
            "GET",
            "/api/skills/hub/scan?identifier=" + URLEncoder.encode(identifier, "UTF-8"),
        ) as? JsonObject ?: JsonObject(emptyMap())
        val findings = (o["findings"] as? JsonArray).orEmpty().mapNotNull { f ->
            val row = f as? JsonObject ?: return@mapNotNull null
            listOfNotNull(row.str("severity")?.uppercase(), row.str("description") ?: row.str("message"), row.str("file"))
                .joinToString(" · ").ifBlank { null }
        }
        return SkillScan(
            verdict = o.str("verdict"),
            summary = o.str("summary"),
            policy = o.str("policy"),
            policyReason = o.str("policy_reason"),
            findings = findings,
        )
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
