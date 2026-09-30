/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 *
 * Modified for Hermes by traveler3022, 2026-09-30: org.telegram.ui.CachedMediaLayout (its pages,
 * CacheCell, SharedPhotoVideoCell2 in STYLE_CACHE, SharedDocumentCell in VIEW_TYPE_CACHE and the
 * document colours of AndroidUtilities.getThumbForNameOrMime) ported to Jetpack Compose. The
 * chats page lists folders here.
 */

package com.hermes.android.ui.screen

import android.media.ThumbnailUtils
import android.net.Uri
import android.text.format.DateUtils
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hermes.android.data.FileArea
import com.hermes.android.data.FoundFile
import com.hermes.android.data.FoundFolder
import com.hermes.android.data.FoundKind
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.icons.filled.Folder
import com.hermes.android.ui.icons.filled.MusicNote
import com.hermes.android.ui.viewer.FileViewerActivity
import com.hermes.android.ui.viewmodel.StorageTab
import com.hermes.android.ui.viewmodel.StorageUiState
import com.hermes.android.ui.viewmodel.tabs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Video frames for the media grid, kept while the page is open (ImageLoader's vthumb://). */
internal class VideoThumbs {
    private val cache = LruCache<String, ImageBitmap>(64)
    operator fun get(path: String): ImageBitmap? = cache.get(path)
    operator fun set(path: String, bitmap: ImageBitmap) {
        cache.put(path, bitmap)
    }
}

/** What the tabs call back into; the picking lives in the ViewModel. */
internal class FileTabActions(
    val onTab: (StorageTab) -> Unit,
    val onFolder: (FoundFolder) -> Unit,
    val onPick: (FoundFile) -> Unit,
)

/**
 * CachedMediaLayout under the Storage chart: a sticky row of the pages that have something in
 * them, then the chosen page's rows in the same list.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.storageFileTabs(state: StorageUiState, actions: FileTabActions, thumbs: VideoThumbs) {
    val files = state.files
    if (state.loadingFiles) {
        item(key = "files-loading") { FilesLoading() }
        return
    }
    if (files == null) return
    val tabs = files.tabs()
    if (tabs.isEmpty()) return
    stickyHeader(key = "tabs") { StorageTabsRow(tabs, state.tab, actions.onTab) }
    when (state.tab) {
        StorageTab.Folders -> items(files.folders, key = { "folder:" + it.path }) { folder ->
            FolderRow(
                folder = folder,
                checked = folder.path in state.pickedFolders,
                divider = folder !== files.folders.last(),
                onClick = { actions.onFolder(folder) },
            )
        }
        StorageTab.Media -> {
            val rows = files.media.chunked(3)
            items(rows.size, key = { "media:" + rows[it].first().path }) { index ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    rows[index].forEach { file ->
                        MediaCell(
                            file = file,
                            checked = file.path in state.picked,
                            thumbs = thumbs,
                            onPick = { actions.onPick(file) },
                            modifier = Modifier.weight(1f).padding(vertical = 1.dp),
                        )
                    }
                    repeat(3 - rows[index].size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        StorageTab.Files -> items(files.documents, key = { "file:" + it.path }) { file ->
            FileRow(file, checked = file.path in state.picked, divider = file !== files.documents.last(), onPick = { actions.onPick(file) })
        }
        StorageTab.Music -> items(files.music, key = { "music:" + it.path }) { file ->
            FileRow(file, checked = file.path in state.picked, divider = file !== files.music.last(), onPick = { actions.onPick(file) })
        }
    }
    item(key = "tabs-end") { Spacer(Modifier.height(24.dp)) }
}

@Composable
private fun tabTitle(tab: StorageTab): String = when (tab) {
    StorageTab.Folders -> t("Folders", "پوشه‌ها")
    StorageTab.Media -> t("Media", "رسانه")
    StorageTab.Files -> t("Files", "فایل‌ها")
    StorageTab.Music -> t("Music", "موسیقی")
}

/** ViewPagerFixed's tabs: the chosen page coloured and underlined. */
@Composable
private fun StorageTabsRow(tabs: List<StorageTab>, selected: StorageTab, onTab: (StorageTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val active = tab == selected
                val indicator by animateFloatAsState(if (active) 1f else 0f, tween(200), label = "tab")
                val color = lerp(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.primary, indicator)
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTab(tab) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tabTitle(tab), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .width(24.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = indicator)),
                    )
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun FilesLoading() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            t("Looking for files…", "در حال پیدا کردن فایل‌ها…"),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The label of a folder: its path under the area, or the area itself. */
@Composable
private fun folderTitle(folder: FoundFolder): String {
    if (folder.relative.isNotEmpty()) return folder.relative
    return when (folder.area) {
        FileArea.Home -> t("Home folder", "پوشهٔ خانه")
        FileArea.Temp -> t("Temporary files", "فایل‌های موقت")
        FileArea.HermesCache -> t("Hermes cache", "کش هرمس") + " · " + folder.guestPath.substringAfterLast('/')
    }
}

/** Telegram's avatar colours, picked the way a chat without a photo gets one. */
private val AvatarColors = listOf(
    Color(0xFFE56555), Color(0xFFF28C48), Color(0xFF8E85EE), Color(0xFF76C84D),
    Color(0xFF5FBED5), Color(0xFF549CDD), Color(0xFFF2749A),
)

/** CacheControlActivity.UserCell in a CacheCell: checkbox, avatar, name and where, size. */
@Composable
private fun FolderRow(folder: FoundFolder, checked: Boolean, divider: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(58.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) { PickBox(checked) }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(AvatarColors[(folder.path.hashCode() and Int.MAX_VALUE) % AvatarColors.size], CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(folderTitle(folder), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    folder.guestPath + " · " + t("${folder.count} files", "${folder.count} فایل"),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                formatFileSize(folder.size),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, end = 21.dp),
            )
        }
        if (divider) RowDivider()
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 98.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/** CacheCell's CheckBox2 (24dp): a ring, filled with the accent and ticked when picked. */
@Composable
private fun PickBox(checked: Boolean, ring: Color = MaterialTheme.colorScheme.onSurfaceVariant, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(if (checked) 1f else 0f, tween(200), label = "pick")
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        val r = size.minDimension / 2 - stroke / 2
        drawCircle(lerp(ring, accent, progress), radius = r, style = Stroke(stroke))
        if (progress > 0) {
            drawCircle(accent, radius = r * progress)
            val tick = Path().apply {
                moveTo(center.x - 5.dp.toPx(), center.y + 0.5.dp.toPx())
                lineTo(center.x - 1.5.dp.toPx(), center.y + 4.dp.toPx())
                lineTo(center.x + 5.dp.toPx(), center.y - 3.5.dp.toPx())
            }
            drawPath(tick, Color.White.copy(alpha = progress), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Opens a found file in the app's own viewer, as a tap in Telegram opens its PhotoViewer. */
@Composable
private fun rememberOpener(): (FoundFile) -> Unit {
    val context = LocalContext.current
    return remember(context) { { file: FoundFile -> FileViewerActivity.open(context, Uri.fromFile(File(file.path)).toString(), file.name) } }
}

/**
 * SharedPhotoVideoCell2, STYLE_CACHE: the picture or a video frame, its size at the bottom (with
 * a play mark for videos), a checkbox in the corner. A tap opens it, the box or a long press picks it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaCell(file: FoundFile, checked: Boolean, thumbs: VideoThumbs, onPick: () -> Unit, modifier: Modifier) {
    val open = rememberOpener()
    var menu by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .combinedClickable(onClick = { open(file) }, onLongClick = { menu = true }),
    ) {
        if (file.kind == FoundKind.Video) {
            val sizePx = with(LocalDensity.current) { 160.dp.roundToPx() }
            val frame by produceState(thumbs[file.path], file.path) {
                if (value == null) {
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            ThumbnailUtils.createVideoThumbnail(File(file.path), android.util.Size(sizePx, sizePx), null).asImageBitmap()
                        }.getOrNull()
                    }?.also { thumbs[file.path] = it }
                }
            }
            frame?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        } else {
            AsyncImage(model = File(file.path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (file.kind == FoundKind.Video) {
                Canvas(Modifier.size(8.dp)) {
                    drawPath(
                        Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, size.height / 2)
                            lineTo(0f, size.height)
                            close()
                        },
                        Color.White,
                    )
                }
                Spacer(Modifier.width(3.dp))
            }
            Text(formatFileSize(file.size), fontSize = 11.sp, color = Color.White)
        }
        PickBox(
            checked = checked,
            ring = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .clip(CircleShape)
                .clickable(onClick = onPick),
        )
        PickMenu(menu, checked, onDismiss = { menu = false }, onOpen = { open(file) }, onPick = onPick)
    }
}

/** The long-press popup of CachedMediaLayout: open the file, or pick it. */
@Composable
private fun PickMenu(expanded: Boolean, checked: Boolean, onDismiss: () -> Unit, onOpen: () -> Unit, onPick: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text(t("Open file", "باز کردن فایل")) }, onClick = { onDismiss(); onOpen() })
        DropdownMenuItem(text = { Text(if (checked) t("Deselect", "لغو انتخاب") else t("Select", "انتخاب")) }, onClick = { onDismiss(); onPick() })
    }
}

/** AndroidUtilities.getThumbForNameOrMime's four document colours: blue, green, red, yellow. */
private val DocumentColors = listOf(Color(0xFF4A95D6), Color(0xFF5FB568), Color(0xFFE05B55), Color(0xFFF2A93A))

private fun documentColor(name: String): Color {
    val lower = name.lowercase()
    val index = when {
        ".doc" in lower || ".txt" in lower || ".psd" in lower -> 0
        ".xls" in lower || ".csv" in lower -> 1
        ".pdf" in lower || ".ppt" in lower || ".key" in lower -> 2
        ".zip" in lower || ".rar" in lower || ".ai" in lower || ".mp3" in lower || ".mov" in lower || ".avi" in lower -> 3
        else -> {
            val ext = name.substringAfterLast('.', "")
            (if (ext.isNotEmpty()) ext[0] else name[0]).code % DocumentColors.size
        }
    }
    return DocumentColors[index]
}

/**
 * CacheCell around a SharedDocumentCell: checkbox, the extension on its colour (a music mark for
 * audio), name, folder and date, size. A tap picks it; a long press offers to open it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(file: FoundFile, checked: Boolean, divider: Boolean, onPick: () -> Unit) {
    val context = LocalContext.current
    val open = rememberOpener()
    var menu by remember { mutableStateOf(false) }
    val date = remember(file.modified) {
        DateUtils.formatDateTime(context, file.modified, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
    }
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = onPick, onLongClick = { menu = true })) {
        Row(
            modifier = Modifier.fillMaxWidth().height(62.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                PickBox(checked, modifier = Modifier.clip(CircleShape).clickable(onClick = onPick))
            }
            if (file.kind == FoundKind.Audio) {
                Box(
                    Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                }
            } else {
                Box(
                    Modifier.size(40.dp).background(documentColor(file.name), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        file.name.substringAfterLast('.', "").take(4).lowercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    file.guestDir + " · " + date,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                formatFileSize(file.size),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, end = 21.dp),
            )
        }
        if (divider) RowDivider()
        PickMenu(menu, checked, onDismiss = { menu = false }, onOpen = { open(file) }, onPick = onPick)
    }
}
