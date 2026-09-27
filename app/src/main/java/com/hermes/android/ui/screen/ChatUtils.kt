package com.hermes.android.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
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
import com.hermes.android.runtime.linux.LinuxFilesProvider
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
import java.io.File

@Composable
internal fun highlightText(text: String, query: String): AnnotatedString {
    val matches = searchMatches(text, query)
    if (matches.isEmpty()) return AnnotatedString(text)
    val highlightColor = MaterialTheme.colorScheme.tertiary
    val highlightBg = MaterialTheme.colorScheme.tertiaryContainer
    return buildAnnotatedString {
        var start = 0
        for (match in matches) {
            append(text.substring(start, match.first))
            withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold, background = highlightBg)) {
                append(text.substring(match.first, match.last + 1))
            }
            start = match.last + 1
        }
        if (start < text.length) append(text.substring(start))
    }
}

/**
 * Where [query] occurs in [text], ignoring case, as ranges of [text] itself. Found in
 * text.lowercase() instead, the ranges cut the original at the wrong places whenever
 * lowercasing changed the length ("İ" becomes two chars) and threw out of bounds.
 */
internal fun searchMatches(text: String, query: String): List<IntRange> {
    if (query.isBlank()) return emptyList()
    val found = mutableListOf<IntRange>()
    var at = text.indexOf(query, 0, ignoreCase = true)
    while (at >= 0) {
        found += at until at + query.length
        at = text.indexOf(query, at + query.length, ignoreCase = true)
    }
    return found
}


/** Ordered list of real agent.reasoning_effort values — verified against
 *  hermes_constants.VALID_REASONING_EFFORTS in the actual gateway source
 *  ("minimal","low","medium","high","xhigh","max"), plus "none" (disabled). */
internal val reasoningLevels = listOf("none", "minimal", "low", "medium", "high", "xhigh", "max")

@Composable
internal fun reasoningLevelLabel(level: String): String = when (level) {
    "none" -> t("Off", "خاموش")
    "minimal" -> t("Minimal", "حداقلی")
    "low" -> t("Low", "کم")
    "medium" -> t("Medium", "متوسط")
    "high" -> t("High", "زیاد")
    "xhigh" -> t("Very High", "خیلی زیاد")
    "max" -> t("Max", "بیشینه")
    else -> level
}

/** WCAG 2.3.3 — respects the OS-level "Remove animations" / reduced-motion
 *  setting (exposed as the animator duration scale; 0 when the user has
 *  disabled animations in Android's accessibility settings) so continuous
 *  pulse/bounce effects don't run for users sensitive to persistent motion. */
@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

internal val codeBlockRegex = Regex("```[\\s\\S]*?```", RegexOption.MULTILINE)
internal fun openUrlExternally(context: Context, url: String) {
    try {
        context.startActivity(externalViewIntent(context, url))
    } catch (e: Exception) {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

/**
 * ACTION_VIEW for [url]. A file of the built-in Linux arrives as a file:// URL into this
 * app's private storage, which Android refuses to put in an intent (and the other app could
 * not read anyway), so "Open in browser" and "Play" always failed for it. It goes out as a
 * document of [LinuxFilesProvider] instead, with a read grant for that one file.
 */
internal fun externalViewIntent(context: Context, url: String): Intent {
    val uri = Uri.parse(url)
    val guestPath = uri.takeIf { it.scheme == "file" }?.path
        ?.let { guestPathIn(File(context.filesDir, "linux/rootfs").absolutePath, it) }
        ?: return Intent(Intent.ACTION_VIEW, uri)
    val document = DocumentsContract.buildDocumentUri(LinuxFilesProvider.authority(context), guestPath)
    val type = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(guestPath.substringAfterLast('.', "").lowercase())
        ?: "*/*"
    return Intent(Intent.ACTION_VIEW)
        .setDataAndType(document, type)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/** The guest path of [hostPath] when it lies inside [rootfs] (the built-in Linux), else null. */
internal fun guestPathIn(rootfs: String, hostPath: String): String? =
    hostPath.removePrefix(rootfs).takeIf { it != hostPath && it.startsWith("/") }
