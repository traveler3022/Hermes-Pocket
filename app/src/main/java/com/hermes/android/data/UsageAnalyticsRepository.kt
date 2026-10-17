package com.hermes.android.data

import com.hermes.android.domain.analytics.UsageAnalytics
import com.hermes.android.domain.analytics.UsageAnalytics.AuxiliaryUse
import com.hermes.android.domain.analytics.UsageAnalytics.Counted
import com.hermes.android.domain.analytics.UsageAnalytics.Day
import com.hermes.android.domain.analytics.UsageAnalytics.ModelUse
import com.hermes.android.domain.analytics.UsageAnalytics.Report
import com.hermes.android.domain.analytics.UsageAnalytics.Totals
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `/api/analytics/usage` and `/api/analytics/models` of the dashboard (web_routers/analytics.py).
 * The first holds totals, days, skills and tools; only the second names the provider of each model.
 */
@Singleton
class UsageAnalyticsRepository @Inject constructor(
    private val dashboard: DashboardApi,
) : UsageAnalytics {

    override suspend fun load(days: Int): Report {
        val usage = dashboard.call("GET", "/api/analytics/usage?days=$days") as? JsonObject
            ?: throw IOException("No answer from Hermes")
        // An older server may lack the per-provider route; the page then lists models without it.
        val models = try {
            (dashboard.call("GET", "/api/analytics/models?days=$days") as? JsonObject)?.get("models") as? JsonArray
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[Analytics] models route failed; falling back to the usage route")
            null
        }
        val totals = usage.obj("totals")
        return Report(
            days = days,
            totals = Totals(
                sessions = totals.long("total_sessions"),
                apiCalls = totals.long("total_api_calls"),
                inputTokens = totals.long("total_input"),
                outputTokens = totals.long("total_output"),
                cacheReadTokens = totals.long("total_cache_read"),
                reasoningTokens = totals.long("total_reasoning"),
                estimatedCost = totals.double("total_estimated_cost"),
                actualCost = totals.double("total_actual_cost"),
            ),
            daily = usage.list("daily").mapNotNull { d ->
                val day = d.str("day") ?: return@mapNotNull null
                Day(
                    day = day,
                    tokens = d.long("input_tokens") + d.long("output_tokens"),
                    cost = d.costOf("actual_cost", "estimated_cost"),
                )
            },
            models = (models ?: usage.list("by_model")).objects().mapNotNull { m ->
                val model = m.str("model") ?: return@mapNotNull null
                ModelUse(
                    model = model,
                    provider = m.str("provider") ?: m.str("billing_provider").orEmpty(),
                    inputTokens = m.long("input_tokens"),
                    outputTokens = m.long("output_tokens"),
                    sessions = m.long("sessions"),
                    apiCalls = m.long("api_calls"),
                    cost = m.costOf("actual_cost", "estimated_cost"),
                )
            },
            auxiliary = usage.list("by_task").mapNotNull { t ->
                val task = t.str("task") ?: return@mapNotNull null
                AuxiliaryUse(
                    task = task,
                    models = (t["models"] as? JsonArray).orEmpty()
                        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) },
                    tokens = t.long("input_tokens") + t.long("output_tokens"),
                    cost = t.double("estimated_cost"),
                )
            },
            skills = usage.obj("skills").list("top_skills").mapNotNull { s ->
                Counted(s.str("skill") ?: return@mapNotNull null, s.long("total_count"))
            },
            tools = usage.list("tools").mapNotNull { t ->
                Counted(t.str("tool") ?: return@mapNotNull null, t.long("count"))
            },
        )
    }

    private fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject

    private fun JsonObject?.list(key: String): List<JsonObject> = (this?.get(key) as? JsonArray).objects()

    private fun JsonArray?.objects(): List<JsonObject> = this.orEmpty().mapNotNull { it as? JsonObject }

    private fun JsonObject?.str(key: String): String? =
        (this?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject?.long(key: String): Long =
        (this?.get(key) as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() } ?: 0L

    private fun JsonObject?.double(key: String): Double = (this?.get(key) as? JsonPrimitive)?.doubleOrNull ?: 0.0

    /** The billed amount when the provider reported one, else the estimate. */
    private fun JsonObject?.costOf(actual: String, estimate: String): Double =
        double(actual).takeIf { it > 0.0 } ?: double(estimate)
}
