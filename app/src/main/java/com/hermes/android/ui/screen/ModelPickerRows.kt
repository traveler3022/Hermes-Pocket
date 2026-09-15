package com.hermes.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ModelOption

// Rows shared by the pages of ModelPickerSheet.

internal fun providerLabel(model: ModelOption): String = model.providerName.ifBlank { model.provider }

@Composable
internal fun SheetHeader(title: String, onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = HxSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "بازگشت"))
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = if (onBack == null) HxSpace.sm else HxSpace.xs),
        )
    }
}

@Composable
internal fun SheetSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = HxSpace.sm, top = HxSpace.sm, bottom = HxSpace.xs),
    )
}

@Composable
internal fun SheetHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = HxSpace.sm, vertical = HxSpace.xs),
    )
}

@Composable
internal fun SelectedMark(selected: Boolean) {
    Icon(
        Icons.Default.Check,
        contentDescription = null,
        tint = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
    )
}

@Composable
internal fun NavRow(title: String, value: String?, highlighted: Boolean = false, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (highlighted) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value != null) {
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
internal fun ModelRow(
    model: ModelOption,
    subtitle: String?,
    selected: Boolean,
    starred: Boolean,
    starEnabled: Boolean,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(
                model.modelId,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
        },
        supportingContent = if (subtitle != null) {
            { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        leadingContent = { SelectedMark(selected) },
        trailingContent = {
            IconButton(onClick = onToggleStar, enabled = starEnabled) {
                Icon(
                    if (starred) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (starred) t("Unstar", "برداشتن ستاره") else t("Star", "ستاره زدن"),
                    tint = when {
                        starred -> MaterialTheme.colorScheme.primary
                        starEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    },
                )
            }
        },
    )
}
