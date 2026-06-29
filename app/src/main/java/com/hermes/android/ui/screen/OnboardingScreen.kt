package com.hermes.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hermes.android.ui.i18n.t

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }
    val totalPages = 4

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (page) {
                0 -> WelcomePage()
                1 -> TermuxSetupPage()
                2 -> HermesInstallPage()
                3 -> DonePage()
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (page > 0) {
                    OutlinedButton(onClick = { page-- }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(t("Back", "قبلی"))
                    }
                } else {
                    TextButton(onClick = onComplete) {
                        Text(t("Skip", "رد شدن"))
                    }
                }

                Text(
                    text = "${page + 1} / $totalPages",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (page < totalPages - 1) {
                    Button(onClick = { page++ }) {
                        Text(t("Next", "بعدی"))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    Button(onClick = onComplete) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(t("Get Started", "شروع کنید"))
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Default.RocketLaunch,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = t("Welcome to Hermes", "به هرمس خوش آمدید"),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Text(
            text = t(
                "Hermes is your AI-powered assistant running locally on your Android device via Termux. Let's get you set up in a few simple steps.",
                "هرمس دستیار هوش مصنوعی شماست که به صورت محلی روی دستگاه اندروید شما از طریق ترموکس اجرا می‌شود. بیایید در چند مرحله ساده شما را راه‌اندازی کنیم."
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TermuxSetupPage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Default.Download,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = t("Install Termux", "نصب ترموکس"),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Text(
            text = t(
                "Hermes requires Termux to run. Please install Termux from F-Droid (not Google Play, as that version is outdated).",
                "هرمس برای اجرا به ترموکس نیاز دارد. لطفاً ترموکس را از F-Droid نصب کنید (نه گوگل پلی، زیرا آن نسخه قدیمی است)."
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = t("Steps:", "مراحل:"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = t(
                        "1. Download F-Droid from f-droid.org\n2. Open F-Droid and search for \"Termux\"\n3. Install Termux\n4. Open Termux once to initialize it",
                        "1. F-Droid را از f-droid.org دانلود کنید\n2. F-Droid را باز کنید و \"Termux\" را جستجو کنید\n3. ترموکس را نصب کنید\n4. ترموکس را یک بار باز کنید تا مقداردهی شود"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HermesInstallPage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Default.Terminal,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = t("Install Hermes", "نصب هرمس"),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Text(
            text = t(
                "Open Termux and run the following command to install Hermes:",
                "ترموکس را باز کنید و دستور زیر را برای نصب هرمس اجرا کنید:"
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Text(
                text = "curl -fsSL https://hermes.run/install.sh | bash",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = t(
                "This will install all required dependencies and configure the Hermes agent.",
                "این دستور تمام وابستگی‌های مورد نیاز را نصب و عامل هرمس را پیکربندی می‌کند."
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DonePage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = t("You're Ready!", "آماده‌اید!"),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Text(
            text = t(
                "Setup is complete. You can now start chatting with Hermes. Configure your AI backend in Settings to get started.",
                "راه‌اندازی کامل شد. اکنون می‌توانید با هرمس گفتگو کنید. برای شروع، بک‌اند هوش مصنوعی خود را در تنظیمات پیکربندی کنید."
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
