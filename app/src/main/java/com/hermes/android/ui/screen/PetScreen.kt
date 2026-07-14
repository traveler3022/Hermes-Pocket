package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.ui.design.HermesEmptyState
import com.hermes.android.ui.design.HermesScaffold
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.PetViewModel

/**
 * Pet gallery — adopt / rename / remove / disable (pet.gallery/select/remove/
 * rename/disable). Depends ONLY on [PetViewModel].
 */
@Composable
fun PetScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: PetViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var renameTarget by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    HermesScaffold(
        title = t("Pet", "پت"),
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        actions = {
            if (uiState.enabled) {
                Switch(
                    checked = true,
                    onCheckedChange = { if (!it) viewModel.disable() },
                    enabled = !uiState.isBusy,
                )
            }
        },
    ) { padding ->
        when {
            uiState.isLoading && uiState.pets.isEmpty() -> Column(
                modifier = Modifier.padding(padding).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            uiState.pets.isEmpty() -> Column(modifier = Modifier.padding(padding)) {
                HermesEmptyState(
                    icon = Icons.Default.Pets,
                    title = t("No pets installed", "هیچ پتی نصب نیست"),
                    caption = t(
                        "Pets adopted from the desktop appear here too",
                        "پت‌هایی که از دسکتاپ گرفتی اینجا هم دیده می‌شن",
                    ),
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(HxSpace.screen, HxSpace.sm, HxSpace.screen, HxSpace.xl),
                verticalArrangement = Arrangement.spacedBy(HxSpace.sm),
                horizontalArrangement = Arrangement.spacedBy(HxSpace.sm),
            ) {
                items(uiState.pets, key = { it.slug }) { pet ->
                    val isActive = pet.slug == uiState.activeSlug && uiState.enabled
                    Surface(
                        shape = RoundedCornerShape(HxRadius.md),
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.select(pet.slug) },
                    ) {
                        Column(
                            modifier = Modifier.padding(HxSpace.md),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier.size(72.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                val bmp = uiState.thumbnails[pet.slug]
                                if (bmp != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = pet.displayName,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Icon(Icons.Default.Pets, contentDescription = null)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isActive) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Text(
                                    pet.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (pet.installed) {
                                Row {
                                    TextButton(onClick = { renameTarget = pet.slug }) {
                                        Text(t("Rename", "تغییر نام"), style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(onClick = { viewModel.remove(pet.slug) }, enabled = !uiState.isBusy) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = t("Remove", "حذف"),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        renameTarget?.let { slug ->
            var name by remember(slug) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text(t("Rename pet", "تغییر نام پت")) },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(t("Name", "نام")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.rename(slug, name)
                            renameTarget = null
                        },
                        enabled = name.isNotBlank(),
                    ) { Text(t("Save", "ذخیره")) }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) { Text(t("Cancel", "انصراف")) }
                },
            )
        }
    }
}
