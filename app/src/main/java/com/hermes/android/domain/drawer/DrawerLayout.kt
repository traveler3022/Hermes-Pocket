package com.hermes.android.domain.drawer

import kotlinx.coroutines.flow.StateFlow

/**
 * What the chat drawer shows, as the user set it in Settings › Appearance › Drawer.
 * Kept on this device. A part turned off never hides chats: topic and pinned chats then
 * sit in the main list with the others.
 *
 * The page switches follow Kyllene's Settings › Sidebar pages (SidebarPagesScreen.kt,
 * SettingsStore.kt `sidebarNav` / `sidebarNavHidden`): Copyright (c) 2026 Giton22, MIT License.
 */
interface DrawerLayout {
    val prefs: StateFlow<DrawerPrefs>
    fun update(edit: (DrawerPrefs) -> DrawerPrefs)
}

data class DrawerPrefs(
    /** The destination tiles at the head of the list. */
    val pages: Boolean = true,
    /** Tiles left out, by [com.hermes.android.ui.screen.WorkspaceDestination.id]; kept while [pages] is off. */
    val hiddenPages: Set<String> = emptySet(),
    /** The row of topic chips. */
    val topics: Boolean = true,
    /** Pinned chats in a section of their own. */
    val pinned: Boolean = true,
    /** The project chips. */
    val projects: Boolean = true,
    /** The All / Running / Needs you chips. */
    val filters: Boolean = true,
    val runningFilter: Boolean = true,
    val needsYouFilter: Boolean = true,
)
