package com.hermes.android.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.hermes.android.ui.viewer.FileViewerActivity

/**
 * Plays the audio viewer's file so it keeps going with the screen off or the app in the
 * background. Media3 posts the playback notification (title, artwork, play/pause, seek) and
 * shows it on the lock screen; tapping it reopens the viewer on the same file. Headphones
 * unplugged pause it, and it yields audio focus to calls and other players.
 */
class AudioPlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        val mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                // A controller may send an item without its URI; the viewer puts it in mediaId.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
                    mediaItems.map { item ->
                        if (item.localConfiguration != null) item else item.buildUpon().setUri(item.mediaId).build()
                    }.toMutableList(),
                )
            })
            .build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let { mediaSession.setSessionActivity(viewerIntent(it)) }
            }
        })
        session = mediaSession
    }

    private fun viewerIntent(item: MediaItem): PendingIntent {
        val url = item.localConfiguration?.uri?.toString() ?: item.mediaId
        val name = item.mediaMetadata.title?.toString().orEmpty()
        val intent = FileViewerActivity.intent(this, url, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiped away from recents: keep playing if it is, otherwise go. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
