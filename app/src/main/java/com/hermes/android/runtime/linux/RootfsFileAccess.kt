package com.hermes.android.runtime.linux

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.hermes.android.runtime.LinuxFileAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** [LinuxFileAccess] on the proot rootfs and [LinuxFilesProvider]. */
@Singleton
class RootfsFileAccess @Inject constructor(
    @ApplicationContext private val context: Context,
) : LinuxFileAccess {

    private val rootfs: File get() = ProotEnvironment.rootfsDir(context)

    override val documentsAuthority: String get() = LinuxFilesProvider.authority(context)

    override val homeFolderUri: Uri get() = DocumentsContract.buildDocumentUri(documentsAuthority, HERMES_HOME_DIR)

    override val rootUri: Uri get() = DocumentsContract.buildRootUri(documentsAuthority, LinuxFilesProvider.ROOT_ID)

    override fun guestPathOf(hostPath: String): String? = guestPathIn(rootfs.absolutePath, hostPath)

    override fun regularFile(guestPath: String): File? =
        runCatching { GuestFiles(rootfs).hostFile(guestPath) }.getOrNull()?.takeIf { it.isFile }

    override fun documentUri(guestPath: String): Uri = DocumentsContract.buildDocumentUri(documentsAuthority, guestPath)

    override fun contains(file: File): Boolean = file.path.startsWith(rootfs.canonicalPath + File.separator)

    private companion object {
        /** Where Hermes works and leaves the files it makes: the gateway's working directory. */
        const val HERMES_HOME_DIR = "/root"
    }
}

/** The guest path of [hostPath] when it lies inside [rootfs] (the built-in Linux), else null. */
internal fun guestPathIn(rootfs: String, hostPath: String): String? =
    hostPath.removePrefix(rootfs).takeIf { it != hostPath && it.startsWith("/") }
