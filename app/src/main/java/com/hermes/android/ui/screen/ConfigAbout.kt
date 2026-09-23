package com.hermes.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.data.AppRelease
import com.hermes.android.data.AppReleaseRepository
import com.hermes.android.data.UpdateCheck
import com.hermes.android.ui.component.HermesMarkdown
import com.hermes.android.ui.design.HxIcons
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.design.SectionHeader
import com.hermes.android.ui.design.SettingRow
import com.hermes.android.ui.design.SettingsGroup
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.AboutUiState
import com.hermes.android.ui.viewmodel.AboutViewModel
import com.hermes.android.ui.viewmodel.HermesUpdate

/**
 * What this build is, and whether a newer one has been published.
 *
 * The app is installed by hand from a GitHub release, so nothing tells the user
 * a new build exists — they would have to think to go and look. This asks for
 * them, and shows the release notes so the answer to "should I bother" is on
 * the same screen as the question.
 */
@Composable
internal fun AboutSection(viewModel: AboutViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = HxSpace.md),
        verticalArrangement = Arrangement.spacedBy(HxSpace.md),
    ) {
        // Two things update here, and they are easy to mistake for one: the app
        // (screens, installed from a GitHub release) and Hermes Agent (the agent
        // itself, inside the built-in Linux). Each gets its own name, version and
        // one line saying what it is, so neither button looks like the other.
        SectionHeader(t("App — Hermes for Android", "برنامه — Hermes برای اندروید"))
        SettingsGroup {
            SettingRow(
                title = t("App version", "نسخهٔ برنامه"),
                subtitle = state.installedVersion,
                icon = HxIcons.Sparkles,
            )
            SettingRow(
                title = t("Source code", "کد منبع"),
                subtitle = t("Open the project on GitHub", "باز کردن پروژه در گیت‌هاب"),
                icon = HxIcons.ExternalLink,
                onClick = { uriHandler.openUri(AppReleaseRepository.PROJECT_URL) },
            )
        }

        SettingsGroup {
            Column(
                modifier = Modifier.padding(HxSpace.inner),
                verticalArrangement = Arrangement.spacedBy(HxSpace.md),
            ) {
                Text(
                    text = t(
                        "Hermes Pocket is installed by hand from a GitHub release, so nothing announces a new build. Check whenever you want to know.",
                        "هرمس پاکت دستی از روی ریلیزهای گیت‌هاب نصب می‌شود، پس چیزی خبر نسخهٔ جدید را نمی‌دهد. هر وقت خواستی بررسی کن.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = viewModel::check,
                    enabled = !state.isChecking,
                ) {
                    if (state.isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(t("Check for app updates", "بررسی به‌روزرسانی برنامه"))
                    }
                }

                when (val result = state.result) {
                    null -> Unit

                    is UpdateCheck.UpToDate -> UpdateNote(
                        text = t(
                            "You are on the newest release.",
                            "روی جدیدترین نسخه‌ای.",
                        ),
                    )

                    // A failed check says so rather than claiming the app is up
                    // to date — silence would be the same shape as good news.
                    is UpdateCheck.Failed -> UpdateNote(
                        text = t(
                            "Could not check right now — ${result.reason}.",
                            "الان نشد بررسی کرد — ${result.reason}.",
                        ),
                        isProblem = true,
                    )

                    is UpdateCheck.Available -> AvailableRelease(
                        release = result.release,
                        installed = state.installedVersion,
                        onOpen = { uriHandler.openUri(result.release.pageUrl) },
                    )
                }
            }
        }

        HermesAgentSection(
            state = state,
            onUpdate = viewModel::updateHermes,
        )
    }
}

@Composable
private fun UpdateNote(text: String, isProblem: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isProblem) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun AvailableRelease(
    release: AppRelease,
    installed: String,
    onOpen: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HxSpace.sm)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HxSpace.sm),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(HxRadius.sm))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "$installed → ${release.versionName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
            release.publishedAt?.let { date ->
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = release.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Release notes are markdown on GitHub, and the app already renders
        // markdown — showing them raw would be the one place it did not.
        if (release.notes.isNotBlank()) {
            HermesMarkdown(
                markdown = release.notes,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }

        Button(onClick = onOpen) {
            Icon(
                HxIcons.ExternalLink,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = t("Open the release", "باز کردن ریلیز"),
                modifier = Modifier.padding(start = HxSpace.sm),
            )
        }
    }
}

/** Hermes Agent itself: its version, and an in-place update for the built-in Linux. */
@Composable
private fun HermesAgentSection(state: AboutUiState, onUpdate: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    SectionHeader(t("Hermes Agent — the core", "هستهٔ Hermes Agent"))
    SettingsGroup {
        SettingRow(
            title = t("Core version", "نسخهٔ هسته"),
            subtitle = state.hermesVersion ?: "—",
            icon = HxIcons.Terminal,
        )
        Column(
            modifier = Modifier.padding(HxSpace.inner),
            verticalArrangement = Arrangement.spacedBy(HxSpace.md),
        ) {
            Text(
                text = t(
                    "The agent that writes the replies and runs the tools. It lives in the built-in Linux and updates separately from the app.",
                    "خود عامل که جواب‌ها را می‌نویسد و ابزارها را اجرا می‌کند. داخل Linux داخلی است و جدا از برنامه به‌روز می‌شود.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.canUpdateHermes) {
                UpdateNote(
                    text = t(
                        "Hermes runs outside the app here (Termux or a server); update it there.",
                        "اینجا هرمس بیرون از برنامه اجرا می‌شود (Termux یا سرور)؛ همان‌جا به‌روزش کن.",
                    ),
                )
                return@Column
            }
            val update = state.hermesUpdate
            Button(
                onClick = { confirming = true },
                enabled = update !is HermesUpdate.Running,
            ) {
                Text(t("Update the Hermes core", "به‌روزرسانی هستهٔ هرمس"))
            }
            when (update) {
                HermesUpdate.Idle -> Unit
                is HermesUpdate.Running -> {
                    if (update.percent != null) {
                        LinearProgressIndicator(
                            progress = { update.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (update.message.isNotBlank()) UpdateNote(text = update.message)
                }
                is HermesUpdate.Done -> UpdateNote(
                    text = if (update.changed) {
                        t("Hermes core updated: ${update.version}", "هستهٔ هرمس به‌روز شد: ${update.version}")
                    } else {
                        t("The core was already the newest version.", "هسته از قبل جدیدترین نسخه بود.")
                    },
                )
                is HermesUpdate.Failed -> UpdateNote(
                    text = t(
                        "Update failed — ${update.reason}. Hermes is being started again.",
                        "به‌روزرسانی نشد — ${update.reason}. هرمس دوباره روشن می‌شود.",
                    ),
                    isProblem = true,
                )
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(t("Update the Hermes core?", "هستهٔ هرمس به‌روز شود؟")) },
            text = {
                Text(
                    t(
                        "Hermes stops for a few minutes while it downloads, and any reply in progress is cut off. Your chats and settings stay.",
                        "هرمس چند دقیقه برای دانلود خاموش می‌شود و جوابی که در حال نوشتن است قطع می‌شود. گفتگوها و تنظیمات سر جایشان می‌مانند.",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirming = false; onUpdate() }) { Text(t("Update", "به‌روزرسانی")) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(t("Cancel", "انصراف")) }
            },
        )
    }
}
