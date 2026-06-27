package com.hermes.android.runtime.termux

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.StatFs
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects Termux and related packages (Termux:API, Termux:Boot) on the device.
 *
 * This is an internal helper of [TermuxBridge] — it MUST NOT be referenced
 * outside the [com.hermes.android.runtime.termux] package, to keep the
 * runtime abstraction clean (ADR-009: production must not depend on Termux).
 *
 * Reference: ADR-001 (Termux wrapper), ADR-004 (Termux:Boot temporary)
 */
@Singleton
class TermuxDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Termux packages we care about.
     */
    enum class Package(val packageName: String, val fDroidUrl: String) {
        TERMUX("com.termux", "https://f-droid.org/packages/com.termux/"),
        TERMUX_API("com.termux.api", "https://f-droid.org/packages/com.termux.api/"),
        TERMUX_BOOT("com.termux.boot", "https://f-droid.org/packages/com.termux.boot/"),
    }

    data class Detection(
        val termuxInstalled: Boolean,
        val termuxVersion: String?,
        val termuxApiInstalled: Boolean,
        val termuxBootInstalled: Boolean,
        val diskFreeBytes: Long?,
    )

    fun detect(): Detection {
        val termuxInfo = getPackageInfo(Package.TERMUX.packageName)
        val termuxApiInfo = getPackageInfo(Package.TERMUX_API.packageName)
        val termuxBootInfo = getPackageInfo(Package.TERMUX_BOOT.packageName)

        return Detection(
            termuxInstalled = termuxInfo != null,
            termuxVersion = termuxInfo?.versionName,
            termuxApiInstalled = termuxApiInfo != null,
            termuxBootInstalled = termuxBootInfo != null,
            diskFreeBytes = getExternalStorageFreeBytes(),
        )
    }

    /**
     * Launch the Termux app.
     * Returns true if the launch intent could be resolved.
     */
    fun launchTermux(): Boolean {
        val intent = Intent().apply {
            setClassName(Package.TERMUX.packageName, "com.termux.app.TermuxActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch Termux")
            false
        }
    }

    /**
     * Open F-Droid install page for a Termux package.
     */
    fun openFDroid(pkg: Package): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pkg.fDroidUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to open F-Droid for ${pkg.packageName}")
            false
        }
    }

    private fun getPackageInfo(packageName: String): android.content.pm.PackageInfo? {
        return try {
            // Use getPackageInfo with 0 flags (avoid deprecated PackageManager.GET_META_DATA)
            context.packageManager.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun getExternalStorageFreeBytes(): Long? {
        return try {
            val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            Timber.w(e, "Could not query external storage free space")
            null
        }
    }
}
