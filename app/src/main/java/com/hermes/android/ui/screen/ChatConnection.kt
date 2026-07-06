package com.hermes.android.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ChatConnectionState

@Composable
internal fun ConnectionIndicator(state: ChatConnectionState) {
    val (color, label) = when (state) {
        ChatConnectionState.Connected -> MaterialTheme.colorScheme.primary to t("● Connected", "● متصل")
        ChatConnectionState.Connecting -> MaterialTheme.colorScheme.tertiary to t("◌ Connecting...", "◌ در حال اتصال...")
        ChatConnectionState.Reconnecting -> MaterialTheme.colorScheme.tertiary to t("↻ Reconnecting...", "↻ اتصال دوباره...")
        ChatConnectionState.Disconnected -> MaterialTheme.colorScheme.outline to t("○ Tap to Connect", "○ برای اتصال لمس کنید")
        ChatConnectionState.Failed -> MaterialTheme.colorScheme.error to t("✕ Termux Error", "✕ خطای ترموکس")
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ── Agent task list (todos from tool.start / tool.complete) ──────────────

@Composable
internal fun ConnectionRetryBanner(
    state: ChatConnectionState,
    onRetry: () -> Unit,
) {
    val (title, subtitle) = when (state) {
        ChatConnectionState.Failed -> t("Connection Failed", "اتصال ناموفق") to
                t("Could not connect to Hermes gateway", "اتصال به گیت‌وی هرمس ممکن نیست")
        ChatConnectionState.Disconnected -> t("Disconnected", "قطع شده") to
                t("Not connected to Hermes gateway", "به گیت‌وی هرمس متصل نیست")
        else -> return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(t("Reconnect", "اتصال دوباره"))
            }
        }
    }
}

// ── Feature #32: Shimmer skeleton ────────────────────────────────────────

@Composable
internal fun ShimmerSkeleton() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_progress",
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        start = Offset(shimmerProgress - 300f, 0f),
        end = Offset(shimmerProgress, 0f),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Fake user message (right-aligned)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .height(48.dp)
                    .background(shimmerBrush, RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)),
            )
        }
        // Fake assistant message (left-aligned, tall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .height(80.dp)
                    .background(shimmerBrush, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)),
            )
        }
        // Fake user message
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 200.dp)
                    .height(40.dp)
                    .background(shimmerBrush, RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)),
            )
        }
        // Fake assistant message (left-aligned)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 350.dp)
                    .height(100.dp)
                    .background(shimmerBrush, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)),
            )
        }
    }
}

