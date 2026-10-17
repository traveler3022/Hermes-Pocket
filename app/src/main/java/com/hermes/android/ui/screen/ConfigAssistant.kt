package com.hermes.android.ui.screen

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hermes.android.R
import com.hermes.android.ui.icons.filled.Mic
import androidx.compose.material.icons.Icons

/**
 * Hermes as the phone's digital assistant. An app can't ask for that role itself
 * (ROLE_ASSISTANT is not requestable), so this explains it and opens the system page
 * where the user picks it. Checked again on every return, since that is where it changes.
 */
@Composable
internal fun AssistantSettingCard() {
    val context = LocalContext.current
    var isAssistant by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        isAssistant = holdsAssistantRole(context)
        onPauseOrDispose { }
    }
    val noSettings = stringResource(R.string.assistant_settings_unavailable)
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.assistant_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(
                        if (isAssistant) R.string.assistant_is_default else R.string.assistant_not_default,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isAssistant) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringResource(R.string.assistant_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                if (!openAssistantSettings(context)) {
                    Toast.makeText(context, noSettings, Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.assistant_open_settings))
        }
    }
}

internal fun holdsAssistantRole(context: Context): Boolean {
    val roles = context.getSystemService(RoleManager::class.java) ?: return false
    return runCatching {
        roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && roles.isRoleHeld(RoleManager.ROLE_ASSISTANT)
    }.getOrDefault(false)
}

/**
 * The assist & voice input page holds "Digital assistant app" on most phones; some
 * ROMs have only the default-apps list, and the last resort is Settings itself.
 */
private fun openAssistantSettings(context: Context): Boolean =
    listOf(
        Settings.ACTION_VOICE_INPUT_SETTINGS,
        Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
        Settings.ACTION_SETTINGS,
    ).any { action ->
        try {
            context.startActivity(Intent(action))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
