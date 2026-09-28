package com.hermes.android.runtime.linux

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.IOException

/**
 * A picked file as a path the built-in Linux can open, the way Hermes desktop hands its
 * gateway a path on the same disk instead of the bytes. A file picked from the Linux itself
 * (LinuxFilesProvider) is already there: its document id is the guest path. Anything else
 * (gallery, the phone's Download, Drive) is streamed once into [GUEST_DIR].
 */
class LinuxUploads(private val context: Context) {
    private val files = GuestFiles(ProotEnvironment.rootfsDir(context))

    fun guestPath(uri: Uri, name: String): String {
        if (uri.authority == LinuxFilesProvider.authority(context)) {
            return GuestFiles.normalize(DocumentsContract.getDocumentId(uri))
        }
        val dir = files.hostFile(GUEST_DIR)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Could not create $GUEST_DIR")
        val target = freeTarget(dir, name.replace('/', '_').trim().ifEmpty { "attachment" })
        val partial = File(dir, ".${target.name}.part")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                partial.outputStream().use { input.copyTo(it) }
            } ?: throw IOException("Cannot read file")
            if (partial.length() == 0L) throw IOException("File is empty")
            if (!partial.renameTo(target)) throw IOException("Could not save ${target.name}")
        } finally {
            partial.delete()
        }
        LinuxFilesProvider.notifyChildrenChanged(context, GUEST_DIR)
        return "$GUEST_DIR/${target.name}"
    }

    /** "name.ext", else "name (1).ext", "name (2).ext"… */
    private fun freeTarget(dir: File, fileName: String): File {
        val stem = fileName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "").let { if (it.isEmpty() || stem.isEmpty()) "" else ".$it" }
        val base = if (ext.isEmpty()) fileName else stem
        var candidate = File(dir, fileName)
        var n = 1
        while (candidate.exists()) candidate = File(dir, "$base (${n++})$ext")
        return candidate
    }

    companion object {
        /** Guest folder for files attached from outside the built-in Linux. */
        const val GUEST_DIR = "/root/Uploads"
    }
}
