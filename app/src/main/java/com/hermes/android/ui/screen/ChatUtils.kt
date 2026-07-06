package com.hermes.android.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.hermes.android.ui.i18n.t
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast


@Composable
internal fun formatRelativeTime(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestampMs
    val diffSeconds = diffMs / 1000
    val diffMinutes = diffSeconds / 60
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24

    return when {
        diffMs < 0 || diffMinutes < 1 -> t("Just now", "همین الان")
        diffMinutes < 60 -> {
            val m = diffMinutes.toInt()
            t("$m min ago", "$m دقیقه پیش")
        }
        diffHours < 24 -> {
            val h = diffHours.toInt()
            if (h == 1) t("1 hour ago", "۱ ساعت پیش")
            else t("$h hours ago", "$h ساعت پیش")
        }
        diffDays < 2 -> t("Yesterday", "دیروز")
        diffDays < 7 -> {
            val d = diffDays.toInt()
            t("$d days ago", "$d روز پیش")
        }
        diffDays < 30 -> {
            val w = (diffDays / 7).toInt()
            if (w == 1) t("1 week ago", "۱ هفته پیش")
            else t("$w weeks ago", "$w هفته پیش")
        }
        else -> {
            val months = (diffDays / 30).toInt()
            if (months == 1) t("1 month ago", "۱ ماه پیش")
            else t("$months months ago", "$months ماه پیش")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)

@Composable
internal fun highlightText(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val highlightColor = MaterialTheme.colorScheme.tertiary
    val highlightBg = MaterialTheme.colorScheme.tertiaryContainer
    return buildAnnotatedString {
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        var start = 0
        var matchIndex = lowerText.indexOf(lowerQuery, start)
        while (matchIndex >= 0) {
            // Append text before match
            append(text.substring(start, matchIndex))
            // Append highlighted match
            withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold, background = highlightBg)) {
                append(text.substring(matchIndex, matchIndex + query.length))
            }
            start = matchIndex + query.length
            matchIndex = lowerText.indexOf(lowerQuery, start)
        }
        // Append remaining text
        if (start < text.length) {
            append(text.substring(start))
        }
    }
}

@Composable
internal fun thinkingDotStr(): String {
    val transition = rememberInfiniteTransition(label = "thinking_dots")
    // InfiniteTransition exposes animateFloat (not animateInt); animate 0f..4f
    // and floor to an int step.
    val rawStep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dots",
    )
    return when (rawStep.toInt() % 4) { 0 -> ""; 1 -> "."; 2 -> ".."; else -> "..." }
}


internal val codeBlockRegex = Regex("```[\\s\\S]*?```", RegexOption.MULTILINE)
internal fun saveImageToDownloads(context: Context, url: String, alt: String) {
    val filename = alt.ifBlank { url.substringAfterLast('/').substringBefore('?') }
        .ifBlank { "hermes_image.jpg" }
        .let { if (!it.contains('.')) "$it.jpg" else it }
    val request = DownloadManager.Request(Uri.parse(url))
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Hermes/$filename")
        .setTitle(filename)
        .setDescription("در حال دانلود از هرمس")
        .setAllowedOverMetered(true)
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    dm.enqueue(request)
}
internal fun openUrlExternally(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}
