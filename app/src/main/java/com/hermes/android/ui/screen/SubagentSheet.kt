package com.hermes.android.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.android.R
import com.hermes.android.ui.design.HxRadius
import com.hermes.android.ui.design.HxSpace
import com.hermes.android.ui.viewmodel.SubagentViewModel

/**
 * One sub-agent of the open chat: its goal and what it is doing now, its live log, a box
 * to steer it (queued into its next step, the running tool is not cut) and Stop, which
 * ends this child only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubagentSheet(
    sheet: SubagentViewModel.Sheet,
    onSteer: (String) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    var steerText by rememberSaveable(sheet.subagentId) { mutableStateOf("") }
    val live = sheet.live
    val running = !sheet.ended
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = HxSpace.screen)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.sub_agent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                live?.goal?.ifBlank { null } ?: sheet.goal,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    sheet.ended -> stringResource(R.string.subagent_ended)
                    live == null -> stringResource(R.string.ops_loading)
                    else -> listOfNotNull(
                        live.status.ifBlank { null },
                        live.model.ifBlank { null },
                        stringResource(R.string.subagent_tools, live.toolCount),
                        live.lastTool.ifBlank { null }?.let { stringResource(R.string.subagent_last_tool, it) },
                    ).joinToString(" · ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (sheet.ended) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )

            Surface(
                shape = RoundedCornerShape(HxRadius.sm),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 380.dp),
            ) {
                val vertical = rememberScrollState()
                LaunchedEffect(sheet.log.length) { vertical.animateScrollTo(vertical.maxValue) }
                Box(modifier = Modifier.verticalScroll(vertical).horizontalScroll(rememberScrollState()).padding(12.dp)) {
                    SelectionContainer {
                        Text(
                            text = when {
                                sheet.log.isNotEmpty() -> (if (sheet.truncated) "…\n" else "") + sheet.log
                                sheet.logAvailable || !running -> stringResource(R.string.ops_no_output)
                                else -> stringResource(R.string.subagent_waiting_log)
                            },
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (running) {
                val canSteer = live?.acceptingSteer != false
                OutlinedTextField(
                    value = steerText,
                    onValueChange = { steerText = it },
                    enabled = canSteer && !sheet.steering,
                    label = { Text(stringResource(R.string.subagent_steer_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = {
                            onSteer(steerText)
                            steerText = ""
                        },
                        enabled = canSteer && steerText.isNotBlank() && !sheet.steering,
                    ) { Text(stringResource(R.string.subagent_steer)) }
                    OutlinedButton(onClick = onStop, enabled = !sheet.stopping) {
                        Text(stringResource(R.string.subagent_stop), color = MaterialTheme.colorScheme.error)
                    }
                    if (sheet.steering || sheet.stopping) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                if (!canSteer) {
                    Text(
                        stringResource(R.string.subagent_no_steer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
