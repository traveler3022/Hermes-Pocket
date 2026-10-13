package com.hermes.android.domain.messaging

/** What saving the bot did to the messaging gateway. */
sealed interface TelegramSaveResult {
    /** A running gateway picked the new token up by itself. */
    data object Live : TelegramSaveResult
    data object Restarting : TelegramSaveResult
    data class NeedsRestart(val reason: String?) : TelegramSaveResult
}

/**
 * Connects the user's own Telegram bot: the token they got from @BotFather and the numeric
 * user ids allowed to talk to it, written to the server's `.env` like the dashboard's Telegram card.
 */
interface TelegramSetup {
    /** False on the built-in Linux: it runs no messaging gateway for the bot to live in. */
    val available: Boolean

    suspend fun save(botToken: String, allowedUserIds: List<String>): TelegramSaveResult
}
