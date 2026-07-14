package com.hermes.android.ui.viewmodel

import android.util.Base64

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
