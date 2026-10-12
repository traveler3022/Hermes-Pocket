package com.hermes.android.ui.screen

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import com.hermes.android.runtime.LinuxFileAccess

/**
 * Opens Android's Files app on Hermes' home folder, with the rest of the Linux one level up.
 * Falls back to the provider's root on a Files app that can't open a folder; false when none can.
 */
internal fun openLinuxFiles(context: Context, files: LinuxFileAccess): Boolean {
    fun view(uri: android.net.Uri, type: String) = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }.isSuccess
    return view(files.homeFolderUri, DocumentsContract.Document.MIME_TYPE_DIR) ||
        view(files.rootUri, DocumentsContract.Root.MIME_TYPE_ITEM)
}
