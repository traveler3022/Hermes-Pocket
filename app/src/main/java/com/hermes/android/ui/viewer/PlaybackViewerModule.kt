package com.hermes.android.ui.viewer

import com.hermes.android.service.PlaybackViewer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The player's notification ([PlaybackViewer]) opens [FileViewerActivity]. */
@Module
@InstallIn(SingletonComponent::class)
object PlaybackViewerModule {
    @Provides
    fun playbackViewer(): PlaybackViewer =
        PlaybackViewer { context, url, name -> FileViewerActivity.intent(context, url, name) }
}
