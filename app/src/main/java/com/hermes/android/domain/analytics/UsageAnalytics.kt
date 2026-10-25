package com.hermes.android.domain.analytics

/**
 * Token and cost statistics of the Hermes server over a window of days: the totals, a cost per
 * day, what each model (and the provider that billed it) used, and the skills and tools the
 * agent reached for. Read from the same database the dashboard's Analytics page reads.
 */
interface UsageAnalytics {
    suspend fun load(days: Int): Report

    data class Report(
        val days: Int,
        val totals: Totals,
        /** One entry per calendar day of the window, quiet days as zeros, ending with today. */
        val daily: List<Day>,
        /** Sessions started today (by the server's calendar); zeros when there were none. */
        val today: Day,
        /** The day before [today], for comparison; null when the window has no such day. */
        val yesterday: Day?,
        val models: List<ModelUse>,
        /** Side jobs such as compression and vision, which the main counters leave out. */
        val auxiliary: List<AuxiliaryUse>,
        val skills: List<Counted>,
        val tools: List<Counted>,
    )

    data class Totals(
        val sessions: Long,
        val apiCalls: Long,
        val inputTokens: Long,
        val outputTokens: Long,
        val cacheReadTokens: Long,
        val reasoningTokens: Long,
        val estimatedCost: Double,
        /** What the provider billed, when it reports that; 0 when it doesn't. */
        val actualCost: Double,
    ) {
        /** The billed amount when there is one, else the estimate from the price table. */
        val cost: Double get() = if (actualCost > 0.0) actualCost else estimatedCost
        val costIsEstimate: Boolean get() = actualCost <= 0.0
    }

    /** One local calendar day of the server (a session counts on the day it started). */
    data class Day(
        val day: String,
        val inputTokens: Long,
        val outputTokens: Long,
        val cacheReadTokens: Long,
        val sessions: Long,
        val apiCalls: Long,
        val cost: Double,
    ) {
        val tokens: Long get() = inputTokens + outputTokens
    }

    data class ModelUse(
        val model: String,
        /** The billing provider; empty when the server didn't record one. */
        val provider: String,
        val inputTokens: Long,
        val outputTokens: Long,
        val sessions: Long,
        val apiCalls: Long,
        val cost: Double,
    ) {
        val tokens: Long get() = inputTokens + outputTokens
    }

    data class AuxiliaryUse(val task: String, val models: List<String>, val tokens: Long, val cost: Double)

    data class Counted(val name: String, val count: Long)
}
