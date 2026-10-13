package com.hermes.android.domain.messaging

/**
 * Hermes' DM pairing: someone not on a bot's allowed list who messages it gets a one-time
 * code and waits in "pending" until the owner approves them. Approved users can talk to the
 * bot until revoked. Each profile has its own list.
 */
interface MessagingPairing {
    /** False on the built-in Linux: it runs no messaging gateway, so nobody can ask to pair. */
    val available: Boolean

    suspend fun list(): PairingList

    /** Approves a pending request by the id from [list]. */
    suspend fun approve(platform: String, requestId: String): PairingResult

    suspend fun revoke(platform: String, userId: String): PairingResult

    suspend fun clearPending(): PairingResult

    data class PairingList(
        val pending: List<Request>,
        val approved: List<User>,
    )

    data class Request(
        val platform: String,
        /** Null for an entry from an older Hermes: it can only be approved with its code. */
        val requestId: String?,
        val userId: String,
        val userName: String,
        val ageMinutes: Int?,
    )

    data class User(
        val platform: String,
        val userId: String,
        val userName: String,
    )
}

sealed interface PairingResult {
    data object Done : PairingResult
    /** [message] is the CLI's own explanation (expired request, lockout, unknown user). */
    data class Failed(val message: String) : PairingResult
}
