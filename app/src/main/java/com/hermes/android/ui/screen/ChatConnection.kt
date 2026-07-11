package com.hermes.android.ui.screen

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateInt
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.hermes.android.ui.component.ContentBlock
import com.hermes.android.ui.component.HermesMarkdown
import com.hermes.android.ui.component.parseContentBlocks
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.ChatConnectionState
import com.hermes.android.ui.viewmodel.ChatMessage
import com.hermes.android.ui.viewmodel.ChatViewModel
import com.hermes.android.ui.viewmodel.DrawerRenameState
import com.hermes.android.ui.viewmodel.InteractiveKind
import com.hermes.android.ui.viewmodel.PendingAttachment
import com.hermes.android.ui.viewmodel.SessionItem
import com.hermes.android.ui.viewmodel.SlashCommandSuggestion
import com.hermes.android.ui.viewmodel.TodoItemUi
import com.hermes.android.ui.viewmodel.TodoStatus
import kotlinx.coroutines.launch

@Composable
internal fun ConnectionIndicator(state: ChatConnectionState) {
    val (color, label) = when (state) {
        ChatConnectionState.Connected -> MaterialTheme.colorScheme.primary to t("● Connected", "● متصل")
        ChatConnectionState.Connecting -> MaterialTheme.colorScheme.tertiary to t("◌ Connecting...", "◌ در حال اتصال...")
        ChatConnectionState.Reconnecting -> MaterialTheme.colorScheme.tertiary to t("↻ Reconnecting...", "↻ اتصال دوباره...")
        ChatConnectionState.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant to t("○ Tap to Connect", "○ برای اتصال لمس کنید")
        ChatConnectionState.Failed -> MaterialTheme.colorScheme.error to t("✕ Connection Error", "✕ خطای اتصال")
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ── Agent task list (todos from tool.start / tool.complete) ──────────────

@Composable
internal fun ConnectionRetryBanner(
    state: ChatConnectionState,
    onRetry: () -> Unit,
) {
    val (title, subtitle) = when (state) {
        ChatConnectionState.Failed -> t("Connection Failed", "اتصال ناموفق") to
                t("Could not connect to Hermes gateway", "اتصال به گیت‌وی هرمس ممکن نیست")
        ChatConnectionState.Disconnected -> t("Disconnected", "قطع شده") to
                t("Not connected to Hermes gateway", "به گیت‌وی هرمس متصل نیست")
        else -> return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(t("Reconnect", "اتصال دوباره"))
            }
        }
    }
}

// ── Feature #32: Shimmer skeleton ────────────────────────────────────────

@Composable
internal fun ShimmerSkeleton() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_progress",
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        start = Offset(shimmerProgress - 300f, 0f),
        end = Offset(shimmerProgress, 0f),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Fake user message (right-aligned)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .height(48.dp)
                    .background(shimmerBrush, RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)),
            )
        }
        // Fake assistant message (left-aligned, tall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .height(80.dp)
                    .background(shimmerBrush, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)),
            )
        }
        // Fake user message
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 200.dp)
                    .height(40.dp)
                    .background(shimmerBrush, RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)),
            )
        }
        // Fake assistant message (left-aligned)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 350.dp)
                    .height(100.dp)
                    .background(shimmerBrush, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)),
            )
        }
    }
}

