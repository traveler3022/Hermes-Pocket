package com.hermes.android.runtime

import java.io.File

/**
 * Asks the user, in Hermes' own dialog, whether a program may go to the phone's Download
 * folder ([LinuxFileGate]). [name] is the name it is saved under. A disguised program is only
 * reported. True only when the user allowed it.
 */
fun interface FileGatePrompt {
    suspend fun allowed(file: File, name: String, risk: LinuxFileGate.Risk): Boolean
}
