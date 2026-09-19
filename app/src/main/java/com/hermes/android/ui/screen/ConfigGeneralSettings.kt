package com.hermes.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ConfigViewModel

/**
 * General settings: the agent-wide switches (command approval, personality,
 * SOUL.md) plus the entry points for Platforms, Memory and Advanced.
 * Every control maps to a real server write — approvals.mode /
 * display.personality via config.set, SOUL.md via the verified shell.exec
 * pattern.
 */
@Composable
internal fun GeneralSettingsSection(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
    onOpen: (SettingsSection) -> Unit,
    onNavigateToPlatforms: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsCardGroup {
            SettingsNavRow(
                title = t("Platforms", "پلتفرم\u200Cها"),
                subtitle = t("Telegram, Discord, Slack", "تلگرام، دیسکورد، اسلک"),
                icon = Icons.Default.Link,
                onClick = onNavigateToPlatforms,
            )
            com.hermes.android.ui.design.GroupDivider()
            SettingsNavRow(
                title = t("Memory", "حافظه"),
                subtitle = "USER.md \u00B7 MEMORY.md",
                icon = Icons.Default.Psychology,
                onClick = { onOpen(SettingsSection.MEMORY) },
            )
            com.hermes.android.ui.design.GroupDivider()
            SettingsNavRow(
                title = t("Advanced", "پیشرفته"),
                subtitle = t("env \u00B7 MCP \u00B7 console \u00B7 log", "env \u00B7 MCP \u00B7 کنسول \u00B7 لاگ"),
                icon = Icons.Default.Terminal,
                onClick = { onOpen(SettingsSection.ADVANCED) },
            )
        }
        ApprovalCard(state, viewModel)
        PersonalityCard(state, viewModel)
        SoulCard(state, viewModel)
    }
}

/** Command approval mode with the risk copy for each mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ApprovalCard(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = t("Command Approval", "تأیید فرمان‌ها"),
                style = MaterialTheme.typography.titleSmall,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes = listOf(
                    "manual" to t("Manual", "دستی"),
                    "smart" to t("Smart", "هوشمند"),
                    "off" to t("Off", "خاموش"),
                )
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = state.approvalMode == mode,
                        onClick = { viewModel.setApprovalMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    ) { Text(label, maxLines = 1) }
                }
            }
            val approvalDescription = when (state.approvalMode) {
                "manual" -> t(
                    "Every risky command asks for your permission before running — the safest mode.",
                    "هر فرمان پرریسک قبل از اجرا از شما اجازه می‌گیرد — امن‌ترین حالت.",
                )
                "smart" -> t(
                    "Low-risk commands run automatically; risky ones still ask.",
                    "فرمان‌های کم‌خطر خودکار اجرا می‌شوند؛ پرریسک‌ها همچنان می‌پرسند.",
                )
                "off" -> t(
                    "Nothing asks for permission (yolo). Only for servers you can afford to lose.",
                    "هیچ‌چیز اجازه نمی‌گیرد (yolo). فقط برای سروری که از دست دادنش مهم نیست.",
                )
                else -> state.approvalMode
            }
            Text(
                text = approvalDescription,
                style = MaterialTheme.typography.labelSmall,
                color = if (state.approvalMode == "off") {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        }
    }
}

/** Personality preset name (display.personality). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonalityCard(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = t("Personality", "شخصیت"),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = t(
                    "Preset name, e.g. helpful / kawaii / pirate",
                    "اسم یک پریست، مثل helpful / kawaii / pirate",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            var personalityText by remember(state.personality) { mutableStateOf(state.personality) }
            OutlinedTextField(
                value = personalityText,
                onValueChange = { personalityText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                placeholder = { Text(t("Enter preset name", "اسم پریست را وارد کنید")) },
                singleLine = true,
            )
            if (personalityText != state.personality) {
                TextButton(
                    onClick = { viewModel.setPersonality(personalityText) },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(t("Save", "ذخیره")) }
            }
        }
    }
}

/** SOUL.md identity editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SoulCard(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var editingSoul by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = t("SOUL.md — persistent identity", "SOUL.md — هویت پایدار عامل"),
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = { editingSoul = !editingSoul }) {
                    Text(if (editingSoul) t("Close", "بستن") else t("Edit", "ویرایش"))
                }
            }
            Text(
                text = t(
                    "The agent's persistent voice & identity — first part of its system prompt",
                    "هویت و لحن ماندگار ایجنت — اولین بخش از دستور سیستم",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            when {
                state.isLoadingSoul -> CircularProgressIndicator(
                    modifier = Modifier.padding(12.dp).size(20.dp),
                    strokeWidth = 2.dp,
                )
                editingSoul -> {
                    var soulText by remember(state.soulMd) { mutableStateOf(state.soulMd) }
                    OutlinedTextField(
                        value = soulText,
                        onValueChange = { soulText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        placeholder = { Text(t("Who is your agent?", "ایجنتت کیه؟")) },
                        minLines = 4,
                    )
                    if (soulText != state.soulMd) {
                        TextButton(
                            onClick = { viewModel.saveSoul(soulText) },
                            modifier = Modifier.align(Alignment.End),
                        ) { Text(t("Save SOUL.md", "ذخیره SOUL.md")) }
                    }
                }
                else -> Text(
                    text = state.soulMd.ifBlank { t("(empty — tap Edit)", "(خالی — روی ویرایش بزنید)") },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        textDirection = androidx.compose.ui.text.style.TextDirection.Ltr,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The 7-level reasoning effort (shown under Models). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReasoningCard(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = t("Reasoning depth", "عمق تفکر"),
                style = MaterialTheme.typography.titleSmall,
            )
            val levels = listOf("none", "minimal", "low", "medium", "high", "xhigh", "max")
            val currentIdx = levels.indexOf(state.reasoning).let { if (it < 0) 3 else it }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                levels.forEachIndexed { i, level ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.setReasoning(level) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (i <= currentIdx) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                ),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "none",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.reasoning,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "max",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = t(
                    "Also switchable mid-session from the chat input bar.",
                    "وسط جلسه هم از نوار ورودی چت قابل تغییر است.",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
