package com.hermes.android.runtime

import android.net.Uri
import java.io.File

/**
 * The built-in Linux's files as the UI needs them: guest paths ("/root/notes.txt") in, and
 * files or documents of the Files provider out. Where the rootfs lives stays behind this.
 */
interface LinuxFileAccess {

    /** Authority of the Linux documents provider, to tell its URIs apart. */
    val documentsAuthority: String

    /** Hermes' home folder in the Files app. */
    val homeFolderUri: Uri

    /** The Linux root in the Files app. */
    val rootUri: Uri

    /** The guest path of [hostPath] when it lies inside the Linux, else null. */
    fun guestPathOf(hostPath: String): String?

    /** The regular file at [guestPath], its symlinks followed as the Linux sees them; else null. */
    fun regularFile(guestPath: String): File?

    /** [guestPath] as a document another app can be granted. */
    fun documentUri(guestPath: String): Uri

    /** True when [file] (canonical) lies inside the Linux. */
    fun contains(file: File): Boolean
}
