package com.hermes.android.ui.viewer

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.hermes.android.R
import com.hermes.android.runtime.LinuxFileAccess
import com.hermes.android.runtime.LinuxFileGate
import com.hermes.android.service.HermesNotifications
import com.hermes.android.ui.i18n.AppLanguageState
import com.hermes.android.ui.i18n.ProvideAppLanguage
import com.hermes.android.i18n.tForContext
import com.hermes.android.ui.theme.Hermes2Theme
import com.hermes.android.ui.theme.ThemeModeState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Asks before a program of the built-in Linux leaves for the phone ([LinuxFileGate]); a program
 * named like a photo or document is only reported. Opened by "Open with another app" (then
 * opens the file) and by the notification the Files provider posts when it held a file back.
 */
@AndroidEntryPoint
class FileGateActivity : ComponentActivity() {

    @Inject lateinit var fileGate: LinuxFileGate
    @Inject lateinit var linuxFiles: LinuxFileAccess

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path == null) {
            finish()
            return
        }
        val open = intent.getBooleanExtra(EXTRA_OPEN, false)
        // FileGate reads the file (a zip's whole index), so not on the main thread; the window
        // stays see-through until a dialog shows.
        lifecycleScope.launch {
            val (file, risk) = withContext(Dispatchers.IO) {
                val file = linuxFile(path)
                file to file?.let { fileGate.heldBack(it) }
            }
            when {
                // Gone or not a file: handed on all the same, and the other app says so.
                file == null -> if (open) startExternalView(this@FileGateActivity, Uri.fromFile(File(path)).toString(), linuxFiles)
                risk == null -> if (open) startExternalView(this@FileGateActivity, Uri.fromFile(file).toString(), linuxFiles)
                else -> {
                    ask(file, risk, open)
                    return@launch
                }
            }
            finish()
        }
    }

    private fun ask(file: File, risk: LinuxFileGate.Risk, open: Boolean) {
        val themeModeState = ThemeModeState(this)
        val appLanguageState = AppLanguageState(this)
        setContent {
            ProvideAppLanguage(appLanguageState.language) {
                Hermes2Theme(
                    themeMode = themeModeState.mode,
                    colorTheme = themeModeState.colorTheme,
                    warmMode = themeModeState.warmMode,
                    appFont = themeModeState.appFont,
                    fontScalePct = themeModeState.fontScalePct,
                ) {
                    if (risk == LinuxFileGate.Risk.DISGUISED) {
                        AlertDialog(
                            onDismissRequest = ::finish,
                            title = { Text(stringResource(R.string.blocked_a_disguised_program)) },
                            text = {
                                Text(
                                    stringResource(R.string.is_named_like_a_photo_or_document_but_it, file.name),
                                )
                            },
                            confirmButton = { TextButton(onClick = ::finish) { Text(stringResource(R.string.ok)) } },
                        )
                    } else {
                        AlertDialog(
                            onDismissRequest = ::finish,
                            title = { Text(stringResource(R.string.this_file_is_a_program)) },
                            text = {
                                Text(
                                    stringResource(R.string.may_have_come_from_the_internet_if_it_is, file.name),
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = { allow(file, open) }) {
                                    Text(
                                        if (open) stringResource(R.string.open_anyway) else stringResource(R.string.allow_for_10_minutes),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                            dismissButton = { TextButton(onClick = ::finish) { Text(stringResource(R.string.cancel)) } },
                        )
                    }
                }
            }
        }
    }

    private fun allow(file: File, open: Boolean) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { fileGate.approve(file) }
            if (open) {
                startExternalView(this@FileGateActivity, Uri.fromFile(file).toString(), linuxFiles)
            } else {
                Toast.makeText(
                    this@FileGateActivity,
                    tForContext(this@FileGateActivity, "Allowed for 10 minutes. Copy it again.", "۱۰ دقیقه اجازه دارد. دوباره کپی کنید."),
                    Toast.LENGTH_LONG,
                ).show()
            }
            finish()
        }
    }

    /** The regular file at [path] in the rootfs, its symlinks followed as the Linux sees them; else null. */
    private fun linuxFile(path: String): File? = linuxFiles.guestPathOf(path)?.let(linuxFiles::regularFile)

    companion object {
        private const val EXTRA_PATH = "com.hermes.android.gate.PATH"
        private const val EXTRA_OPEN = "com.hermes.android.gate.OPEN"
        private const val CHANNEL_ID = "file_gate"

        /** The dialog for [file] (a host path in the rootfs); with [open], opens it once allowed. */
        fun intent(context: Context, file: File, open: Boolean): Intent =
            Intent(context, FileGateActivity::class.java)
                .putExtra(EXTRA_PATH, file.absolutePath)
                .putExtra(EXTRA_OPEN, open)
                .apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

        /** Another app (a copy in the Files app) tried to read [file]; the tap opens the dialog. */
        fun notifyHeldBack(context: Context, file: File, risk: LinuxFileGate.Risk) {
            HermesNotifications.ensureChannel(
                context,
                NotificationChannel(
                    CHANNEL_ID,
                    tForContext(context, "Programs leaving Hermes", "برنامه‌های خروجی از Hermes"),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
            val id = file.absolutePath.hashCode()
            val tap = PendingIntent.getActivity(
                context,
                id,
                intent(context, file, open = false),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val title = if (risk == LinuxFileGate.Risk.DISGUISED) {
                tForContext(context, "Hermes blocked a disguised program", "Hermes یک برنامهٔ پنهان را بست")
            } else {
                tForContext(context, "Hermes held back a program", "Hermes جلوی بیرون رفتن یک برنامه را گرفت")
            }
            val notification = HermesNotifications.builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(tForContext(context, "${file.name} — tap to review", "${file.name} — برای بررسی بزنید"))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(id, notification)
            } catch (e: SecurityException) {
                Timber.w(e, "[FileGate] no notification permission; %s held back", file.name)
            }
        }
    }
}
