package com.hermes.android.domain.vault

/**
 * Hermes' credential vault, per profile: logins, cards and addresses the agent fills on web
 * pages without ever seeing the secret, plus password managers (Bitwarden, 1Password) it can
 * read from once unlocked. Secrets go in and never come back out: listings are metadata only.
 */
interface CredentialVault {

    /** Items of the local vault and of every unlocked password manager. */
    suspend fun items(): List<VaultItem>

    /** The local vault and each password manager Hermes knows, with its state. */
    suspend fun sources(): List<VaultSource>

    /** Turns a password manager on or off (off also locks it). */
    suspend fun setSourceEnabled(name: String, enabled: Boolean)

    /** Unlocks a password manager for this server session; the password is not stored. */
    suspend fun unlock(name: String, masterPassword: String)

    suspend fun lock(name: String)

    /** Saves an item in the local vault; [secret] uses the server's field names. Returns its id. */
    suspend fun add(kind: VaultKind, label: String, origin: String?, secret: Map<String, String>): String

    suspend fun remove(id: String)
}

enum class VaultKind(val wire: String) { LOGIN("login"), PAYMENT("payment"), ADDRESS("address") }

data class VaultItem(
    val id: String,
    /** "login", "payment", "address", or whatever a newer server sends. */
    val kind: String,
    val label: String,
    val origin: String?,
    val createdAt: String,
    /** Logins: the email / phone / username, visible metadata by design. */
    val identifier: String?,
    val hasOtp: Boolean,
    /** "local", or the password manager it comes from (read-only here). */
    val backend: String,
)

data class VaultSource(
    val name: String,
    val displayName: String,
    val enabled: Boolean,
    val needsUnlock: Boolean,
    val unlocked: Boolean,
    /** Its CLI is on the server; without it the switch can't do anything. */
    val installed: Boolean,
)
