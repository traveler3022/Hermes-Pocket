package com.hermes.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hermes.android.R
import com.hermes.android.domain.diff.DiffLineType
import com.hermes.android.domain.diff.DiffRow
import com.hermes.android.domain.diff.FileEdit
import com.hermes.android.domain.diff.FileEditKind
import com.hermes.android.domain.diff.cappedRows
import com.hermes.android.ui.design.HxDiff
import com.hermes.android.ui.design.HxRadius

/** Rows a file shows before "show the rest". */
internal const val DIFF_LINE_CAP = 40

/** The files a patch or write_file call edits, each as a coloured unified diff. */
@Composable
internal fun FileEditsView(edits: List<FileEdit>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        edits.forEach { FileEditBlock(it) }
    }
}

@Composable
private fun FileEditBlock(edit: FileEdit) {
    var showAll by rememberSaveable(edit.path, edit.rows.size) { mutableStateOf(false) }
    val added = HxDiff.added
    val removed = HxDiff.removed
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = edit.path,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            val kind = when (edit.kind) {
                FileEditKind.Added -> stringResource(R.string.diff_new_file)
                FileEditKind.Deleted -> stringResource(R.string.diff_deleted_file)
                FileEditKind.Moved -> stringResource(R.string.diff_moved_to, edit.newPath.orEmpty())
                FileEditKind.Modified -> null
            }
            kind?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1) }
            if (edit.additions > 0) Text("+${edit.additions}", style = MaterialTheme.typography.labelSmall, color = added)
            if (edit.deletions > 0) Text("−${edit.deletions}", style = MaterialTheme.typography.labelSmall, color = removed)
        }
        if (edit.rows.isNotEmpty()) {
            FileEditRows(edit, showAll, onShowAll = { showAll = true })
        }
    }
}

@Composable
private fun FileEditRows(edit: FileEdit, showAll: Boolean, onShowAll: () -> Unit) {
    val added = HxDiff.added
    val removed = HxDiff.removed
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val (rows, hidden) = remember(edit, showAll) {
            if (showAll) edit.rows to 0 else cappedRows(edit.rows, DIFF_LINE_CAP)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(HxRadius.sm))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        ) {
            rows.forEach { row ->
                when (row) {
                    is DiffRow.Line -> DiffLineRow(row, added, removed)
                    is DiffRow.Gap -> Text(
                        text = row.hint?.let { "⋯ $it" } ?: "⋯",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
        if (hidden > 0) {
            TextButton(onClick = onShowAll) {
                Text(stringResource(R.string.diff_show_rest, hidden), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun DiffLineRow(line: DiffRow.Line, added: Color, removed: Color) {
    val tint = when (line.type) {
        DiffLineType.Add -> added
        DiffLineType.Del -> removed
        DiffLineType.Ctx -> null
    }
    val text = remember(line, tint) { diffLineText(line, tint) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .background(tint?.copy(alpha = 0.12f) ?: Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

/** "+ text" with the changed words on a stronger tint of the line's colour. */
private fun diffLineText(line: DiffRow.Line, tint: Color?): AnnotatedString = buildAnnotatedString {
    append(
        when (line.type) {
            DiffLineType.Add -> "+ "
            DiffLineType.Del -> "- "
            DiffLineType.Ctx -> "  "
        },
    )
    val text = line.text
    if (tint == null || line.emphasis.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    var pos = 0
    for (range in line.emphasis) {
        val start = range.start.coerceIn(pos, text.length)
        val end = range.end.coerceIn(start, text.length)
        append(text.substring(pos, start))
        withStyle(SpanStyle(background = tint.copy(alpha = 0.3f))) { append(text.substring(start, end)) }
        pos = end
    }
    append(text.substring(pos))
}
