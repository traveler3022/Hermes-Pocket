package com.hermes.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import com.hermes.android.R
import com.hermes.android.ui.icons.automirrored.filled.ArrowForwardIos
import com.hermes.android.ui.icons.filled.Description
import androidx.compose.material.icons.filled.Face
import com.hermes.android.ui.icons.filled.Security
import com.hermes.android.ui.icons.filled.Link
import com.hermes.android.ui.icons.filled.Psychology
import com.hermes.android.ui.icons.filled.Terminal
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ConfigViewModel

/**
 * General settings folder, laid out like Aether's settings pages: each item
 * is one row that opens its own page, so this screen stays a short list.
 * Every control maps to a real server write — approvals.mode /
 * display.personality via config.set, SOUL.md via the verified shell.exec
 * pattern.
 */
@Composable
internal fun GeneralSettingsSection(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    onOpen: (SettingsSection) -> Unit,
    onNavigateToPlatforms: () -> Unit,
) {
    SettingsFolder {
        SettingsCardGroup {
            SettingsNavRow(
                title = stringResource(R.string.command_approval_2),
                subtitle = t(state.approvalMode, approvalModeFa(state.approvalMode)),
                icon = Icons.Default.Security,
                onClick = { onOpen(SettingsSection.APPROVAL) },
            )
            com.hermes.android.ui.design.GroupDivider()
            SettingsNavRow(
                title = stringResource(R.string.personality),
                subtitle = stringResource(R.string.preset_soul_md_memory),
                icon = Icons.Default.Face,
                onClick = { onOpen(SettingsSection.PERSONALITY) },
            )
        }
        SettingsCardGroup {
            SettingsNavRow(
                title = stringResource(R.string.platforms),
                subtitle = stringResource(R.string.telegram_discord_slack),
                icon = Icons.Default.Link,
                onClick = onNavigateToPlatforms,
            )
            com.hermes.android.ui.design.GroupDivider()
            SettingsNavRow(
                title = stringResource(R.string.advanced),
                subtitle = stringResource(R.string.env_mcp_console_log),
                icon = Icons.Default.Terminal,
                onClick = { onOpen(SettingsSection.ADVANCED) },
            )
        }
    }
}

/** Scrollable page body for a settings folder or a single-setting page. */
@Composable
internal fun SettingsFolder(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/** A rounded card holding a stack of rows (Aether's card group). */
@Composable
internal fun SettingsCardGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                // Aether's cards are its surface on the grey settings page.
                if (com.hermes.android.ui.theme.LocalAetherStyle.current) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
        content = content,
    )
}

/** One tappable row that opens a sub-page: icon, title, one-line subtitle, chevron. */
@Composable
internal fun SettingsNavRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp),
        )
    }
}

/** Personality folder: the preset, plus SOUL.md and the memory files. */
@Composable
internal fun PersonalitySection(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
    onOpen: (SettingsSection) -> Unit,
) {
    SettingsFolder {
        PersonalityCard(state, viewModel)
        SettingsCardGroup {
            SettingsNavRow(
                title = "SOUL.md",
                subtitle = state.soulMd.lineSequence().firstOrNull { it.isNotBlank() }
                    ?: stringResource(R.string.persistent_identity),
                icon = Icons.Default.Description,
                onClick = { onOpen(SettingsSection.SOUL) },
            )
            com.hermes.android.ui.design.GroupDivider()
            SettingsNavRow(
                title = stringResource(R.string.memory),
                subtitle = "USER.md \u00B7 MEMORY.md",
                icon = Icons.Default.Psychology,
                onClick = { onOpen(SettingsSection.MEMORY) },
            )
        }
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
                text = stringResource(R.string.command_approval_2),
                style = MaterialTheme.typography.titleSmall,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes = listOf(
                    "manual" to stringResource(R.string.manual),
                    "smart" to stringResource(R.string.smart),
                    "off" to stringResource(R.string.off),
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
                "manual" -> stringResource(R.string.every_risky_command_asks_for_your)
                "smart" -> stringResource(R.string.low_risk_commands_run_automatically)
                "off" -> stringResource(R.string.nothing_asks_for_permission_yolo_only)
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
                text = stringResource(R.string.personality),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.preset_name_e_g_helpful_kawaii_pirate),
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
                placeholder = { Text(stringResource(R.string.enter_preset_name)) },
                singleLine = true,
            )
            if (personalityText != state.personality) {
                TextButton(
                    onClick = { viewModel.setPersonality(personalityText) },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.save)) }
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
                    text = stringResource(R.string.soul_md_persistent_identity),
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = { editingSoul = !editingSoul }) {
                    Text(if (editingSoul) stringResource(R.string.close) else stringResource(R.string.edit))
                }
            }
            Text(
                text = stringResource(R.string.the_agents_persistent_voice_identity),
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
                        placeholder = { Text(stringResource(R.string.who_is_your_agent)) },
                        minLines = 4,
                    )
                    if (soulText != state.soulMd) {
                        TextButton(
                            onClick = { viewModel.saveSoul(soulText) },
                            modifier = Modifier.align(Alignment.End),
                        ) { Text(stringResource(R.string.save_soul_md)) }
                    }
                }
                else -> Text(
                    text = state.soulMd.ifBlank { stringResource(R.string.empty_tap_edit) },
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
                text = stringResource(R.string.reasoning_depth),
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
                text = stringResource(R.string.also_switchable_mid_session_from_the),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
