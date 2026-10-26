package com.hermes.android.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.R
import com.hermes.android.domain.storage.SpaceItem
import com.hermes.android.domain.storage.SpaceOverview
import com.hermes.android.domain.storage.SpacePlace
import com.hermes.android.domain.storage.SpaceVerdict
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.icons.filled.Description
import com.hermes.android.ui.icons.filled.Folder
import com.hermes.android.ui.icons.filled.Security
import com.hermes.android.ui.viewmodel.SpaceLevel
import com.hermes.android.ui.viewmodel.StorageSpaceEvent
import com.hermes.android.ui.viewmodel.StorageSpaceViewModel

private val SafeColor = Color(0xFF61C752)
private val CarefulColor = Color(0xFFEBA52D)
private val NeededColor = Color(0xFFE05356)

private fun verdictColor(verdict: SpaceVerdict): Color = when (verdict) {
    SpaceVerdict.Safe -> SafeColor
    SpaceVerdict.Careful -> CarefulColor
    SpaceVerdict.Needed -> NeededColor
}

@Composable
private fun verdictTitle(verdict: SpaceVerdict): String = when (verdict) {
    SpaceVerdict.Safe -> stringResource(R.string.space_safe)
    SpaceVerdict.Careful -> stringResource(R.string.space_careful)
    SpaceVerdict.Needed -> stringResource(R.string.space_needed)
}

@Composable
private fun placeTitle(place: SpacePlace): String = when (place) {
    SpacePlace.YourFiles -> stringResource(R.string.space_place_your_files)
    SpacePlace.Uploads -> stringResource(R.string.space_place_uploads)
    SpacePlace.HermesMade -> stringResource(R.string.space_place_hermes_made)
    SpacePlace.Caches -> stringResource(R.string.space_place_caches)
    SpacePlace.Temp -> stringResource(R.string.space_place_temp)
    SpacePlace.Installed -> stringResource(R.string.space_place_installed)
    SpacePlace.Backups -> stringResource(R.string.space_place_backups)
    SpacePlace.Hermes -> stringResource(R.string.space_place_hermes)
    SpacePlace.HermesData -> stringResource(R.string.space_place_hermes_data)
    SpacePlace.System -> stringResource(R.string.space_place_system)
}

@Composable
private fun placeDescription(place: SpacePlace): String = when (place) {
    SpacePlace.YourFiles -> stringResource(R.string.space_place_your_files_desc)
    SpacePlace.Uploads -> stringResource(R.string.space_place_uploads_desc)
    SpacePlace.HermesMade -> stringResource(R.string.space_place_hermes_made_desc)
    SpacePlace.Caches -> stringResource(R.string.space_place_caches_desc)
    SpacePlace.Temp -> stringResource(R.string.space_place_temp_desc)
    SpacePlace.Installed -> stringResource(R.string.space_place_installed_desc)
    SpacePlace.Backups -> stringResource(R.string.space_place_backups_desc)
    SpacePlace.Hermes -> stringResource(R.string.space_place_hermes_desc)
    SpacePlace.HermesData -> stringResource(R.string.space_place_hermes_data_desc)
    SpacePlace.System -> stringResource(R.string.space_place_system_desc)
}

/** A line about what a well-known folder is, or null. */
@Composable
private fun knownHint(item: SpaceItem): String? = when (item.name) {
    "huggingface", "torch", "whisper", "models" -> stringResource(R.string.space_hint_models)
    "ms-playwright", "ms-playwright-go", "chromium", "google-chrome", "puppeteer" -> stringResource(R.string.space_hint_browsers)
    "pip", "uv", "pypoetry" -> stringResource(R.string.space_hint_python)
    "node_modules", "_cacache", "npm", "pnpm", "yarn" -> stringResource(R.string.space_hint_node)
    "venv", ".venv", "site-packages" -> stringResource(R.string.space_hint_python_env)
    else -> null
}

/**
 * "Where the space goes": every place of the built-in Linux with its size and whether it may go,
 * then its folders, biggest first, down to single files. What Hermes needs is shown but locked.
 */
@Composable
fun StorageSpaceScreen(
    onNavigateBack: () -> Unit,
    viewModel: StorageSpaceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    val picking = state.picked.isNotEmpty()

    BackHandler(enabled = picking || state.levels.size > 1) {
        if (picking) viewModel.clearPicks() else viewModel.back()
    }

    val freed = stringResource(R.string.s_freed_on_your_phone)
    val busy = stringResource(R.string.hermes_is_installing_or_updating_clear)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StorageSpaceEvent.Deleted -> snackbarHostState.showSnackbar(freed.format(formatFileSize(event.bytes)))
                StorageSpaceEvent.Busy -> snackbarHostState.showSnackbar(busy)
            }
        }
    }

    val level = state.level
    val title = when {
        picking -> stringResource(R.string.count_selected_2, state.picked.size.toString())
        level is SpaceLevel.Place -> placeTitle(level.place)
        level is SpaceLevel.Folder -> level.name
        else -> stringResource(R.string.space_where_it_goes)
    }
    val subtitle = when {
        picking -> formatFileSize(state.pickedBytes)
        level is SpaceLevel.Folder -> level.guestPath
        else -> null
    }

    HermesScaffold(
        title = title,
        subtitle = subtitle,
        onBack = {
            when {
                picking -> viewModel.clearPicks()
                !viewModel.back() -> onNavigateBack()
            }
        },
        actions = {
            if (picking) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        },
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            state.deleting?.let { progress ->
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            when {
                state.scanning && state.overview == null -> Scanning()
                level is SpaceLevel.Places -> PlacesList(state.overview, state.scanning, viewModel::openPlace)
                state.loadingItems -> Scanning()
                else -> ItemsList(
                    place = (level as? SpaceLevel.Place)?.place ?: (level as SpaceLevel.Folder).place,
                    items = state.items,
                    picked = state.picked.keys,
                    onOpen = viewModel::openFolder,
                    onPick = viewModel::togglePick,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.space_delete_title, formatFileSize(state.pickedBytes))) },
            text = {
                Column {
                    Text(stringResource(R.string.space_delete_text, state.picked.size.toString()))
                    if (state.pickedCareful) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.space_delete_careful), color = CarefulColor)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deletePicked()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel_2)) }
            },
        )
    }
}

@Composable
private fun Scanning() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.space_scanning), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
private fun PlacesList(overview: SpaceOverview?, scanning: Boolean, onOpen: (SpacePlace) -> Unit) {
    if (overview == null) {
        Text(
            stringResource(R.string.space_empty),
            modifier = Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "summary") { Summary(overview, scanning) }
        SpaceVerdict.entries.forEach { verdict ->
            val places = SpacePlace.entries
                .filter { it.verdict == verdict && (overview.sizes[it] ?: 0L) > 0 }
                .sortedByDescending { overview.sizes[it] ?: 0L }
            if (places.isEmpty()) return@forEach
            item(key = "head-$verdict") { VerdictHeader(verdict, overview.size(verdict)) }
            items(places, key = { it.name }) { place ->
                PlaceRow(place, overview.sizes[place] ?: 0L, onClick = { onOpen(place) })
            }
        }
    }
}

/** The total and one bar split into the three verdicts. */
@Composable
private fun Summary(overview: SpaceOverview, scanning: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 21.dp, vertical = 16.dp)) {
        Text(
            stringResource(R.string.space_total).format(formatFileSize(overview.total)),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (scanning) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
        val total = overview.total.coerceAtLeast(1).toFloat()
        Canvas(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
            var x = 0f
            SpaceVerdict.entries.forEach { verdict ->
                val w = size.width * overview.size(verdict) / total
                if (w > 0) drawRect(verdictColor(verdict), topLeft = Offset(x, 0f), size = Size(w, size.height))
                x += w
            }
        }
        Spacer(Modifier.height(10.dp))
        SpaceVerdict.entries.forEach { verdict ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Box(Modifier.size(10.dp).background(verdictColor(verdict), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(verdictTitle(verdict), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                Text(formatFileSize(overview.size(verdict)), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun VerdictHeader(verdict: SpaceVerdict, size: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 21.dp, end = 21.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            verdictTitle(verdict),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = verdictColor(verdict),
            modifier = Modifier.weight(1f),
        )
        Text(formatFileSize(size), fontSize = 14.sp, color = verdictColor(verdict))
    }
}

@Composable
private fun PlaceRow(place: SpacePlace, size: Long, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 21.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(12.dp).background(verdictColor(place.verdict), CircleShape))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(placeTitle(place), fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(placeDescription(place), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                formatFileSize(size),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 47.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun ItemsList(
    place: SpacePlace,
    items: List<SpaceItem>,
    picked: Set<String>,
    onOpen: (SpaceItem) -> Unit,
    onPick: (SpaceItem) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "about") {
            Text(
                placeDescription(place),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 21.dp, vertical = 12.dp),
            )
        }
        if (items.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.space_empty),
                    modifier = Modifier.padding(horizontal = 21.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(items, key = { it.guestPath }) { item ->
            // Inside a picked folder counts as picked.
            val checked = picked.any { item.guestPath == it || item.guestPath.startsWith("$it/") }
            ItemRow(item, checked, otherPlace = item.place != place, onOpen = { onOpen(item) }, onPick = { onPick(item) })
        }
    }
}

@Composable
private fun ItemRow(item: SpaceItem, checked: Boolean, otherPlace: Boolean, onOpen: () -> Unit, onPick: () -> Unit) {
    val hint = knownHint(item)
    val note = when {
        !item.deletable -> stringResource(R.string.space_needed_locked)
        otherPlace -> placeTitle(item.place)
        else -> null
    }
    Column(
        Modifier.fillMaxWidth().clickable(onClick = if (item.isDirectory) onOpen else onPick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(62.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                if (item.deletable) {
                    PickCircle(checked, Modifier.clip(CircleShape).clickable(onClick = onPick))
                } else {
                    Icon(Icons.Default.Security, contentDescription = null, tint = NeededColor, modifier = Modifier.size(20.dp))
                }
            }
            Box(
                Modifier.size(38.dp).background(verdictColor(item.place.verdict).copy(alpha = 0.85f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(item.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                val line = listOfNotNull(hint, note).joinToString(" · ").ifEmpty { item.guestPath }
                Text(
                    line,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (!item.deletable) NeededColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                formatFileSize(item.size),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, end = 21.dp),
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 98.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun PickCircle(checked: Boolean, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        val r = size.minDimension / 2 - stroke / 2
        if (checked) {
            drawCircle(accent, radius = r + stroke / 2)
            drawRoundRect(
                Color.White,
                topLeft = Offset(center.x - 3.dp.toPx(), center.y - 3.dp.toPx()),
                size = Size(6.dp.toPx(), 6.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        } else {
            drawCircle(ring, radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        }
    }
}
