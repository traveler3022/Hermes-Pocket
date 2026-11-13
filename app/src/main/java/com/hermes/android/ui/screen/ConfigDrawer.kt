package com.hermes.android.ui.screen

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.R
import com.hermes.android.domain.drawer.DrawerPrefs
import com.hermes.android.ui.design.GroupDivider
import com.hermes.android.ui.design.HxIcons
import com.hermes.android.ui.design.SettingRow
import com.hermes.android.ui.viewmodel.DrawerLayoutViewModel

/**
 * Settings › Appearance › Drawer: which parts of the chat drawer show. A part turned off
 * never hides chats; topic and pinned chats then sit in the main list.
 *
 * The page switches follow Kyllene's Settings › Sidebar pages (SidebarPagesScreen.kt):
 * one switch for all the page buttons, then one for each. Copyright (c) 2026 Giton22, MIT License.
 */
@Composable
internal fun DrawerSettingsSection(viewModel: DrawerLayoutViewModel = hiltViewModel()) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val update = viewModel::update
    // The tiles as the drawer builds them, for their names and icons; nothing here opens them.
    val pages = rememberWorkspaceDestinations(
        onScheduled = {},
        onGroups = {},
        onMedia = {},
        onFiles = {},
        onKanban = {},
    )
    SettingsFolder {
        SettingsLabel(stringResource(R.string.drawer_sections))
        SettingsCardGroup {
            SwitchRow(
                title = stringResource(R.string.topics_section),
                detail = stringResource(R.string.drawer_topics_detail),
                icon = HxIcons.Hash,
                checked = prefs.topics,
                onChange = { on -> update { it.copy(topics = on) } },
            )
            GroupDivider(startIndent = 62.dp)
            SwitchRow(
                title = stringResource(R.string.pinned),
                detail = stringResource(R.string.drawer_pinned_detail),
                icon = HxIcons.Pin,
                checked = prefs.pinned,
                onChange = { on -> update { it.copy(pinned = on) } },
            )
            GroupDivider(startIndent = 62.dp)
            SwitchRow(
                title = stringResource(R.string.drawer_projects_title),
                detail = stringResource(R.string.drawer_projects_detail),
                icon = HxIcons.FolderInput,
                checked = prefs.projects,
                onChange = { on -> update { it.copy(projects = on) } },
            )
            GroupDivider(startIndent = 62.dp)
            SwitchRow(
                title = stringResource(R.string.drawer_filters_title),
                detail = stringResource(R.string.drawer_filters_detail),
                icon = HxIcons.CircleDot,
                checked = prefs.filters,
                // Off keeps which chips were left out, for when it's back on.
                onChange = { on -> update { it.copy(filters = on) } },
            )
        }

        if (prefs.filters) {
            SettingsLabel(stringResource(R.string.drawer_filters_each))
            SettingsCardGroup {
                SwitchRow(
                    title = stringResource(R.string.running),
                    detail = null,
                    icon = HxIcons.Bot,
                    checked = prefs.runningFilter,
                    onChange = { on -> update { it.copy(runningFilter = on) } },
                )
                GroupDivider(startIndent = 62.dp)
                SwitchRow(
                    title = stringResource(R.string.drawer_filter_needs_you),
                    detail = null,
                    icon = HxIcons.ShieldAlert,
                    checked = prefs.needsYouFilter,
                    onChange = { on -> update { it.copy(needsYouFilter = on) } },
                )
            }
        }

        SettingsLabel(stringResource(R.string.drawer_pages_title))
        SettingsCardGroup {
            SwitchRow(
                title = stringResource(R.string.drawer_pages_show),
                detail = stringResource(R.string.drawer_pages_detail),
                icon = HxIcons.LayoutGrid,
                checked = prefs.pages,
                // Off keeps which tiles were left out, for when it's back on.
                onChange = { on -> update { it.copy(pages = on) } },
            )
        }
        if (prefs.pages) {
            SettingsLabel(stringResource(R.string.drawer_pages_each))
            SettingsCardGroup {
                pages.forEachIndexed { i, page ->
                    if (i > 0) GroupDivider(startIndent = 62.dp)
                    SwitchRow(
                        title = page.label,
                        detail = null,
                        icon = page.icon,
                        checked = page.id !in prefs.hiddenPages,
                        onChange = { on -> update { it.withPage(page.id, on) } },
                    )
                }
            }
        }
    }
}

/** [prefs] with the tile [id] shown or hidden. */
internal fun DrawerPrefs.withPage(id: String, show: Boolean): DrawerPrefs =
    copy(hiddenPages = if (show) hiddenPages - id else hiddenPages + id)

/** A settings row with a switch at its end; the whole row flips it. */
@Composable
private fun SwitchRow(
    title: String,
    detail: String?,
    icon: ImageVector,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingRow(
        title = title,
        subtitle = detail,
        icon = icon,
        onClick = { onChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}
