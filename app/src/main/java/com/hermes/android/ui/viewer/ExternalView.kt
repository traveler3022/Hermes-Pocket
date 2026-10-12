package com.hermes.android.ui.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import com.hermes.android.runtime.LinuxFileAccess
import java.io.File

/**
 * Opens [url] in another app. A file of the built-in Linux goes by [FileGateActivity], which
 * reads it off the main thread and opens it, or asks first when it is a program.
 */
internal fun openUrlExternally(context: Context, url: String, linuxFiles: LinuxFileAccess) {
    val linuxPath = Uri.parse(url).takeIf { it.scheme == "file" }?.path?.takeIf { linuxFiles.guestPathOf(it) != null }
    if (linuxPath != null) {
        context.startActivity(FileGateActivity.intent(context, File(linuxPath), open = true))
    } else {
        startExternalView(context, url, linuxFiles)
    }
}

/** [externalViewIntent] for [url], or a toast when no app takes it. */
internal fun startExternalView(context: Context, url: String, linuxFiles: LinuxFileAccess) {
    try {
        context.startActivity(externalViewIntent(url, linuxFiles))
    } catch (e: Exception) {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

/**
 * ACTION_VIEW for [url]. A file of the built-in Linux arrives as a file:// URL into this
 * app's private storage, which Android refuses to put in an intent (and the other app could
 * not read anyway), so "Open in browser" and "Play" always failed for it. It goes out as a
 * document of the Linux Files provider instead, with a read grant for that one file.
 */
internal fun externalViewIntent(url: String, linuxFiles: LinuxFileAccess): Intent {
    val uri = Uri.parse(url)
    val guestPath = uri.takeIf { it.scheme == "file" }?.path
        ?.let(linuxFiles::guestPathOf)
        ?: return Intent(Intent.ACTION_VIEW, uri)
    val document = linuxFiles.documentUri(guestPath)
    val type = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(guestPath.substringAfterLast('.', "").lowercase())
        ?: "*/*"
    return Intent(Intent.ACTION_VIEW)
        .setDataAndType(document, type)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
