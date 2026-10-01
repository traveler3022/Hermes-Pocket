package com.hermes.android.service

import android.content.Context
import android.content.Intent

/**
 * The viewer a tap on the player's notification opens on the playing file. The viewer is in the
 * UI layer, which binds this, so [AudioPlaybackService] doesn't import it.
 */
fun interface PlaybackViewer {
    fun intent(context: Context, url: String, name: String): Intent
}
