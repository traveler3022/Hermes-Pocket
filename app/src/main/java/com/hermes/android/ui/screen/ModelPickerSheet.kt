package com.hermes.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ModelFavorite
import com.hermes.android.ui.viewmodel.ModelFavorites
import com.hermes.android.ui.viewmodel.ModelOption

private sealed interface ModelSheetPage {
    data object Main : ModelSheetPage
    data object Effort : ModelSheetPage
    data class Provider(val slug: String) : ModelSheetPage
}

// Providers with more models than this get a search box on their page.
private const val SEARCH_THRESHOLD = 12

/** Composer chip label: the model id without its vendor prefix ("anthropic/claude-x" → "claude-x"). */
internal fun shortModelName(model: String): String = model.substringAfterLast('/')

/**
 * Chat model sheet, opened from the composer's model chip: starred models
 * first, then reasoning effort and the providers as nested pages. Models are
 * only listed inside their provider — the full catalogue is hundreds of rows
 * and the same model id is often served by several providers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelPickerSheet(
    models: List<ModelOption>,
    favorites: List<ModelFavorite>,
    activeModel: String?,
    activeProvider: String?,
    reasoningLevel: String,
    isLoading: Boolean,
    onSelectModel: (ModelOption) -> Unit,
    onToggleFavorite: (ModelOption) -> Unit,
    onReasoningLevelChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf<ModelSheetPage>(ModelSheetPage.Main) }
    var query by remember { mutableStateOf("") }
    val byProvider = remember(models) { models.groupBy { it.provider } }
    // Same id under several providers: also match the provider, unless the
    // reported one isn't a slug in this list (then the id alone decides).
    val isActive: (ModelOption) -> Boolean = { model ->
        model.modelId == activeModel &&
            (activeProvider.isNullOrBlank() || activeProvider !in byProvider || model.provider == activeProvider)
    }
    fun open(next: ModelSheetPage) {
        query = ""
        page = next
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = HxSpace.screen, end = HxSpace.screen, bottom = HxSpace.xl),
        ) {
            when (val current = page) {
                ModelSheetPage.Main -> {
                    item { SheetHeader(t("Model", "مدل"), onBack = null) }
                    if (favorites.isEmpty()) {
                        item {
                            SheetHint(
                                t(
                                    "Star models inside a provider to pin them here (up to ${ModelFavorites.MAX}).",
                                    "داخل هر پرووایدر روی ستاره‌ی مدل بزن تا اینجا بیاد (تا ${ModelFavorites.MAX} تا).",
                                )
                            )
                        }
                    }
                    items(favorites, key = { "fav:${it.provider}/${it.modelId}" }) { favorite ->
                        val model = byProvider[favorite.provider]?.firstOrNull { it.modelId == favorite.modelId }
                            ?: ModelOption(favorite.provider, favorite.modelId, favorite.modelId, requiresApiKey = false)
                        ModelRow(
                            model = model,
                            subtitle = providerLabel(model),
                            selected = isActive(model),
                            starred = true,
                            starEnabled = true,
                            onClick = { onSelectModel(model) },
                            onToggleStar = { onToggleFavorite(model) },
                        )
                    }
                    item {
                        NavRow(t("Effort", "سطح استدلال"), reasoningLevelLabel(reasoningLevel)) {
                            open(ModelSheetPage.Effort)
                        }
                    }
                    item { SheetSection(t("Providers", "پرووایدرها")) }
                    when {
                        models.isEmpty() && isLoading -> item {
                            CircularProgressIndicator(modifier = Modifier.padding(HxSpace.sm))
                        }
                        models.isEmpty() -> item { SheetHint(t("No models loaded.", "مدلی بارگذاری نشد.")) }
                        else -> items(byProvider.keys.sorted(), key = { "provider:$it" }) { slug ->
                            val providerModels = byProvider.getValue(slug)
                            NavRow(
                                title = providerLabel(providerModels.first()),
                                value = providerModels.size.toString(),
                                highlighted = providerModels.any(isActive),
                            ) { open(ModelSheetPage.Provider(slug)) }
                        }
                    }
                }

                ModelSheetPage.Effort -> {
                    item { SheetHeader(t("Effort", "سطح استدلال")) { open(ModelSheetPage.Main) } }
                    items(reasoningLevels) { level ->
                        val selected = level == reasoningLevel
                        ListItem(
                            modifier = Modifier.clickable {
                                onReasoningLevelChange(level)
                                open(ModelSheetPage.Main)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = {
                                Text(
                                    reasoningLevelLabel(level),
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                )
                            },
                            leadingContent = { SelectedMark(selected) },
                        )
                    }
                }

                is ModelSheetPage.Provider -> {
                    val providerModels = byProvider[current.slug].orEmpty()
                    val shown = if (query.isBlank()) {
                        providerModels
                    } else {
                        providerModels.filter { it.modelId.contains(query, ignoreCase = true) }
                    }
                    item {
                        SheetHeader(providerModels.firstOrNull()?.let(::providerLabel) ?: current.slug) {
                            open(ModelSheetPage.Main)
                        }
                    }
                    if (providerModels.size > SEARCH_THRESHOLD) {
                        item {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                placeholder = { Text(t("Search models", "جستجوی مدل")) },
                                modifier = Modifier.fillMaxWidth().padding(bottom = HxSpace.xs),
                            )
                        }
                    }
                    if (favorites.size >= ModelFavorites.MAX) {
                        item {
                            SheetHint(
                                t(
                                    "${ModelFavorites.MAX} models starred — unstar one to add another.",
                                    "${ModelFavorites.MAX} مدل ستاره خورده — برای افزودن، یکی رو بردار.",
                                )
                            )
                        }
                    }
                    items(shown) { model ->
                        val starred = ModelFavorite(model.provider, model.modelId) in favorites
                        ModelRow(
                            model = model,
                            subtitle = null,
                            selected = isActive(model),
                            starred = starred,
                            starEnabled = starred || favorites.size < ModelFavorites.MAX,
                            onClick = { onSelectModel(model) },
                            onToggleStar = { onToggleFavorite(model) },
                        )
                    }
                }
            }
        }
    }
}
