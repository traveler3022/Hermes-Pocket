package com.hermes.android.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hermes.android.R
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.icons.filled.ContentCopy

/**
 * The one look of a code block, wherever it is drawn: a reply's fenced code and the code
 * inside tool output, `/btw` answers and the other markdown. They used to be two
 * different cards (colour, corners, header).
 *
 * Always left to right, header included: in the Persian UI a short block hugged the
 * right edge and its language label and copy button swapped sides.
 */
@Composable
fun HermesCodeBlock(
    language: String,
    code: String,
    modifier: Modifier = Modifier,
    /** Null: no copy button (the block's text can still be selected). */
    onCopy: ((String) -> Unit)? = null,
    /** The chat search's query, marked as in the prose around the block. */
    searchQuery: String = "",
) {
    val background = MaterialTheme.colorScheme.surfaceContainerHighest
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val hit = SpanStyle(
        color = MaterialTheme.colorScheme.tertiary,
        fontWeight = FontWeight.Bold,
        background = MaterialTheme.colorScheme.tertiaryContainer,
    )
    val coloured = rememberHighlightedCode(code, language, background)
    val text = remember(coloured, searchQuery, hit) { coloured.withSearchHits(searchQuery, hit) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(HxRadius.sm))
                .background(background),
        ) {
            if (language.isNotBlank() || onCopy != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = language,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = muted,
                        modifier = Modifier.weight(1f),
                    )
                    if (onCopy != null) {
                        IconButton(onClick = { onCopy(code) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.copy_code),
                                modifier = Modifier.size(16.dp),
                                tint = muted,
                            )
                        }
                    }
                }
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    textDirection = TextDirection.Ltr,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp, top = if (language.isNotBlank() || onCopy != null) 2.dp else 10.dp),
            )
        }
    }
}
