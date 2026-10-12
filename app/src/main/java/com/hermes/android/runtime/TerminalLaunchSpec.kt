package com.hermes.android.runtime

/** How a terminal emulator starts an interactive shell of the built-in Linux. */
class TerminalLaunchSpec(
    val executable: String,
    val arguments: Array<String>,
    val environment: Array<String>,
    val workingDirectory: String,
)
