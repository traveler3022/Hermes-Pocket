package com.hermes.android.domain.messaging

/** A started pairing: open [deepLink] in Telegram to create the bot. */
data class TelegramPairing(
    val pairingId: String,
    val suggestedUsername: String,
    val deepLink: String,
    // ISO time, UTC; empty when the server sent none.
    val expiresAt: String,
)

sealed interface TelegramPairingStatus {
    data object Waiting : TelegramPairingStatus
    /** The bot exists; [ownerUserId] is the Telegram user who made it, when known. */
    data class Ready(val botUsername: String?, val ownerUserId: String?) : TelegramPairingStatus
}

/** What saving the bot did to the messaging gateway. */
sealed interface TelegramApplyResult {
    data object Done : TelegramApplyResult
    data object Restarting : TelegramApplyResult
    data class NeedsRestart(val reason: String?) : TelegramApplyResult
}

/** The dashboard's Telegram wizard: make a bot from Telegram, then allow users and save it. */
interface TelegramSetup {
    /** False on the built-in Linux: it runs no messaging gateway for the bot to live in. */
    val available: Boolean

    suspend fun start(): TelegramPairing

    suspend fun status(pairingId: String): TelegramPairingStatus

    suspend fun apply(pairingId: String, allowedUserIds: List<String>): TelegramApplyResult

    suspend fun cancel(pairingId: String)
}
