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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hermes.android.ui.i18n.AppLanguage
import com.hermes.android.ui.i18n.AppLanguageState
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.theme.AppFont
import com.hermes.android.ui.theme.ColorTheme
import com.hermes.android.ui.theme.ThemeMode
import com.hermes.android.ui.theme.ThemeModeState
import com.hermes.android.ui.theme.TopBarDisplay
import com.hermes.android.ui.viewmodel.ConfigViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GeneralTab(
    state: com.hermes.android.ui.viewmodel.ConfigUiState,
    viewModel: ConfigViewModel,
    themeModeState: ThemeModeState? = null,
    appLanguageState: AppLanguageState? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (themeModeState != null) {
            Text(
                text = t("Appearance", "ظاهر"),
                style = MaterialTheme.typography.titleMedium,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = t("Theme", "تم"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            val label = when (mode) {
                                ThemeMode.SYSTEM -> t("System", "سیستم")
                                ThemeMode.LIGHT -> t("Light", "روشن")
                                ThemeMode.DARK -> t("Dark", "تاریک")
                            }
                            androidx.compose.material3.FilterChip(
                                selected = themeModeState.mode == mode,
                                onClick = { themeModeState.updateMode(mode) },
                                label = { Text(label) },
                            )
                        }
                    }
                    Text(
                        text = t("Color Theme", "رنگ تم"),
                        style = MaterialTheme.typography.titleSmall,
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
                                text = t("Warm / Night mode", "حالت گرم / شب"),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = t(
                                    "Shifts screens toward a warm amber tint to reduce blue light for long sessions.",
                                    "صفحات را به سمت رنگ کهربایی گرم متمایل می‌کند تا نور آبی در استفاده طولانی کمتر شود.",
                                ),
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
                    Text(
                        text = t("Font", "فونت"),
                        style = MaterialTheme.typography.titleSmall,
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
                        text = t("Font size", "اندازه فونت"),
                        style = MaterialTheme.typography.titleSmall,
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
                            onValueChange = { themeModeState.updateFontScalePct(it.toInt()) },
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

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = t("Top bar", "نوار بالا"),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = t("Display in top bar", "نمایش در نوار بالا"),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TopBarDisplay.entries.forEach { mode ->
                        androidx.compose.material3.FilterChip(
                            selected = themeModeState?.topBarDisplay == mode,
                            onClick = { themeModeState?.updateTopBarDisplay(mode) },
                            label = { Text(t(mode.displayEn, mode.displayFa), maxLines = 1) },
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                Text(
                    text = t("Assistant name", "نام دستیار"),
                    style = MaterialTheme.typography.titleSmall,
                )
                OutlinedTextField(
                    value = themeModeState?.assistantName ?: "Hermes",
                    onValueChange = { themeModeState?.updateAssistantName(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(t("Hermes", "هرمس")) },
                )
                if (themeModeState?.topBarDisplay == TopBarDisplay.AVATAR) {
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    Text(
                        text = t("Avatar size", "اندازه آواتار"),
                        style = MaterialTheme.typography.titleSmall,
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
                            value = (themeModeState?.avatarSizeDp ?: 36).toFloat(),
                            onValueChange = { themeModeState?.updateAvatarSizeDp(it.toInt()) },
                            valueRange = 28f..48f,
                            steps = 9,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "A",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${themeModeState?.avatarSizeDp ?: 36} dp",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                    )
                }
            }
        }

        if (appLanguageState != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = t("Language", "زبان"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            val label = when (lang) {
                                AppLanguage.AUTO -> t("Auto", "خودکار")
                                AppLanguage.ENGLISH -> "English"
                                AppLanguage.FARSI -> "فارسی"
                            }
                            androidx.compose.material3.FilterChip(
                                selected = appLanguageState.language == lang,
                                onClick = { appLanguageState.updateLanguage(lang) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = t("Avatar", "آواتار"),
            style = MaterialTheme.typography.titleMedium,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column {
                    Text(
                        text = t("Avatar", "آواتار"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val avatarPicker = rememberLauncherForActivityResult(
                        ActivityResultContracts.GetContent(),
                    ) { uri -> uri?.let { viewModel.setAvatarUri(it) } }
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { avatarPicker.launch("image/*") },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.avatarUri != null) {
                                AsyncImage(
                                    model = state.avatarUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    text = "\u2695",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                        OutlinedButton(onClick = { avatarPicker.launch("image/*") }) {
                            Text(t("Upload image", "آپلود عکس"))
                        }
                        if (state.avatarUri != null) {
                            TextButton(onClick = { viewModel.clearAvatarUri() }) {
                                Text(t("Reset", "بازنشانی"))
                            }
                        }
                    }
                }
            }
        }
    }
}
