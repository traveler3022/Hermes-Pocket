package com.hermes.android.runtime

import kotlinx.coroutines.flow.StateFlow

/** The built-in Linux's browser & desktop (Chromium over VNC), as its screens drive it. */
interface LinuxDesktopControl {
    val state: StateFlow<DesktopState>
    val settings: StateFlow<DesktopSettings>

    /** noVNC's page for the in-app viewer; load it only after [startViewer] succeeds. */
    val viewerUrl: String

    suspend fun isInstalled(): Boolean
    suspend fun refresh()
    suspend fun start(): Result<Unit>
    suspend fun stop()

    /** Brings the desktop up if nothing else has, then the VNC bridge on top of it. */
    suspend fun startViewer(): Result<Unit>
    fun stopViewerAsync()
    suspend fun restart(): Result<Unit>

    /** Saves [next]; true when it only applies after a restart of the running desktop. */
    suspend fun update(next: DesktopSettings): Boolean
    suspend fun clearBrowserData(): Result<Unit>

    /** This phone's address on the local network, for VNC from another device. */
    fun lanAddress(): String?
    suspend fun focusedElementIsEditable(y: Int): Boolean
}

const val DefaultDesktopHomepage = "https://duckduckgo.com"

/** [pageWidth]: how wide Chromium lays pages out (CSS px), drawn scaled into [width] pixels. */
enum class DesktopResolution(
    val width: Int,
    val height: Int,
    val titleEn: String,
    val titleFa: String,
    val pageWidth: Int = width,
) {
    // Not the phone's full 1080x2040: every frame is drawn on the CPU and sent over VNC, and
    // half the pixels gave ~4x the frames on the server mock; the viewer scales it up. Pages
    // still lay out 1080 wide: at 720, desktop sites (Google…) overflowed and overlapped.
    PHONE(720, 1360, "Phone (portrait)", "گوشی (عمودی)", pageWidth = 1080),
    TABLET(1600, 1000, "Tablet (landscape)", "تبلت (افقی)"),
    DESKTOP(1920, 1080, "Desktop (1080p)", "دسکتاپ (1080p)"),
}

data class DesktopSettings(
    /** Hermes' browser tools use this Chromium (`browser.cdp_url`); it starts on demand (before Hermes starts). */
    val agentBrowser: Boolean = true,
    val resolution: DesktopResolution = DesktopResolution.PHONE,
    val homepage: String = DefaultDesktopHomepage,
    /** Serve VNC on the local network (port 5900) with [vncPassword]. */
    val vncLan: Boolean = false,
    val vncPassword: String = "",
)

sealed interface DesktopState {
    data object Stopped : DesktopState
    data object Starting : DesktopState
    data object Running : DesktopState
    data class Error(val message: String) : DesktopState
}
