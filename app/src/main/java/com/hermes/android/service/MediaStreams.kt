package com.hermes.android.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.hermes.android.gateway.RemoteFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the app's players read from. A file of this disk or a content provider plays as before;
 * a file on the Hermes server streams through [RemoteFiles]' client (server sign-in, Tailscale
 * route, HTTP/1.1) with Range requests, so playback starts at once and seeks without the whole
 * file, as Hermes' desktop streams audio and video. What has played is kept on disk, so playing
 * it again or seeking back does not fetch it again.
 */
@Singleton
@OptIn(UnstableApi::class)
class MediaStreams @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: RemoteFiles,
) {
    // One SimpleCache per folder for the whole process (a second one on it throws).
    private val cache by lazy {
        SimpleCache(
            File(context.cacheDir, CACHE_DIR),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }

    private val dataSourceFactory by lazy {
        val server = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(files.client))
            // Keyed by the server and the file, never by a token the URL may carry.
            .setCacheKeyFactory { spec ->
                RemoteFiles.serverFileUrl(spec.uri.toString())?.let { RemoteFiles.cacheKey(it) }
                    ?: spec.key ?: spec.uri.toString()
            }
            // The cleaner may have emptied the folder under the cache: read past it.
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // DefaultDataSource keeps file:// and content:// on their own readers; http(s) goes to [server].
        DefaultDataSource.Factory(context, server)
    }

    /** An ExoPlayer builder whose player reads through these sources (its own source factory). */
    fun playerBuilder(context: Context): ExoPlayer.Builder =
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))

    private companion object {
        const val CACHE_DIR = "media-stream"
        const val MAX_CACHE_BYTES = 256L * 1024 * 1024
    }
}
