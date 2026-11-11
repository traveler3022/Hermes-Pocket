package com.hermes.android.ui.screen

import com.hermes.android.ui.design.HxRadius
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.res.stringResource
import com.hermes.android.R
import com.hermes.android.ui.icons.filled.ExpandLess
import com.hermes.android.ui.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.gestures.detectTapGestures
import android.widget.Toast
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.android.domain.diff.FileEditDiff
import com.hermes.android.ui.component.HermesMarkdown
import com.hermes.android.ui.design.HxIcons
import com.hermes.android.ui.design.HxStatus
import com.hermes.android.ui.design.hxSoftShadow
import com.hermes.android.ui.viewmodel.ChatMessage

@Composable
internal fun ToolCallCard(message: ChatMessage.ToolCall) {
    // Colours come from HxStatus so a running tool here and a running chat in
    // the drawer read as the same state.
    val failed = message.error != null
    val toolAccent = when {
        message.isRunning -> HxStatus.running
        failed -> HxStatus.error
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hxSoftShadow(radius = 10.dp, shape = RoundedCornerShape(HxRadius.md))
            .clip(RoundedCornerShape(HxRadius.md))
            .background(toolAccent.copy(alpha = 0.07f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (message.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = HxStatus.running,
                    )
                } else {
                    Icon(
                        imageVector = if (failed) HxIcons.CircleX else HxIcons.CircleCheck,
                        contentDescription = stringResource(if (failed) R.string.status_failed else R.string.status_done),
                        modifier = Modifier.size(14.dp),
                        tint = if (failed) HxStatus.error else HxStatus.done,
                    )
                }
                ToolTitle(
                    line = toolLine(message.toolName, message.preview, message.isRunning, failed),
                    failed = failed,
                    modifier = Modifier.weight(1f),
                )
                if (message.isRunning) {
                    Text(
                        text = stringResource(R.string.running_5),
                        style = MaterialTheme.typography.labelSmall,
                        color = HxStatus.running,
                    )
                }
                message.durationS?.let {
                    Text(
                        text = "${"%.1f".format(it)}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            message.argsText?.takeIf { it.isNotBlank() }?.let { args ->
                // A file edit's arguments read as the diff they make; anything else as they are.
                val edits = remember(message.toolName, args) { FileEditDiff.fromToolArgs(message.toolName, args) }
                // Auto-expand while the tool is running so the user can watch
                // what it's doing; once finished, they can open it on demand.
                var argsExpanded by remember { mutableStateOf(message.isRunning) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { argsExpanded = !argsExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(if (edits != null) R.string.changes else R.string.arguments),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (argsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedVisibility(visible = argsExpanded) {
                    if (edits != null) {
                        FileEditsView(edits)
                    } else {
                        Text(
                            text = args,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            message.resultText?.takeIf { it.isNotBlank() }?.let { result ->
                val isLongResult = result.length > 300
                var resultExpanded by remember { mutableStateOf(false) }
                val displayResult = if (isLongResult && !resultExpanded) {
                    result.take(300) + "\u2026"
                } else {
                    result
                }
                HermesMarkdown(
                    markdown = displayResult,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    // Tool output is shell and code text: `$HOME/$USER` is not a formula,
                    // and measuring one blocks the main thread (it ANR'd here).
                    math = false,
                )
                if (isLongResult) {
                    TextButton(
                        onClick = { resultExpanded = !resultExpanded },
                        modifier = Modifier.padding(top = 0.dp),
                    ) {
                        Icon(
                            imageVector = if (resultExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (resultExpanded) stringResource(R.string.collapse) else stringResource(R.string.show_more_2),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            message.error?.takeIf { it.isNotBlank() }?.let { err ->
                // The header's circle-x already says it failed; the text only
                // has to say why, without a second (emoji) cross in front.
                Text(
                    text = err.take(220),
                    style = MaterialTheme.typography.bodySmall,
                    color = HxStatus.error,
                )
            }
        }
    }
}

/**
 * "Read README.md": the verb in the label face, the subject in mono when it is a
 * path or a command, on one line. A long press copies the subject.
 */
@Composable
private fun ToolTitle(line: ToolLine, failed: Boolean, modifier: Modifier = Modifier) {
    val verb = line.verb?.let { stringResource(it) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copied = stringResource(R.string.copied)
    val subjectColor = MaterialTheme.colorScheme.onSurface
    val verbColor = if (failed) HxStatus.error else MaterialTheme.colorScheme.onSurfaceVariant
    val text = remember(verb, line, subjectColor, verbColor) {
        buildAnnotatedString {
            if (verb != null) {
                withStyle(SpanStyle(color = verbColor)) { append(verb) }
                append(' ')
            }
            val style = if (line.mono) SpanStyle(fontFamily = FontFamily.Monospace) else SpanStyle()
            withStyle(style.copy(color = subjectColor)) { append(line.subject) }
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.pointerInput(line.subject) {
            detectTapGestures(onLongPress = {
                clipboard.setText(AnnotatedString(line.subject))
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            })
        },
    )
}
