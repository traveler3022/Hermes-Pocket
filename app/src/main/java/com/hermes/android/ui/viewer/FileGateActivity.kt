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
import com.hermes.android.runtime.linux.FileGate
import com.hermes.android.runtime.linux.ProotEnvironment
import com.hermes.android.service.HermesNotifications
import com.hermes.android.ui.i18n.AppLanguageState
import com.hermes.android.ui.i18n.LocalAppLanguage
import com.hermes.android.ui.i18n.t
import com.hermes.android.i18n.tForContext
import com.hermes.android.ui.screen.openUrlExternally
import com.hermes.android.ui.theme.Hermes2Theme
import com.hermes.android.ui.theme.ThemeModeState
import timber.log.Timber
import java.io.File

/**
 * Asks before a program of the built-in Linux leaves for the phone ([FileGate]); a program
 * named like a photo or document is only reported. Opened by "Open with another app" (then
 * opens the file) and by the notification the Files provider posts when it held a file back.
 */
class FileGateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = intent.getStringExtra(EXTRA_PATH)?.let(::File)?.takeIf { insideLinux(it) && it.isFile }
        if (file == null) {
            finish()
            return
        }
        val open = intent.getBooleanExtra(EXTRA_OPEN, false)
        val risk = FileGate.riskOf(file)
        if (risk == FileGate.Risk.SAFE) {
            if (open) openUrlExternally(this, Uri.fromFile(file).toString())
            finish()
            return
        }
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
        FileGate.approve(file)
        if (open) {
            openUrlExternally(this, Uri.fromFile(file).toString())
        } else {
            Toast.makeText(
                this,
                tForContext(this, "Allowed for 10 minutes. Copy it again.", "۱۰ دقیقه اجازه دارد. دوباره کپی کنید."),
                Toast.LENGTH_LONG,
            ).show()
        }
        finish()
    }

    private fun insideLinux(file: File): Boolean {
        val root = runCatching { ProotEnvironment.rootfsDir(this).canonicalPath }.getOrNull() ?: return false
        val path = runCatching { file.canonicalPath }.getOrNull() ?: return false
        return path.startsWith(root + File.separator)
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
