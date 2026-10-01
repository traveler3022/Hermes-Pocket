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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.hermes.android.runtime.linux.FileGate
import com.hermes.android.runtime.linux.GuestFiles
import com.hermes.android.runtime.linux.ProotEnvironment
import com.hermes.android.service.HermesNotifications
import com.hermes.android.ui.i18n.AppLanguageState
import com.hermes.android.ui.i18n.LocalAppLanguage
import com.hermes.android.ui.i18n.t
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
 * Asks before a program of the built-in Linux leaves for the phone ([FileGate]); a program
 * named like a photo or document is only reported. Opened by "Open with another app" (then
 * opens the file) and by the notification the Files provider posts when it held a file back.
 */
@AndroidEntryPoint
class FileGateActivity : ComponentActivity() {

    @Inject lateinit var fileGate: FileGate

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
                file == null -> if (open) startExternalView(this@FileGateActivity, Uri.fromFile(File(path)).toString())
                risk == null -> if (open) startExternalView(this@FileGateActivity, Uri.fromFile(file).toString())
                else -> {
                    ask(file, risk, open)
                    return@launch
                }
            }
            finish()
        }
    }

    private fun ask(file: File, risk: FileGate.Risk, open: Boolean) {
        val themeModeState = ThemeModeState(this)
        val appLanguageState = AppLanguageState(this)
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides appLanguageState.language) {
                Hermes2Theme(
                    themeMode = themeModeState.mode,
                    colorTheme = themeModeState.colorTheme,
                    warmMode = themeModeState.warmMode,
                    appFont = themeModeState.appFont,
                    fontScalePct = themeModeState.fontScalePct,
                ) {
                    if (risk == FileGate.Risk.DISGUISED) {
                        AlertDialog(
                            onDismissRequest = ::finish,
                            title = { Text(t("Blocked: a disguised program", "بسته شد: برنامهٔ پنهان")) },
                            text = {
                                Text(
                                    t(
                                        "“${file.name}” is named like a photo or document, but it is really a program. " +
                                            "Files like this are made to trick people, so it can't leave Hermes.",
                                        "«${file.name}» از روی اسمش عکس یا سند به نظر می‌رسد، ولی در واقع برنامه است. " +
                                            "این‌جور فایل‌ها برای فریب ساخته می‌شوند، پس از Hermes بیرون نمی‌رود.",
                                    ),
                                )
                            },
                            confirmButton = { TextButton(onClick = ::finish) { Text(t("OK", "باشه")) } },
                        )
                    } else {
                        AlertDialog(
                            onDismissRequest = ::finish,
                            title = { Text(t("This file is a program", "این فایل یک برنامه است")) },
                            text = {
                                Text(
                                    t(
                                        "“${file.name}” may have come from the internet. If it is malware, it can harm " +
                                            "your phone. Continue only if you know where it came from.",
                                        "«${file.name}» ممکن است از اینترنت آمده باشد. اگر بدافزار باشد، می‌تواند به " +
                                            "گوشی آسیب بزند. فقط اگر می‌دانید از کجا آمده ادامه دهید.",
                                    ),
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = { allow(file, open) }) {
                                    Text(
                                        if (open) t("Open anyway", "باز شود") else t("Allow for 10 minutes", "اجازه برای ۱۰ دقیقه"),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                            dismissButton = { TextButton(onClick = ::finish) { Text(t("Cancel", "لغو")) } },
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
                startExternalView(this@FileGateActivity, Uri.fromFile(file).toString())
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
    private fun linuxFile(path: String): File? {
        val rootfs = ProotEnvironment.rootfsDir(this)
        val guestPath = guestPathIn(rootfs.absolutePath, path) ?: return null
        return runCatching { GuestFiles(rootfs).hostFile(guestPath) }.getOrNull()?.takeIf { it.isFile }
    }

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
        fun notifyHeldBack(context: Context, file: File, risk: FileGate.Risk) {
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
            val title = if (risk == FileGate.Risk.DISGUISED) {
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
