package com.hermes.android.ui.screen

import android.os.SystemClock
import android.app.Activity
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.res.stringResource
import com.hermes.android.R
import com.hermes.android.ui.icons.filled.AttachFile
import com.hermes.android.ui.icons.filled.Mic
import com.hermes.android.ui.icons.filled.PhotoCamera
import com.hermes.android.ui.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.design.HxIcons
import com.hermes.android.ui.design.hxSoftShadow
import com.hermes.android.ui.viewmodel.PendingAttachment
import com.hermes.android.ui.viewmodel.SlashCommandSuggestion
import com.hermes.android.ui.i18n.SlashCommandDescriptions
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InputBar(
    text: String,
    isSending: Boolean,
    isAttaching: Boolean = false,
    pendingAttachments: List<PendingAttachment> = emptyList(),
    slashCommands: List<SlashCommandSuggestion>,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onSteer: () -> Unit = {},
    onAttachFiles: (List<Uri>) -> Unit = {},
    /** Images pasted into the box, with the pasted content that lends their read grant. */
    onPasteImages: (List<Uri>, Any) -> Unit = { _, _ -> },
    /** A new file for the camera to write its photo to; null when none could be made. */
    newCameraPhotoUri: () -> Uri? = { null },
    onRemoveAttachment: (PendingAttachment) -> Unit = {},
    activeModel: String? = null,
    onModelClick: () -> Unit = {},
    // Set when the assistant gesture opened the chat: start voice input once.
    voiceRequest: Any? = null,
    onVoiceRequestTaken: () -> Unit = {},
) {
    // Several files at once; each goes the way a single one always did.
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris -> if (uris.isNotEmpty()) onAttachFiles(uris) }
    // The camera app writes into a file of ours (no CAMERA permission: the app declares none,
    // so TakePicture needs none). Saved, as the app can be killed while the camera is open.
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val uri = cameraUri
        cameraUri = null
        if (saved && uri != null) onAttachFiles(listOf(uri))
    }
    var attachMenuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    // The "add to current task / stop and send" choice while a turn is running.
    var sendChoiceOpen by remember { mutableStateOf(false) }
    // A turn that ends while the menu is open leaves nothing to choose between.
    LaunchedEffect(isSending) { if (!isSending) sendChoiceOpen = false }
    // Voice input: Android's own recognizer (the system's speech dialog), so no
    // microphone permission here. No language is asked for: the recognizer uses the
    // one the user set on the phone. What was said lands in the box to check before sending.
    val context = LocalContext.current
    val speechPrompt = stringResource(R.string.speak)
    val noSpeech = stringResource(R.string.speech_recognition_isnt_available_on)
    val noCamera = stringResource(R.string.attach_no_camera)
    val takePhoto = {
        newCameraPhotoUri()?.let { uri ->
            cameraUri = uri
            try {
                camera.launch(uri)
            } catch (e: ActivityNotFoundException) {
                cameraUri = null
                Toast.makeText(context, noCamera, Toast.LENGTH_LONG).show()
            }
        }
        Unit
    }
    // The box as a TextFieldState, as only that kind of field takes pasted images
    // (Modifier.contentReceiver); [text] stays the truth both ways.
    // [lastSynced] is the last text both sides agreed on: a [text] equal to it is only our own
    // typing coming back (maybe a keystroke late), and must not overwrite the box.
    // [recentTyped]: what was typed in the last moment, with when. A [text] that is one of
    // them is a late echo of a keystroke already overtaken (typing "ab" fast can bring back
    // "a"), not a change from outside, so it must not take the box back a step.
    val fieldState = rememberTextFieldState(text)
    var lastSynced by remember { mutableStateOf(text) }
    val recentTyped = remember { ArrayDeque<Pair<String, Long>>() }
    val currentOnTextChange by rememberUpdatedState(onTextChange)
    LaunchedEffect(fieldState, text) {
        val now = SystemClock.uptimeMillis()
        while (recentTyped.isNotEmpty() && now - recentTyped.first().second > ECHO_WINDOW_MS) {
            recentTyped.removeFirst()
        }
        val lateEcho = recentTyped.any { it.first == text }
        if (!lateEcho && text != lastSynced && fieldState.text.toString() != text) {
            fieldState.setTextAndPlaceCursorAtEnd(text)
            recentTyped.clear()
        }
        if (!lateEcho) lastSynced = text
    }
    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }.collect { typed ->
            if (typed != lastSynced) {
                lastSynced = typed
                recentTyped.addLast(typed to SystemClock.uptimeMillis())
                if (recentTyped.size > 32) recentTyped.removeFirst()
                currentOnTextChange(typed)
            }
        }
    }
    val currentOnPasteImages by rememberUpdatedState(onPasteImages)
    val imageReceiver = remember(context) { pastedImageReceiver(context) { uris, grant -> currentOnPasteImages(uris, grant) } }
    val voiceInput = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrEmpty()) {
            onTextChange(if (text.isBlank()) spoken else text.trimEnd() + " " + spoken)
        }
    }
    val startVoiceInput = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, speechPrompt)
        try {
            voiceInput.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, noSpeech, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(voiceRequest) {
        if (voiceRequest != null) {
            onVoiceRequestTaken()
            startVoiceInput()
        }
    }
    // `/` list: the gateway catalog (commands.catalog) minus the commands the app has
    // a button for; a short built-in list until the catalog arrives.
    val fallbackCommands = remember {
        listOf(
            "/help" to "Show available commands",
            "/btw" to "Ask a side question without interrupting",
            "/bg" to "Run a prompt in a separate background session",
            "/goal" to "Set a standing goal Hermes works on across turns",
            "/status" to "Show session, model, token, and context info",
        ).map { (command, description) -> SlashCommandSuggestion(command, description) }
    }
    val commandList = slashCommands.ifEmpty { fallbackCommands }
    // Hermes describes its commands in English only; Persian comes from the app.
    val persianDescriptions = stringResource(R.string.ui_language) == "fa"
    // Only while the command name is being typed: past the first space it is the argument.
    val showSuggestions = text.startsWith("/") && text.none { it == ' ' || it == '\n' } && !isSending
    val suggestions = remember(text, commandList) {
        commandList.distinctBy { it.command }.filter { it.command.startsWith(text, ignoreCase = true) }
    }

    // Scaffold's own content padding (in ChatScreen.kt) already reserves
    // the navigation-bar inset — adding navigationBarsPadding() again here
    // double-counted it, leaving a dead gap between the pill and the true
    // bottom edge. imePadding() stays: that inset is NOT part of Scaffold's
    // systemBars content window insets.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
    ) {
        if (showSuggestions && suggestions.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .heightIn(max = 260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                items(suggestions, key = { it.command }) { cmd ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTextChange(cmd.command + " ") }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            cmd.command,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (cmd.description.isNotBlank()) {
                            Text(
                                SlashCommandDescriptions.describe(cmd.command, cmd.description, persianDescriptions),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        // Staged attachments — already uploaded to the gateway, sent with the
        // next prompt. Tap a chip to remove it.
        if (pendingAttachments.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(pendingAttachments) { attachment ->
                    // A real trailing icon for "remove", not a ✕ typed into the label.
                    InputChip(
                        selected = false,
                        onClick = { onRemoveAttachment(attachment) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        label = { Text(attachment.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = {
                            Icon(
                                HxIcons.X,
                                contentDescription = stringResource(R.string.remove_attachment),
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }
        // Claude/ChatGPT-style composer: one rounded surface, the text on top
        // and an action row under it — attach and the model button on the
        // start side, send/steer/stop on the end. Model and effort live in the
        // model button's sheet; the button stays quiet so the conversation,
        // not the controls, carries the visual weight.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .hxSoftShadow(radius = 10.dp, shape = RoundedCornerShape(26.dp))
                .clip(RoundedCornerShape(26.dp))
                .background(
                    // Aether's composer card is its plain surface.
                    if (com.hermes.android.ui.theme.LocalAetherStyle.current) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.surfaceVariant,
                )
                .padding(4.dp),
        ) {
            TextField(
                state = fieldState,
                modifier = Modifier
                    .fillMaxWidth()
                    .contentReceiver(imageReceiver),
                placeholder = { Text(stringResource(R.string.ask_hermes)) },
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box {
                    IconButton(
                        onClick = { attachMenuOpen = true },
                        enabled = !isAttaching,
                        modifier = Modifier.size(40.dp),
                    ) {
                        if (isAttaching) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.attach_file),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    DropdownMenu(expanded = attachMenuOpen, onDismissRequest = { attachMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.attach_files)) },
                            leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null) },
                            onClick = {
                                attachMenuOpen = false
                                filePicker.launch("*/*")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.attach_take_photo)) },
                            leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                            onClick = {
                                attachMenuOpen = false
                                takePhoto()
                            },
                        )
                    }
                }
                ComposerModelButton(activeModel = activeModel, onClick = onModelClick)
                Spacer(Modifier.weight(1f))
                if (isSending) {
                    // Mid-turn the agent is running. Two DIFFERENT things can be
                    // meant by "send while it's replying", and neither replaces
                    // the other:
                    //  - Steer (session.steer): folds a note into the CURRENT
                    //    turn without interrupting it — but verified against
                    //    Hermes' own docs, the text only actually lands "after
                    //    the next tool call" (appended to a tool result). For a
                    //    turn with no tool calls (a plain text answer), it just
                    //    queues and never gets delivered — steer alone can look
                    //    completely broken for ordinary chatty replies.
                    //  - A normal Send: submits as a new prompt. The gateway's
                    //    prompt.submit handler explicitly does NOT reject this
                    //    mid-turn — it queues it and interrupts the live turn
                    //    (_handle_busy_submit), i.e. exactly "send a message
                    //    that cuts in", which is what most users expect from a
                    //    chat app. This used to be unreachable: the button was
                    //    swapped out entirely while isSending.
                    // Stop (full interrupt, no follow-up) stays available too.
                    //
                    // These used to be three bare icons side by side — steer, send,
                    // stop — and the difference between the first two lived only
                    // in their contentDescription. Now there is one Send; tapping it
                    // asks which of the two the user means, each with a line saying
                    // what it does. Stop keeps its place at the end so it never
                    // moves under a thumb when Send appears beside it.
                    if (text.isNotBlank()) {
                        Box {
                            IconButton(
                                onClick = { sendChoiceOpen = true },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    HxIcons.ArrowUp,
                                    contentDescription = stringResource(R.string.send_options),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            DropdownMenu(
                                expanded = sendChoiceOpen,
                                onDismissRequest = { sendChoiceOpen = false },
                            ) {
                                SendChoiceItem(
                                    icon = HxIcons.CornerDownRight,
                                    title = stringResource(R.string.steer_add_title),
                                    hint = stringResource(R.string.steer_add_hint),
                                    onClick = {
                                        sendChoiceOpen = false
                                        onSteer()
                                    },
                                )
                                SendChoiceItem(
                                    icon = Icons.AutoMirrored.Filled.Send,
                                    title = stringResource(R.string.interrupt_send_title),
                                    hint = stringResource(R.string.interrupt_send_hint),
                                    onClick = {
                                        sendChoiceOpen = false
                                        onSend()
                                    },
                                )
                            }
                        }
                    }
                    // Stop sits in the same blue disc as Send, a white square in
                    // place of the arrow: one button that changes its sign, not
                    // a red control that reads as an error.
                    ComposerDiscButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStop()
                        },
                        contentDescription = stringResource(R.string.stop),
                        filled = true,
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White,
                        )
                    }
                } else {
                    // One slot, one button: the microphone while the box is empty,
                    // the arrow once there is something to send. An attachment on
                    // its own is sendable (sendMessage() handles it), so a picked
                    // file switches to the arrow too. While a file is still
                    // uploading the arrow shows but waits, so nothing goes without it.
                    val canSend = text.isNotBlank() || pendingAttachments.isNotEmpty()
                    if (!canSend && !isAttaching) {
                        ComposerDiscButton(
                            onClick = startVoiceInput,
                            contentDescription = stringResource(R.string.voice_input),
                            filled = false,
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        ComposerDiscButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSend()
                            },
                            contentDescription = stringResource(R.string.send),
                            filled = true,
                            // Send is the one inverted disc, as in ChatGPT / Claude:
                            // white on a dark theme, black on a light one.
                            discColor = MaterialTheme.colorScheme.onSurface,
                            enabled = canSend && !isAttaching,
                        ) {
                            Icon(
                                HxIcons.ArrowUp,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.surface,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The composer's trailing button: a 48dp touch target around a 38dp disc.
 * Filled, the disc is [discColor] (primary unless told otherwise); unfilled, it is just
 * the icon (voice). Pressed, the disc shrinks a touch instead of a ripple
 * washing over the whole composer.
 */
@Composable
private fun ComposerDiscButton(
    onClick: () -> Unit,
    contentDescription: String,
    filled: Boolean,
    discColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.9f else 1f, label = "disc_press")
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (enabled) 1f else 0.4f
                }
                .clip(CircleShape)
                .then(if (filled) Modifier.background(discColor) else Modifier),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/** One row of the send-while-running menu: what it does, and a line on what that means. */
@Composable
private fun SendChoiceItem(
    icon: ImageVector,
    title: String,
    hint: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column(modifier = Modifier.widthIn(max = 260.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingIcon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        onClick = onClick,
    )
}

/**
 * Takes the images pasted into the composer, from the clipboard or a keyboard's image
 * (Gboard stickers, GIFs), and hands them to [onImages]; text and anything else stays for
 * the box. Drag and drop is left out: its grant works differently.
 * Adapted from Hy4ri/hermes-mobile (Apache-2.0).
 */
@OptIn(ExperimentalFoundationApi::class)
private fun pastedImageReceiver(
    context: Context,
    onImages: (List<Uri>, Any) -> Unit,
): ReceiveContentListener = object : ReceiveContentListener {
    override fun onReceive(transferableContent: TransferableContent): TransferableContent? {
        if (!transferableContent.hasMediaType(MediaType.Image)) return transferableContent
        if (transferableContent.source != TransferableContent.Source.Clipboard &&
            transferableContent.source != TransferableContent.Source.Keyboard
        ) {
            return transferableContent
        }
        val clip = transferableContent.clipEntry.clipData
        val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }.filter { uri ->
            uri.scheme == "content" && try {
                context.contentResolver.getType(uri)?.startsWith("image/", ignoreCase = true) == true
            } catch (e: RuntimeException) {
                false
            }
        }
        if (uris.isEmpty()) return transferableContent
        onImages(uris, transferableContent)
        val taken = uris.toSet()
        // An item with text as well keeps it for the box.
        return transferableContent.consume { it.uri in taken && it.text == null && it.htmlText == null }
    }
}

/** How long a typed value counts as possibly echoed back late from the view model. */
private const val ECHO_WINDOW_MS = 500L
