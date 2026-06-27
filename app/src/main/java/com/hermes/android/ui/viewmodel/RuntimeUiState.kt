package com.hermes.android.ui.viewmodel

/**
 * UI-facing state model for the Runtime Setup screen.
 *
 * This is the ONLY type the UI is allowed to observe for runtime state.
 * It decouples the UI from the runtime layer's internal state representation
 * (RuntimeState sealed class in the runtime package).
 *
 * Per Phase 1.5 Rule 1 (Strict Layer Dependency):
 *   ui.screen → ui.viewmodel ONLY
 *   ui.screen must NOT import from runtime package
 *
 * The ViewModel converts RuntimeState → RuntimeUiState and exposes it.
 */
sealed class RuntimeUiState {

    /** Initial state — runtime has not been probed yet. */
    object NotDetected : RuntimeUiState()

    /** Detection in progress. */
    object Detecting : RuntimeUiState()

    /**
     * Runtime detected but not yet installed.
     */
    data class Detected(
        val version: String?,
        val diskFreeBytes: Long?,
    ) : RuntimeUiState()

    /** Installation in progress. */
    object Installing : RuntimeUiState()

    /** Runtime is installed and ready. */
    data class Installed(val hermesVersion: String?) : RuntimeUiState()

    /** Gateway is running. */
    data class Running(val webSocketUrl: String) : RuntimeUiState()

    /** An error occurred. */
    data class Error(val message: String) : RuntimeUiState()
}

/**
 * UI-facing progress model.
 */
data class InstallProgressUi(
    val stage: String,
    val message: String,
    val percent: Int?,
)

/**
 * UI-facing install instructions model.
 */
data class InstallInstructionsUi(
    val title: String,
    val steps: List<String>,
    val command: String?,
)
