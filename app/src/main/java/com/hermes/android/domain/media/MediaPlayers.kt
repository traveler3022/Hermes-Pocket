package com.hermes.android.domain.media

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer

/** Players that read server files through the app's sign-in and media cache. */
interface MediaPlayers {

    /** An ExoPlayer builder whose player reads through the app's media sources. */
    fun playerBuilder(context: Context): ExoPlayer.Builder
}
