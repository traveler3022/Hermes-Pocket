package com.hermes.android.ui.component

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.hermes.android.domain.files.ServerFiles
import okio.Path.Companion.toOkioPath

/**
 * Coil's way to an image on the Hermes server: fetched into [ServerFiles]' cache, then decoded
 * from there, so every place that shows one (chat, gallery, group chat, viewer) gets the
 * server sign-in, the Tailscale route, resumed transfers and the disk copy. Other URLs go on to
 * Coil's own fetchers.
 */
class ServerImageFetcher(private val url: String, private val files: ServerFiles) : Fetcher {

    override suspend fun fetch(): FetchResult = SourceResult(
        source = ImageSource(files.fetch(url).toOkioPath()),
        mimeType = null,
        dataSource = DataSource.DISK,
    )

    class Factory(private val files: ServerFiles) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val url = data.toString()
            return if (files.isServerFile(url)) ServerImageFetcher(url, files) else null
        }
    }
}
