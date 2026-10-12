package com.hermes.android.domain.session

/** A LIVE session's context window: what fills it, and a manual compress. */
interface SessionContext {

    /** What is filling this LIVE session's context window, largest first. */
    suspend fun contextBreakdown(liveSessionId: String): ContextBreakdown

    suspend fun compressSession(liveSessionId: String, focusTopic: String = ""): CompressionResult
}

data class ContextCategory(val id: String, val label: String, val tokens: Int)

data class ContextBreakdown(
    val categories: List<ContextCategory>,
    val used: Int,
    val max: Int,
    val percent: Int,
    val model: String,
)

/**
 * Outcome of a manual compress. The server answers in several shapes: a
 * finished local run, an "still running" ack from a compute host, and a
 * skip when another compressor holds the lock.
 */
data class CompressionResult(
    val status: String,
    val beforeTokens: Int,
    val afterTokens: Int,
    val removedMessages: Int,
    val message: String,
)
