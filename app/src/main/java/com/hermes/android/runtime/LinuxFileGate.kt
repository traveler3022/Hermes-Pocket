package com.hermes.android.runtime

import java.io.File

/**
 * The check a file of the built-in Linux passes on its way out to the phone (the Files app,
 * "Open with another app"): a program leaves only after the user confirms it, and a program
 * named like a photo or a document never leaves.
 */
interface LinuxFileGate {

    enum class Risk { SAFE, PROGRAM, DISGUISED }

    /** Why another app may not have [file] now, or null when it may. */
    fun heldBack(file: File, now: Long = System.currentTimeMillis()): Risk?

    /** The user confirmed [file]: other apps may read it for a while, as long as it is unchanged. */
    fun approve(file: File, now: Long = System.currentTimeMillis())
}
