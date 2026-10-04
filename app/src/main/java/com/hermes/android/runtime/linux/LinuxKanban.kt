package com.hermes.android.runtime.linux

import com.hermes.android.runtime.KanbanSwitch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hermes Kanban on the built-in Linux, when the user turned Kanban on ([KanbanSwitch]): a
 * dispatcher then wakes every minute, and each task it starts is a whole Hermes process on
 * the phone.
 *
 * The gateway script looks for a flag file at start ([ProotLinuxRuntime] `GATEWAY_SCRIPT`);
 * [syncBeforeStart] writes it from the switch right before every start, so a change applies
 * with the next (re)start of the gateway.
 */
@Singleton
class LinuxKanban @Inject constructor(
    private val environment: ProotEnvironment,
    private val switch: KanbanSwitch,
) {
    fun syncBeforeStart() {
        if (!environment.isRootfsInstalled) return
        val flag = environment.guestFile(FLAG_PATH)
        if (switch.on.value) {
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
