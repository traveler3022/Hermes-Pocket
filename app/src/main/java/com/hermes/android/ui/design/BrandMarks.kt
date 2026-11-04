package com.hermes.android.ui.design

import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.hermes.android.R

/**
 * A brand's mark for the settings pages (Providers, MCP), from Simple Icons: res/drawable/brand_*.xml,
 * written by scripts/brand-icons.py. [color] null is a black or white mark that follows the theme, as
 * the desktop's `monochrome`. Some brands are missing on purpose: Simple Icons removed OpenAI's mark at
 * its request and has none for xAI or Nous, so those rows keep their plain icon.
 */
data class BrandMark(@DrawableRes val icon: Int, val color: Color?)

object BrandMarks {
    // Hermes provider slugs (hermes_cli/providers.py) and sign-in account ids; colors are Simple Icons'.
    private val providers: List<Pair<String, BrandMark>> = listOf(
        "anthropic" to BrandMark(R.drawable.brand_anthropic, null),
        "gemini" to BrandMark(R.drawable.brand_googlegemini, Color(0xFF8E75B2)),
        "vertex" to BrandMark(R.drawable.brand_googlecloud, Color(0xFF4285F4)),
        "google" to BrandMark(R.drawable.brand_googlegemini, Color(0xFF8E75B2)),
        "openrouter" to BrandMark(R.drawable.brand_openrouter, Color(0xFF94A3B8)),
        "mistral" to BrandMark(R.drawable.brand_mistralai, Color(0xFFFA520F)),
        "ollama" to BrandMark(R.drawable.brand_ollama, null),
        "huggingface" to BrandMark(R.drawable.brand_huggingface, Color(0xFFFFD21E)),
        "perplexity" to BrandMark(R.drawable.brand_perplexity, Color(0xFF1FB8CD)),
        "deepseek" to BrandMark(R.drawable.brand_deepseek, Color(0xFF5786FE)),
        "nvidia" to BrandMark(R.drawable.brand_nvidia, Color(0xFF76B900)),
        "alibaba" to BrandMark(R.drawable.brand_alibabacloud, Color(0xFFFF6A00)),
        "qwen" to BrandMark(R.drawable.brand_qwen, Color(0xFF6950EF)),
        "kimi" to BrandMark(R.drawable.brand_kimi, null),
        "minimax" to BrandMark(R.drawable.brand_minimax, Color(0xFFE73562)),
        "copilot" to BrandMark(R.drawable.brand_githubcopilot, null),
        "lmstudio" to BrandMark(R.drawable.brand_lmstudio, null),
        "vercel" to BrandMark(R.drawable.brand_vercel, null),
        "xiaomi" to BrandMark(R.drawable.brand_xiaomi, Color(0xFFFF6900)),
        "opencode" to BrandMark(R.drawable.brand_opencode, null),
        "cloudflare" to BrandMark(R.drawable.brand_cloudflare, Color(0xFFF38020)),
        "meta" to BrandMark(R.drawable.brand_meta, Color(0xFF0467DF)),
    )

    // Hermes Desktop's MCP_BRAND_ICONS (apps/desktop/src/lib/mcp-brands.tsx), same keys and colors.
    // Its Slack glyph comes from Tabler, not Simple Icons, so Slack keeps the plain icon here.
    private val mcpServers: List<Pair<String, BrandMark>> = listOf(
        "airtable" to BrandMark(R.drawable.brand_airtable, Color(0xFF18BFFF)),
        "asana" to BrandMark(R.drawable.brand_asana, Color(0xFFF06A6A)),
        "atlassian" to BrandMark(R.drawable.brand_atlassian, Color(0xFF0052CC)),
        "datadog" to BrandMark(R.drawable.brand_datadog, Color(0xFF632CA6)),
        "discord" to BrandMark(R.drawable.brand_discord, Color(0xFF5865F2)),
        "figma" to BrandMark(R.drawable.brand_figma, Color(0xFFF24E1E)),
        "github" to BrandMark(R.drawable.brand_github, null),
        "gitlab" to BrandMark(R.drawable.brand_gitlab, Color(0xFFFC6D26)),
        "gmail" to BrandMark(R.drawable.brand_gmail, Color(0xFFEA4335)),
        "googlecalendar" to BrandMark(R.drawable.brand_googlecalendar, Color(0xFF4285F4)),
        "google-calendar" to BrandMark(R.drawable.brand_googlecalendar, Color(0xFF4285F4)),
        "googledrive" to BrandMark(R.drawable.brand_googledrive, Color(0xFF4285F4)),
        "google-drive" to BrandMark(R.drawable.brand_googledrive, Color(0xFF4285F4)),
        "googlesheets" to BrandMark(R.drawable.brand_googlesheets, Color(0xFF34A853)),
        "hugging_face" to BrandMark(R.drawable.brand_huggingface, Color(0xFFFFD21E)),
        "huggingface" to BrandMark(R.drawable.brand_huggingface, Color(0xFFFFD21E)),
        "intercom" to BrandMark(R.drawable.brand_intercom, Color(0xFF6AFDEF)),
        "jira" to BrandMark(R.drawable.brand_jira, Color(0xFF0052CC)),
        "linear" to BrandMark(R.drawable.brand_linear, Color(0xFF5E6AD2)),
        "n8n" to BrandMark(R.drawable.brand_n8n, Color(0xFFEA4B71)),
        "netlify" to BrandMark(R.drawable.brand_netlify, Color(0xFF00C7B7)),
        "notion" to BrandMark(R.drawable.brand_notion, null),
        "paypal" to BrandMark(R.drawable.brand_paypal, Color(0xFF003087)),
        "postgres" to BrandMark(R.drawable.brand_postgresql, Color(0xFF4169E1)),
        "postgresql" to BrandMark(R.drawable.brand_postgresql, Color(0xFF4169E1)),
        "sentry" to BrandMark(R.drawable.brand_sentry, Color(0xFF362D59)),
        "spotify" to BrandMark(R.drawable.brand_spotify, Color(0xFF1DB954)),
        "square" to BrandMark(R.drawable.brand_square, null),
        "stripe" to BrandMark(R.drawable.brand_stripe, Color(0xFF635BFF)),
        "supabase" to BrandMark(R.drawable.brand_supabase, Color(0xFF3FCF8E)),
        "telegram" to BrandMark(R.drawable.brand_telegram, Color(0xFF26A5E4)),
        "todoist" to BrandMark(R.drawable.brand_todoist, Color(0xFFE44332)),
        "unreal-engine" to BrandMark(R.drawable.brand_unrealengine, null),
        "vercel" to BrandMark(R.drawable.brand_vercel, null),
        "webflow" to BrandMark(R.drawable.brand_webflow, Color(0xFF146EF5)),
        "youtube" to BrandMark(R.drawable.brand_youtube, Color(0xFFFF0000)),
        "zapier" to BrandMark(R.drawable.brand_zapier, Color(0xFFFF4A00)),
    )

    /** [id] is a provider slug (`minimax-oauth`) or a sign-in account id. */
    fun forProvider(id: String): BrandMark? = find(providers, id)

    fun forMcpServer(name: String): BrandMark? = find(mcpServers, name)

    /** The desktop's `brandFor`: letters and digits only; an exact key first, then a key the name contains. */
    private fun find(table: List<Pair<String, BrandMark>>, name: String): BrandMark? {
        val target = squash(name)
        if (target.isEmpty()) return null
        return table.firstOrNull { squash(it.first) == target }?.second
            ?: table.firstOrNull { target.contains(squash(it.first)) }?.second
    }

    private fun squash(value: String): String = value.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
}

/** The mark as an [ImageVector], for [SettingRow]'s icon. */
@Composable
fun BrandMark.vector(): ImageVector = ImageVector.vectorResource(icon)

/** The brand's color, or the theme's text color for a black or white mark. */
@Composable
@ReadOnlyComposable
fun BrandMark.tint(): Color = color ?: MaterialTheme.colorScheme.onSurface
