package com.hermes.android.gateway

private val CREDENTIAL_PARAM = Regex("(token|key)=[^&\\s]+", RegexOption.IGNORE_CASE)

/**
 * A log line without credentials: the gateway token, and API keys sent as a query
 * parameter (the Gemini key check puts the key in the URL as `?key=`).
 */
internal fun redactCredentials(message: String): String = message.replace(CREDENTIAL_PARAM, "$1=REDACTED")
