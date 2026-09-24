package com.hermes.android.ui.viewmodel

import android.util.Base64
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Base64 (no-wrap) encoding for smuggling arbitrary values into embedded
 * python scripts that are sent through shell.exec.
 *
 * Used within string-interpolated Python source where the value crosses
 * Kotlin → HTML/JSON escape → base64 → Python decode boundaries, so it
 * MUST be clean ASCII-safe output. NO_WRAP avoids spurious newlines that
 * would break the Python one-liner.
 */
fun b64(s: String): String =
    Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

/**
 * Slugs are interpolated straight into Python source (via string template),
 * so they must never carry a quote, newline, or shell metacharacter.
 * Provider slugs are always `[a-z0-9._-]` in practice; strip anything else
 * as defense-in-depth (the value may originate from a hand-edited config.yaml
 * or a third-party mod).
 */
fun safeSlug(s: String): String =
    s.filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }

/**
 * shell.exec command that runs [script] with `python3 -`.
 *
 * The gateway's safety filter answers 4005 for both `python3 -c` ("script
 * execution via -e/-c flag") and `python3 - <<EOF` ("script execution via
 * heredoc"), so the script travels base64-encoded and is piped into
 * python's stdin instead.
 */
fun pythonStdinCommand(script: String): String =
    "printf %s '${b64(script)}' | base64 -d | python3 -"

/**
 * Run [script] on the server ([pythonStdinCommand] through shell.exec) and
 * return its stdout. Throws with stderr's last line when it exits non-zero,
 * so a failed write reports its cause instead of passing for success.
 */
suspend fun GatewayClient.execPython(script: String): String {
    val result = request(
        GatewayMethods.SHELL_EXEC,
        mapOf("command" to JsonPrimitive(pythonStdinCommand(script))),
    ) as? JsonObject
    val code = (result?.get("code") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: -1
    if (code != 0) {
        val stderr = (result?.get("stderr") as? JsonPrimitive)?.contentOrNull.orEmpty()
        throw IllegalStateException(stderr.lines().lastOrNull { it.isNotBlank() } ?: "exit $code")
    }
    return (result?.get("stdout") as? JsonPrimitive)?.contentOrNull.orEmpty()
}
