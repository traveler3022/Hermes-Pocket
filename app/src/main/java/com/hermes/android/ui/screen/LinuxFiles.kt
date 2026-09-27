package com.hermes.android.ui.screen

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import com.hermes.android.runtime.linux.GuestFiles
import com.hermes.android.runtime.linux.LinuxFilesProvider
import com.hermes.android.runtime.linux.ProotEnvironment

/** Where Hermes works and leaves the files it makes: the gateway's working directory. */
private const val HERMES_HOME_DIR = "/root"

/** True once the built-in Linux is installed, so the Files app has something to show. */
internal fun linuxFilesReady(context: Context): Boolean =
    GuestFiles(ProotEnvironment.rootfsDir(context)).isReady

/**
 * Opens Android's Files app on Hermes' home folder, with the rest of the Linux one level up.
 * Falls back to the provider's root on a Files app that can't open a folder; false when none can.
 */
internal fun openLinuxFiles(context: Context): Boolean {
    val authority = LinuxFilesProvider.authority(context)
    fun view(uri: android.net.Uri, type: String) = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }.isSuccess
    return view(DocumentsContract.buildDocumentUri(authority, HERMES_HOME_DIR), DocumentsContract.Document.MIME_TYPE_DIR) ||
        view(DocumentsContract.buildRootUri(authority, LinuxFilesProvider.ROOT_ID), DocumentsContract.Root.MIME_TYPE_ITEM)
}
