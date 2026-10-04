package com.hermes.android.runtime.linux

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the built-in Linux runs Hermes Kanban. Off unless the user turns it on: a
 * dispatcher then wakes every minute, and each task it starts is a whole Hermes process
 * on the phone.
 *
 * The switch is a file the gateway script looks for at start
 * ([ProotLinuxRuntime] `GATEWAY_SCRIPT`), so a change applies when the gateway restarts.
 */
@Singleton
class LinuxKanban @Inject constructor(
    private val environment: ProotEnvironment,
) {
    private val flag get() = environment.guestFile(FLAG_PATH)

    val enabled: Boolean get() = environment.isRootfsInstalled && flag.isFile

    fun setEnabled(on: Boolean) {
        if (on) {
            flag.parentFile?.mkdirs()
            flag.writeText("1\n")
        } else {
            flag.delete()
        }
    }

    companion object {
        const val FLAG_PATH = "/root/.hermes/android/kanban.enabled"
    }
}
