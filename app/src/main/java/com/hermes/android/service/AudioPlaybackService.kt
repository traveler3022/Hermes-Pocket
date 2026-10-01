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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Plays the audio viewer's file so it keeps going with the screen off or the app in the
 * background. Media3 posts the playback notification (title, artwork, play/pause, seek) and
 * shows it on the lock screen; tapping it reopens the viewer on the same file. Headphones
 * unplugged pause it, and it yields audio focus to calls and other players.
 */
@AndroidEntryPoint
class AudioPlaybackService : MediaSessionService() {

    @Inject lateinit var playbackViewer: PlaybackViewer

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
                // Only the viewer adds items, and each carries its URI (a controller sends it along;
                // the id is [itemId]). The service is exported (media buttons, the system's
                // controls), and a file:// another app named here would be opened with this app's
                // rights and become the notification's link into the viewer.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> =
                    if (mayAddItems(controller.packageName, packageName, mediaItems)) {
                        Futures.immediateFuture(mediaItems)
                    } else {
                        Futures.immediateFailedFuture(UnsupportedOperationException("Only Hermes adds files to its player"))
                    }
            })
            .build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let { viewerIntent(it) }?.let { mediaSession.setSessionActivity(it) }
            }
        })
        session = mediaSession
    }

    private fun viewerIntent(item: MediaItem): PendingIntent? {
        val url = item.localConfiguration?.uri?.toString() ?: return null
        val name = item.mediaMetadata.title?.toString().orEmpty()
        val intent = playbackViewer.intent(this, url, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    companion object {
        /**
         * The player item's id for [url]. Any app may connect to this session with read access,
         * and that includes the current item's id (not its URI); a remote gateway's URL carries
         * its token, so the id is a digest of the URL, not the URL.
         */
        fun itemId(url: String): String =
            java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
                .joinToString("") { "%02x".format(it) }

        /** Whether [controllerPackage] may add [items]: only Hermes itself, each item with its URI. */
        fun mayAddItems(controllerPackage: String, ownPackage: String, items: List<MediaItem>): Boolean =
            controllerPackage == ownPackage && items.all { it.localConfiguration != null }
    }

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
