package com.hermes.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.data.SessionRepository
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ContextViewModel

/**
 * "Context" sheet — what is filling the current session's context window
 * (session.context_breakdown) plus manual compression (session.compress).
 * Opened over ChatScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextSheet(
    sessionId: String,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    viewModel: ContextViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(sessionId) { viewModel.load(sessionId) }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(uiState.lastActionMessage) {
        uiState.lastActionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HxSpace.screen)
                .padding(bottom = HxSpace.xl),
            verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
        ) {
            Text(t("Context", "کانتکست"), style = MaterialTheme.typography.titleMedium)

            val breakdown = uiState.breakdown
            when {
                uiState.isLoading && breakdown == null -> ContextLoadingRow()
                breakdown == null -> Text(
                    t("Context usage is unavailable.", "اطلاعات کانتکست در دسترس نیست."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    ContextUsageHeader(breakdown)
                    if (breakdown.categories.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(HxSpace.xs),
                        ) {
                            items(breakdown.categories, key = { it.id }) { category ->
                                ContextCategoryRow(category, breakdown.used)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(HxSpace.xs))

            OutlinedTextField(
                value = uiState.focusTopic,
                onValueChange = viewModel::updateFocusTopic,
                singleLine = true,
                enabled = !uiState.isCompressing,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(t("Keep focus on (optional)", "تمرکز روی (اختیاری)")) },
                placeholder = { Text(t("e.g. the login bug", "مثلاً باگ لاگین")) },
            )

            Button(
                onClick = { viewModel.compress() },
                enabled = !uiState.isCompressing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isCompressing) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(HxSpace.sm))
                    Text(t("Compressing…", "در حال فشرده‌سازی…"))
                } else {
                    Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.width(HxSpace.sm))
                    Text(t("Compress history", "فشرده‌سازی تاریخچه"))
                }
            }

            Text(
                t(
                    "Compression summarizes older turns to free room. Stop the current turn first.",
                    "فشرده‌سازی نوبت‌های قدیمی رو خلاصه می‌کنه تا جا باز شه. اول نوبت جاری رو متوقف کن.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContextLoadingRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(HxSpace.lg)) { CircularProgressIndicator() }
}

@Composable
private fun ContextUsageHeader(breakdown: SessionRepository.ContextBreakdown) {
    Column(verticalArrangement = Arrangement.spacedBy(HxSpace.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${breakdown.percent}%",
                style = MaterialTheme.typography.titleLarge,
                color = if (breakdown.percent >= 80) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${formatTokens(breakdown.used)} / ${formatTokens(breakdown.max)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { (breakdown.percent.coerceIn(0, 100)) / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = if (breakdown.percent >= 80) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary,
        )
        if (breakdown.model.isNotBlank()) {
            Text(
                text = breakdown.model,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ContextCategoryRow(category: SessionRepository.ContextCategory, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HxSpace.sm),
    ) {
        Box(
            modifier = Modifier
                .height(10.dp)
                .width(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(categoryColor(category.id)),
        )
        Text(
            text = category.label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatTokens(category.tokens),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (total > 0) {
            Text(
                text = "${category.tokens * 100 / total}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// The server ships CSS custom properties for these, which mean nothing here.
private fun categoryColor(id: String): Color = when (id) {
    "system_prompt" -> Color(0xFF7C8CF8)
    "tool_definitions" -> Color(0xFF4FB6A8)
    "rules" -> Color(0xFFE0A458)
    "skills" -> Color(0xFFB07CC6)
    "mcp" -> Color(0xFF5AA9E6)
    "subagent_definitions" -> Color(0xFFE07A5F)
    "memory" -> Color(0xFF8FBF6B)
    "conversation" -> Color(0xFF9AA3B2)
    else -> Color(0xFF9AA3B2)
}

private fun formatTokens(tokens: Int): String = when {
    tokens >= 1_000_000 -> "${tokens / 100_000 / 10.0}M"
    tokens >= 1_000 -> "${tokens / 1_000}k"
    else -> tokens.toString()
}
