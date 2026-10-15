package com.hermes.android.domain.tools

/** A skill's on/off state and where it came from (hub, bundled, external, agent), per the dashboard. */
data class SkillState(val name: String, val category: String?, val enabled: Boolean, val provenance: String?)

/** The hub's security scan of a skill before it is installed. */
data class SkillScan(
    val verdict: String?,
    val summary: String?,
    /** allow, ask or block: what the server's install policy says for this skill. */
    val policy: String?,
    val policyReason: String?,
    val findings: List<String>,
)

/** The dashboard's skill endpoints the gateway has no RPC for: on/off, hub uninstall, update, scan. */
interface SkillsAdmin {
    suspend fun states(): List<SkillState>
    suspend fun setEnabled(name: String, enabled: Boolean)
    /** Starts `hermes skills uninstall` on the server; it finishes in the background. */
    suspend fun uninstall(name: String)
    /** Starts `hermes skills update` for every hub skill; it finishes in the background. */
    suspend fun updateAll()
    suspend fun scan(identifier: String): SkillScan
}
