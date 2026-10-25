package com.hermes.android.domain.analytics

/**
 * Token and cost statistics of the Hermes server over a window of days: the totals, a cost per
 * day, what each model (and the provider that billed it) used, and the skills and tools the
 * agent reached for. Read from the same database the dashboard's Analytics page reads.
 */
interface UsageAnalytics {
    /** [days] 24-hour periods back from now, or [TODAY]. */
    suspend fun load(days: Int): Report

    companion object {
        /**
         * Today's calendar day (the server's). The totals are that day's; models, skills and tools
         * cover the last 24 hours, the shortest window the server takes; the chart is the last
         * [TODAY_CHART_DAYS] days so today has something to stand against.
         */
        const val TODAY = 0
        const val TODAY_CHART_DAYS = 7

        /** Windows longer than this chart one bar per month instead of per day. */
        const val MAX_DAILY_BARS = 90
    }

    data class Report(
        val days: Int,
        val totals: Totals,
        /**
         * One entry per calendar day of the window (per month past [MAX_DAILY_BARS] days), quiet
         * ones as zeros, ending with today.
         */
        val daily: List<Day>,
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

    /**
     * One local calendar day of the server ("2026-10-25"), or one month ("2026-10") on the
     * yearly chart. A session counts on the day it started.
     */
    data class Day(
        val day: String,
        val inputTokens: Long,
        val outputTokens: Long,
        val cacheReadTokens: Long,
        val reasoningTokens: Long,
        val sessions: Long,
        val apiCalls: Long,
        val estimatedCost: Double,
        val actualCost: Double,
    ) {
        val tokens: Long get() = inputTokens + outputTokens
        val cost: Double get() = if (actualCost > 0.0) actualCost else estimatedCost

        operator fun plus(o: Day) = Day(
            day, inputTokens + o.inputTokens, outputTokens + o.outputTokens, cacheReadTokens + o.cacheReadTokens,
            reasoningTokens + o.reasoningTokens, sessions + o.sessions, apiCalls + o.apiCalls,
            estimatedCost + o.estimatedCost, actualCost + o.actualCost,
        )
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
