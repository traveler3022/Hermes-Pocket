package com.hermes.android.runtime.linux

import java.io.File

/**
 * Notifies the user when a program of the built-in Linux was held back from
 * being read by an external Android application until confirmed.
 */
fun interface FileGateNotifier {
    fun notifyHeldBack(file: File, risk: FileGate.Risk)
}
