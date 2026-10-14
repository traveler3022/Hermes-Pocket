package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.R
import com.hermes.android.ui.component.HermesMarkdown
import com.hermes.android.ui.viewmodel.WhatsNewViewModel

/** Release notes of the version just installed, once, over whatever screen is open. */
@Composable
fun WhatsNewDialog(viewModel: WhatsNewViewModel = hiltViewModel()) {
    val release by viewModel.release.collectAsStateWithLifecycle()
    val shown = release ?: return
    AlertDialog(
        onDismissRequest = viewModel::dismiss,
        title = { Text(stringResource(R.string.whats_new_in_version, shown.versionName)) },
        text = {
            HermesMarkdown(
                markdown = shown.notes,
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.whats_new_got_it)) }
        },
    )
}
