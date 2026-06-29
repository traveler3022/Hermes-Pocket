package com.hermes.android.ui.viewmodel

/** Converts a Unix epoch in seconds to milliseconds if value looks like seconds. */
internal fun normalizeEpochMillis(value: Long): Long =
    if (value in 1..999_999_999_999L) value * 1000L else value
