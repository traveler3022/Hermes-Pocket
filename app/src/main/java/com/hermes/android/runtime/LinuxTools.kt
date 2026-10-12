package com.hermes.android.runtime

/** The built-in Linux's optional tools (package profiles) and its terminal. */
interface LinuxTools {

    /** True once the built-in Linux itself is installed. */
    val isRootfsInstalled: Boolean

    val profiles: List<PackageProfile>

    /** Profile id → whether its tools run. Throws when the Linux can't be asked. */
    suspend fun installedProfiles(): Map<String, Boolean>

    /** Installs [profile], handing each output line to [onLog]. Throws when it can't run. */
    suspend fun install(profile: PackageProfile, onLog: (String) -> Unit): InstallOutcome

    /** An interactive shell for the terminal; fails until the Linux is installed. */
    suspend fun terminalLaunchSpec(): Result<TerminalLaunchSpec>

    companion object {
        const val DesktopProfileId = "desktop"
    }
}

/** One of Aether's Alpine package profiles (`AlpineRuntime.AlpinePackageProfiles`). */
data class PackageProfile(
    val id: String,
    val titleEn: String,
    val titleFa: String,
    val packages: List<String>,
)

/** [ok] when the profile's tools run; else [error], the end of the failed command's output. */
data class InstallOutcome(val ok: Boolean, val error: String?)
