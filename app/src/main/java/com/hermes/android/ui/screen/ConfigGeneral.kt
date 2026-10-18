package com.hermes.android.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hermes.android.R
import com.hermes.android.i18n.AppLanguage
import com.hermes.android.ui.i18n.AppLanguageState
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.theme.AppFont
import com.hermes.android.ui.theme.ColorTheme
import com.hermes.android.ui.theme.ThemeMode
import com.hermes.android.ui.theme.ThemeModeState
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GeneralTab(
    themeModeState: ThemeModeState? = null,
    appLanguageState: AppLanguageState? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(com.hermes.android.ui.design.SettingsPagePadding),
        verticalArrangement = Arrangement.spacedBy(com.hermes.android.ui.design.HxSpace.group),
    ) {
        // Language now has its own row on the Control Center root (LanguageSection).

        if (themeModeState != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = com.hermes.android.ui.design.settingsCardColor()),
            ) {
                Column(
                    modifier = Modifier.padding(com.hermes.android.ui.design.HxSpace.inner),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.theme),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            val label = when (mode) {
                                ThemeMode.SYSTEM -> stringResource(R.string.system)
                                ThemeMode.LIGHT -> stringResource(R.string.light)
                                ThemeMode.DARK -> stringResource(R.string.dark)
                            }
                            androidx.compose.material3.FilterChip(
                                selected = themeModeState.mode == mode,
                                onClick = { themeModeState.updateMode(mode) },
                                label = { Text(label) },
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.color_theme),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ColorTheme.entries.forEach { theme ->
                            val label = t(theme.displayEn, theme.displayFa)
                            androidx.compose.material3.FilterChip(
                                selected = themeModeState.colorTheme == theme,
                                onClick = { themeModeState.updateColorTheme(theme) },
                                label = { Text(label, maxLines = 1) },
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.warm_night_mode),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.shifts_screens_toward_a_warm_amber_tint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = themeModeState.warmMode,
                            onCheckedChange = { themeModeState.updateWarmMode(it) },
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.show_the_agents_running_commentary),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.what_the_agent_says_between_tool_calls),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = themeModeState.showInlineNarration,
                            onCheckedChange = { themeModeState.updateShowInlineNarration(it) },
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.message_reactions),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.touch_and_hold_a_message_to_react_with),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = themeModeState.messageReactions,
                            onCheckedChange = { themeModeState.updateMessageReactions(it) },
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    Text(
                        text = stringResource(R.string.font),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppFont.entries.forEach { font ->
                            androidx.compose.material3.FilterChip(
                                selected = themeModeState.appFont == font,
                                onClick = { themeModeState.updateAppFont(font) },
                                label = { Text(t(font.displayEn, font.displayFa)) },
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    Text(
                        text = stringResource(R.string.font_size),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "A",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = themeModeState.fontScalePct.toFloat(),
                            // Rounded, not truncated: a snapped step can land a hair under its value (114.99…).
                            onValueChange = { themeModeState.updateFontScalePct(it.roundToInt()) },
                            valueRange = 80f..140f,
                            steps = 11,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "A",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${themeModeState.fontScalePct}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                    )
                }
            }
        }

    }
}

/** Kanban, off unless turned on: shows the board, and on the built-in Linux runs it too. */
@Composable
internal fun KanbanSettingRow(
    viewModel: com.hermes.android.ui.viewmodel.KanbanSettingViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val on by viewModel.on.collectAsStateWithLifecycle()
    val applying by viewModel.applying.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.kanban_board),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.a_task_board_your_profiles_work_through),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (applying) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Switch(checked = on, onCheckedChange = viewModel::set)
        }
    }
}
