package com.hermes.android.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.R
import com.hermes.android.domain.files.SavedFile
import com.hermes.android.domain.files.SavedFileKind
import com.hermes.android.ui.component.FileKind
import com.hermes.android.ui.component.fileKindOf
import com.hermes.android.ui.design.HermesEmptyState
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.icons.filled.Description
import com.hermes.android.ui.icons.filled.MusicNote
import com.hermes.android.ui.icons.outlined.PermMedia
import com.hermes.android.ui.viewer.FileViewerActivity
import com.hermes.android.ui.viewmodel.MediaViewModel
import kotlinx.coroutines.launch

/**
 * Media (رسانه): what the user saved with Download, read from the phone's Download/Hermes.
 * Laid out like Telegram's shared media — photos and videos as a grid, music and files as
 * lists — one tab each. A tap opens the in-app viewer; a file it can't show goes to another app.
 *
 * Depends ONLY on [MediaViewModel].
 */
@Composable
fun MediaScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: MediaViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Back from the viewer or from a chat download: the folder may have changed.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    val tabs = MediaTab.entries
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    HermesScaffold(
        title = stringResource(R.string.media),
        onBack = onNavigateBack,
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = pager.currentPage) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pager.currentPage == index,
                        onClick = { scope.launch { pager.animateScrollToPage(index) } },
                        text = { Text(stringResource(tab.label), maxLines = 1) },
                    )
                }
            }
            when (val state = uiState) {
                MediaViewModel.UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is MediaViewModel.UiState.Failed -> HermesEmptyState(
                    icon = Icons.Outlined.PermMedia,
                    title = state.message,
                    modifier = Modifier.fillMaxSize(),
                    actionLabel = stringResource(R.string.retry),
                    onAction = viewModel::refresh,
                )
                is MediaViewModel.UiState.Loaded -> HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val tab = tabs[page]
                    val files = remember(state.files, tab) { state.files.filter { it.kind == tab.kind } }
                    when {
                        files.isEmpty() -> HermesEmptyState(
                            icon = tab.icon(),
                            title = stringResource(R.string.media_empty),
                            caption = stringResource(R.string.media_empty_caption),
                            modifier = Modifier.fillMaxSize(),
                        )
                        tab.grid -> MediaGrid(files, viewModel::thumbnail)
                        else -> MediaList(files)
                    }
                }
            }
        }
    }
}

private enum class MediaTab(val kind: SavedFileKind, val label: Int, val grid: Boolean) {
    Photos(SavedFileKind.Photo, R.string.photos, grid = true),
    Videos(SavedFileKind.Video, R.string.videos, grid = true),
    Music(SavedFileKind.Audio, R.string.music, grid = false),
    Files(SavedFileKind.File, R.string.files, grid = false),
}

private fun MediaTab.icon() = when (this) {
    MediaTab.Photos, MediaTab.Videos -> Icons.Outlined.PermMedia
    MediaTab.Music -> Icons.Filled.MusicNote
    MediaTab.Files -> Icons.Filled.Description
}

/** Photos and videos: square tiles, three across, a play mark on a video. */
@Composable
private fun MediaGrid(files: List<SavedFile>, thumbnail: suspend (String, Int) -> Bitmap?) {
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(files, key = { it.uri }) { file ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { openSaved(context, file) },
            ) {
                val image = rememberThumbnail(file.uri, thumbnail)
                if (image != null) {
                    Image(image, contentDescription = file.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (file.kind == SavedFileKind.Video) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        contentColor = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(36.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.padding(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberThumbnail(uri: String, load: suspend (String, Int) -> Bitmap?): ImageBitmap? {
    val sizePx = with(LocalDensity.current) { ThumbnailSize.roundToPx() }
    val image by produceState<ImageBitmap?>(null, uri, sizePx) {
        value = load(uri, sizePx)?.asImageBitmap()
    }
    return image
}

/** Music and files: a row each, as Telegram lists them — icon, name, size and day. */
@Composable
private fun MediaList(files: List<SavedFile>) {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(files, key = { it.uri }) { file ->
            ListItem(
                modifier = Modifier.animateItem().clickable { openSaved(context, file) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { FileBadge(file) },
                headlineContent = {
                    Text(
                        file.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                },
                supportingContent = {
                    Text(
                        "${Formatter.formatShortFileSize(context, file.sizeBytes)} · " +
                            DateUtils.formatDateTime(context, file.addedAtMillis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}

/** A round note for music; for any other file a rounded square with its extension. */
@Composable
private fun FileBadge(file: SavedFile) {
    if (file.kind == SavedFileKind.Audio) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.padding(10.dp))
        }
        return
    }
    val ext = file.name.substringAfterLast('.', "").take(4).uppercase()
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (ext.isEmpty()) {
            Icon(
                Icons.Filled.Description, contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        } else {
            Text(
                ext,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 1,
            )
        }
    }
}

/**
 * The in-app viewer for what it shows; anything else to another app, with a read grant for
 * this one file (Download/Hermes is this app's MediaStore entry, not readable by others as is).
 */
private fun openSaved(context: Context, file: SavedFile) {
    if (fileKindOf(file.name, file.mimeType) != FileKind.OTHER) {
        FileViewerActivity.open(context, file.uri, file.name)
        return
    }
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(file.uri), file.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.no_app_opens_file), Toast.LENGTH_SHORT).show()
    }
}

private val ThumbnailSize = 160.dp
