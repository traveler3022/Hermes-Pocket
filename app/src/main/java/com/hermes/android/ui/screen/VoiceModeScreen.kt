package com.hermes.android.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.hermes.android.R
import com.hermes.android.ui.icons.filled.Mic
import com.hermes.android.ui.icons.filled.Stop
import com.hermes.android.voice.VoiceStatus
import com.hermes.android.voice.VoiceUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * The hands-free voice conversation, full screen: a circle that breathes with the voice, what
 * Hermes heard and is saying, and Mute / Done / End. Talking over Hermes interrupts it.
 */
@Composable
internal fun VoiceModeScreen(
    state: StateFlow<VoiceUiState>,
    level: StateFlow<Float>,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onFinishTake: () -> Unit,
) {
    val context = LocalContext.current
    val ui by state.collectAsState()
    val mic by level.collectAsState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onStart() else onEnd()
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            onStart()
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // A spoken stop phrase ends the conversation: close with it (an error stays up to be read).
    var seenActive by remember { mutableStateOf(false) }
    LaunchedEffect(ui.status, ui.error) {
        if (ui.status != VoiceStatus.Off) seenActive = true else if (seenActive && ui.error == null) onEnd()
    }

    Dialog(
        onDismissRequest = onEnd,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = statusText(ui),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                val target = when (ui.status) {
                    VoiceStatus.Listening -> 1f + mic * 0.6f
                    VoiceStatus.Speaking -> 1.15f
                    VoiceStatus.Thinking, VoiceStatus.Transcribing -> 0.9f
                    VoiceStatus.Off -> 0.8f
                }
                val scale by animateFloatAsState(target, spring(stiffness = 300f), label = "voice-orb")
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(if (ui.muted) 0.8f else scale)
                        .clip(CircleShape)
                        .background(
                            if (ui.muted) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.primary.copy(alpha = if (ui.status == VoiceStatus.Speaking) 0.95f else 0.7f),
                        ),
                )
                Spacer(Modifier.height(32.dp))
                if (ui.speaking.isNotBlank()) {
                    Text(
                        text = ui.speaking,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (ui.heard.isNotBlank()) {
                    Text(
                        text = "«${ui.heard}»",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                val notes = listOfNotNull(
                    stringResource(R.string.voice_phone_voice_note).takeIf { ui.phoneVoice },
                    stringResource(R.string.voice_phone_listening_note).takeIf { ui.phoneListening },
                )
                notes.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    )
                }
                ui.error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
                if (ui.status == VoiceStatus.Listening && !ui.muted && !ui.phoneListening) {
                    TextButton(onClick = onFinishTake) { Text(stringResource(R.string.voice_done_talking)) }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledIconButton(
                        onClick = onToggleMute,
                        modifier = Modifier.size(64.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (ui.muted) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (ui.muted) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(
                            if (ui.muted) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = stringResource(if (ui.muted) R.string.voice_unmute else R.string.voice_mute),
                        )
                    }
                    FilledIconButton(
                        onClick = onEnd,
                        modifier = Modifier.size(64.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.voice_end))
                    }
                }
            }
        }
    }
}

@Composable
private fun statusText(ui: VoiceUiState): String = when {
    ui.muted -> stringResource(R.string.voice_status_muted)
    else -> when (ui.status) {
        VoiceStatus.Listening -> stringResource(R.string.voice_status_listening)
        VoiceStatus.Transcribing -> stringResource(R.string.voice_status_transcribing)
        VoiceStatus.Thinking -> stringResource(R.string.voice_status_thinking)
        VoiceStatus.Speaking -> stringResource(R.string.voice_status_speaking)
        VoiceStatus.Off -> stringResource(R.string.voice_status_off)
    }
}
