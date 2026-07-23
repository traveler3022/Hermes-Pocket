package com.hermes.android.ui.screen

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.design.HermesEmptyState
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.StatTile
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.HistoryMessage
import com.hermes.android.ui.viewmodel.SessionSortOrder
import com.hermes.android.ui.viewmodel.SessionSummary
import com.hermes.android.ui.viewmodel.SessionsEffect
import com.hermes.android.ui.viewmodel.SessionsUiState
import com.hermes.android.ui.viewmodel.SessionsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun HistoryDetailView(
    messages: List<HistoryMessage>,
    isLoading: Boolean,
    usage: com.hermes.android.ui.viewmodel.SessionUsage?,
    onResumeSession: () -> Unit,
) {
    if (isLoading) {
        LoadingIndicator(t("Loading messages...", "در حال بارگذاری پیام‌ها..."))
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = onResumeSession,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HxSpace.screen, vertical = HxSpace.sm),
        ) {
            Text(t("Continue this chat", "ادامه این گفتگو"))
        }

        usage?.takeIf { it.total > 0 || it.calls > 0 }?.let { u ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(HxSpace.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HxSpace.screen, vertical = HxSpace.xs),
            ) {
                StatTile(value = "${u.input}", label = t("in", "ورودی"))
                StatTile(value = "${u.output}", label = t("out", "خروجی"))
                StatTile(value = "${u.total}", label = t("total", "کل"))
                StatTile(value = "${u.calls}", label = t("calls", "فراخوانی"))
            }
            u.creditsLines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = HxSpace.screen),
                )
            }
        }

        if (messages.isEmpty()) {
            HermesEmptyState(
                icon = Icons.Default.Forum,
                title = t("No messages found", "پیامی پیدا نشد"),
                caption = t("This session may be empty or inaccessible", "این گفتگو خالی یا در دسترس نیست"),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = HxSpace.md, vertical = HxSpace.xs),
                verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
            ) {
                items(messages, key = { it.role + it.content.take(40) + messages.indexOf(it) }) { msg ->
                    HistoryMessageBubble(msg)
                }
            }
        }
    }
}

@Composable
internal fun HistoryMessageBubble(msg: HistoryMessage) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(HxRadius.md),
            color = if (isUser) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            Column(modifier = Modifier.padding(HxSpace.md)) {
                Text(
                    text = if (isUser) t("You", "شما") else "Hermes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Text(
                    text = msg.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

// ── Active agents ─────────────────────────────────────────────────────────

@Composable
internal fun ActiveAgentsCard(state: SessionsUiState) {
    val agents = state.activeAgents
    Surface(
        shape = RoundedCornerShape(HxRadius.md),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(HxSpace.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary),
                )
                Text(
                    text = t("Active agents (${agents.size})", "ایجنت‌های فعال (${agents.size})"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            agents.take(8).forEach { a ->
                Text(
                    text = "${a.command}  —  ${a.status}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

// LoadingIndicator: internal fun in ConfigComponents.kt (same package, no import needed)
