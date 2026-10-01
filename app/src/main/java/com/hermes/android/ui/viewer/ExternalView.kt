package com.hermes.android.ui.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import android.widget.Toast
import com.hermes.android.runtime.linux.LinuxFilesProvider
import com.hermes.android.runtime.linux.ProotEnvironment
import java.io.File

/**
 * Opens [url] in another app. A file of the built-in Linux goes by [FileGateActivity], which
 * reads it off the main thread and opens it, or asks first when it is a program.
 */
internal fun openUrlExternally(context: Context, url: String) {
    val rootfs = ProotEnvironment.rootfsDir(context).absolutePath
    val linuxPath = Uri.parse(url).takeIf { it.scheme == "file" }?.path?.takeIf { guestPathIn(rootfs, it) != null }
    if (linuxPath != null) {
        context.startActivity(FileGateActivity.intent(context, File(linuxPath), open = true))
    } else {
        startExternalView(context, url)
    }
}

/** [externalViewIntent] for [url], or a toast when no app takes it. */
internal fun startExternalView(context: Context, url: String) {
    try {
        context.startActivity(externalViewIntent(context, url))
    } catch (e: Exception) {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

/**
 * ACTION_VIEW for [url]. A file of the built-in Linux arrives as a file:// URL into this
 * app's private storage, which Android refuses to put in an intent (and the other app could
 * not read anyway), so "Open in browser" and "Play" always failed for it. It goes out as a
 * document of [LinuxFilesProvider] instead, with a read grant for that one file.
 */
internal fun externalViewIntent(context: Context, url: String): Intent {
    val uri = Uri.parse(url)
    val guestPath = uri.takeIf { it.scheme == "file" }?.path
        ?.let { guestPathIn(ProotEnvironment.rootfsDir(context).absolutePath, it) }
        ?: return Intent(Intent.ACTION_VIEW, uri)
    val document = DocumentsContract.buildDocumentUri(LinuxFilesProvider.authority(context), guestPath)
    val type = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(guestPath.substringAfterLast('.', "").lowercase())
        ?: "*/*"
    return Intent(Intent.ACTION_VIEW)
        .setDataAndType(document, type)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/** The guest path of [hostPath] when it lies inside [rootfs] (the built-in Linux), else null. */
internal fun guestPathIn(rootfs: String, hostPath: String): String? =
    hostPath.removePrefix(rootfs).takeIf { it != hostPath && it.startsWith("/") }
