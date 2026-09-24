package com.hermes.android.ui.viewmodel

/** Converts a Unix epoch in seconds to milliseconds if value looks like seconds. */
internal fun normalizeEpochMillis(value: Long): Long =
    if (value in 1..999_999_999_999L) value * 1000L else value

/** An epoch the gateway sent as seconds (fraction allowed) or milliseconds, in milliseconds. */
internal fun epochMillisOf(value: Double): Long =
    if (value > 0 && value < 1_000_000_000_000.0) (value * 1000).toLong() else value.toLong()
