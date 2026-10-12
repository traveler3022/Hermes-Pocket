package com.hermes.android.runtime.linux

import com.hermes.android.runtime.InstallOutcome
import com.hermes.android.runtime.LinuxTools
import com.hermes.android.runtime.PackageProfile
import com.hermes.android.runtime.TerminalLaunchSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** [LinuxTools] on the proot Linux: `apk add` per profile, checked by each profile's verify command. */
@Singleton
class LinuxPackages @Inject constructor(
    private val environment: ProotEnvironment,
) : LinuxTools {

    /** [verify] decides "installed"; [postInstall] runs after `apk add`, e.g. an npm install the profile also needs. */
    private class Spec(val profile: PackageProfile, val verify: String, val postInstall: String? = null)

    override val isRootfsInstalled: Boolean get() = environment.isRootfsInstalled

    override val profiles: List<PackageProfile> = Specs.map { it.profile }

    override suspend fun installedProfiles(): Map<String, Boolean> =
        environment.run(checkScript()).output.lineSequence()
            .filter { it.startsWith(STATUS_PREFIX) }
            .associate { line ->
                val (id, status) = line.removePrefix(STATUS_PREFIX).split(' ', limit = 2)
                id to (status == "ok")
            }

    /** Aether's `installPackageProfile`: `apk add`, then trust the verify command over apk's exit code. */
    override suspend fun install(profile: PackageProfile, onLog: (String) -> Unit): InstallOutcome {
        val spec = Specs.first { it.profile.id == profile.id }
        val command = "apk add --no-cache --no-chown ${profile.packages.joinToString(" ")}"
        onLog("$ $command")
        val apk = environment.run(command, forApk = true, onLine = onLog)
        var result = apk
        if (profile.id == LinuxTools.DesktopProfileId) {
            // The post-install fetches the X programs the Linux's repo lacks from Debian.
            val staged = withContext(Dispatchers.IO) {
                environment.copyAssetToGuest(LinuxDesktop.DebianFetchAsset, LinuxDesktop.DebianFetchPath)
            }
            check(staged) { "debian_fetch.py is missing from this build" }
        }
        spec.postInstall?.let { post ->
            onLog("$ $post")
            result = environment.run(post, onLine = onLog)
        }
        // apk can exit 1 on Android ("failed to write database") after installing everything.
        // A post-install that succeeds says nothing about apk's packages, so both count.
        val ok = (apk.ok && result.ok) || environment.run(spec.verify).ok
        val failed = if (apk.ok) result else apk
        return InstallOutcome(ok, if (ok) null else failed.output.lines().takeLast(3).joinToString("\n"))
    }

    override suspend fun terminalLaunchSpec(): Result<TerminalLaunchSpec> = withContext(Dispatchers.IO) {
        runCatching {
            check(environment.isRootfsInstalled) { "Install the built-in Linux runtime first." }
            environment.terminalLaunchSpec()
        }
    }

    private fun checkScript(): String = Specs.joinToString("\n") { spec ->
        "if ( ${spec.verify} ) >/dev/null 2>&1; then echo '$STATUS_PREFIX${spec.profile.id} ok'; " +
            "else echo '$STATUS_PREFIX${spec.profile.id} missing'; fi"
    }

    private companion object {
        private const val STATUS_PREFIX = "HERMES2_PROFILE "

        /**
         * ripgrep and fd are not in the Linux's package repo; their own static release builds
         * run on any Linux. Pinned versions, arch from the guest (aarch64 on phones).
         */
        private const val RgFdInstall =
            "set -e; a=\$(uname -m); cd /tmp; " +
                "curl -fsSL --retry 3 https://github.com/BurntSushi/ripgrep/releases/download/15.2.0/ripgrep-15.2.0-\$a-unknown-linux-musl.tar.gz | tar -xz; " +
                "curl -fsSL --retry 3 https://github.com/sharkdp/fd/releases/download/v10.5.0/fd-v10.5.0-\$a-unknown-linux-musl.tar.gz | tar -xz; " +
                "mkdir -p /usr/local/bin; " +
                "install -m 755 ripgrep-15.2.0-\$a-unknown-linux-musl/rg fd-v10.5.0-\$a-unknown-linux-musl/fd /usr/local/bin/; " +
                "rm -rf ripgrep-15.2.0-\$a-unknown-linux-musl fd-v10.5.0-\$a-unknown-linux-musl"

        private val Specs = listOf(
            Spec(
                PackageProfile("python", "Python tools", "ابزارهای پایتون", listOf("python3", "py3-pip", "py3-virtualenv")),
                "python3 --version && pip3 --version && virtualenv --version",
            ),
            Spec(
                PackageProfile("node", "Node.js", "Node.js", listOf("nodejs", "npm")),
                "node --version && npm --version",
            ),
            Spec(
                PackageProfile("git_search", "Git & code search", "Git و جستجوی کد", listOf("git")),
                "git --version && rg --version && fd --version",
                postInstall = RgFdInstall,
            ),
            Spec(
                PackageProfile("ssh", "SSH client", "کلاینت SSH", listOf("openssh-client-default")),
                "ssh -V",
            ),
            Spec(
                PackageProfile(
                    LinuxTools.DesktopProfileId, "Browser & desktop (VNC)", "مرورگر و دسکتاپ (VNC)",
                    LinuxDesktop.Packages,
                ),
                LinuxDesktop.VerifyCommand,
                postInstall = LinuxDesktop.PostInstall,
            ),
        )
    }
}
