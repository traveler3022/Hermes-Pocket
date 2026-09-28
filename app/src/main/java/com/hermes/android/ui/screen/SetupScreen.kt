package com.hermes.android.ui.screen

import android.content.Intent
import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import com.hermes.android.ui.icons.filled.OpenInNew
import com.hermes.android.ui.icons.filled.RocketLaunch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.android.service.HermesGatewayService
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.RuntimeChoiceUi
import com.hermes.android.ui.viewmodel.RuntimeEffect
import com.hermes.android.ui.viewmodel.RuntimeUiState
import com.hermes.android.ui.viewmodel.RuntimeViewModel
import com.hermes.android.ui.viewmodel.SetupStep
import com.hermes.android.ui.viewmodel.SetupUiState
import com.hermes.android.ui.viewmodel.SetupViewModel

/** First-run setup, modelled on Aether's onboarding: runtime first, then provider + key + model. */
@Composable
fun SetupScreen(
    onFinished: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
    runtimeViewModel: RuntimeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    LaunchedEffect(runtimeViewModel.effects) {
        runtimeViewModel.effects.collect { effect ->
            when (effect) {
                RuntimeEffect.StartForegroundService -> HermesGatewayService.start(context)
            }
        }
    }
    BackHandler(enabled = state.step != SetupStep.Welcome) { viewModel.back() }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.step != SetupStep.Welcome) {
                StepHeader(state.step, onBack = viewModel::back)
            }
            AnimatedContent(
                targetState = state.step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "setup_step",
            ) { step ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (step) {
                        SetupStep.Welcome -> WelcomeStep(onStart = { viewModel.goTo(SetupStep.Runtime) })
                        SetupStep.Runtime -> RuntimeStep(runtimeViewModel, onReady = viewModel::onRuntimeReady)
                        SetupStep.Provider -> ProviderStep(state, viewModel)
                        SetupStep.ApiKey -> ApiKeyStep(state, viewModel)
                        SetupStep.Model -> ModelStep(state, viewModel)
                    }
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    state.confirmMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text(t("Expensive model", "مدل گران")) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::confirmExpensiveModel) { Text(t("Use it", "استفاده شود")) } },
            dismissButton = { TextButton(onClick = viewModel::dismissConfirm) { Text(t("Pick another", "انتخاب دیگر")) } },
        )
    }
}

@Composable
private fun StepHeader(step: SetupStep, onBack: () -> Unit) {
    val index = when (step) {
        SetupStep.Welcome -> 0
        SetupStep.Runtime -> 1
        SetupStep.Provider -> 2
        SetupStep.ApiKey -> 3
        SetupStep.Model -> 4
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "بازگشت")) }
        Text(
            t("Step $index of 4", "مرحله $index از ۴"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    LinearProgressIndicator(progress = { index / 4f }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ColumnScope.WelcomeStep(onStart: () -> Unit) {
    Spacer(Modifier.height(48.dp))
    Icon(
        Icons.Default.RocketLaunch,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(72.dp).align(Alignment.CenterHorizontally),
    )
    Text(
        "Hermes",
        style = MaterialTheme.typography.displayMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Text(
        t(
            "An AI agent that lives on your phone. It runs commands, edits files and searches the web — " +
                "with your own API key, no account and no middleman.",
            "یک ایجنت هوش مصنوعی که روی گوشی خودتان زندگی می‌کند: دستور اجرا می‌کند، فایل ویرایش می‌کند و در وب " +
                "جست‌وجو می‌کند — با کلید API خودتان، بدون حساب کاربری و بدون واسطه.",
        ),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(t("Get started", "شروع")) }
}

@Composable
private fun ColumnScope.RuntimeStep(viewModel: RuntimeViewModel, onReady: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val choice by viewModel.runtimeChoice.collectAsStateWithLifecycle()
    val installing by viewModel.installing.collectAsStateWithLifecycle()
    val progress by viewModel.installProgress.collectAsStateWithLifecycle()
    val error by viewModel.errorMessage.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val chosen by viewModel.runtimeChosen.collectAsStateWithLifecycle()

    // Nothing is detected, installed or started until the user has picked:
    // selectRuntime() runs detection for the runtime they chose.
    LaunchedEffect(chosen) { if (chosen) viewModel.detect() }

    Text(t("Where should Hermes run?", "Hermes کجا اجرا شود؟"), style = MaterialTheme.typography.headlineSmall)
    RuntimeChoiceRow(selected = choice.takeIf { chosen }, enabled = !installing, onSelect = viewModel::selectRuntime)
    if (!chosen) {
        Text(
            t(
                "Built-in Linux runs Hermes inside this app, with nothing else to install. " +
                    "Termux uses the separate Termux app — pick it if you run Hermes there. " +
                    "You can switch later in Settings.",
                "لینوکس داخلی Hermes را داخل همین اپ اجرا می‌کند و چیز دیگری لازم نیست. " +
                    "Termux از اپ جداگانه‌ی Termux استفاده می‌کند — اگر Hermes را آنجا اجرا می‌کنید این را بزنید. " +
                    "بعداً از تنظیمات هم می‌شود عوضش کرد.",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Text(
        if (choice == RuntimeChoiceUi.BuiltInLinux) {
            t(
                "Recommended. A small Linux runs inside this app — nothing else to install.",
                "پیشنهادی. یک لینوکس کوچک داخل همین اپ اجرا می‌شود — نیازی به نصب چیز دیگری نیست.",
            )
        } else {
            t(
                "Uses the separate Termux app. Pick this if you already run Hermes in Termux.",
                "از اپ جداگانه‌ی Termux استفاده می‌کند. اگر Hermes را از قبل در Termux دارید این را انتخاب کنید.",
            )
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    when (val state = uiState) {
        RuntimeUiState.NotDetected, RuntimeUiState.Detecting ->
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        is RuntimeUiState.Detected -> if (choice == RuntimeChoiceUi.BuiltInLinux) {
            BuiltInLinuxDetectedContent(diskFreeBytes = state.diskFreeBytes, onStartInstall = viewModel::startInstall)
        } else {
            DetectedContent(
                version = state.version,
                diskFreeBytes = state.diskFreeBytes,
                onShowInstallInstructions = viewModel::prepareInstallInstructions,
                onStartInstall = viewModel::startInstall,
                onLaunchHostApp = viewModel::launchHostApp,
                onStartGateway = viewModel::startGateway,
            )
        }
        RuntimeUiState.Installing -> InstallingContent(progress = progress)
        is RuntimeUiState.Installed -> InstalledContent(
            hermesVersion = state.hermesVersion,
            onStartGateway = viewModel::startGateway,
            startLabel = t("Start Hermes", "اجرای Hermes"),
        )
        is RuntimeUiState.Running -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Text(t("Hermes is running", "Hermes در حال اجراست"), style = MaterialTheme.typography.titleMedium)
                }
            }
            Button(onClick = onReady, modifier = Modifier.fillMaxWidth()) { Text(t("Continue", "ادامه")) }
        }
        is RuntimeUiState.Error -> ErrorContent(
            message = state.message,
            onRetry = viewModel::detect,
            onFetchLogs = viewModel::fetchLogs,
        )
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    logs?.let {
        Text(it.takeLast(4_000), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ProviderStep(state: SetupUiState, viewModel: SetupViewModel) {
    Text(t("Choose your AI provider", "ارائه‌دهنده‌ی هوش مصنوعی را انتخاب کنید"), style = MaterialTheme.typography.headlineSmall)
    Text(
        t(
            "Hermes calls the model with your own key. It is stored only on this phone.",
            "Hermes با کلید خودتان به مدل وصل می‌شود. کلید فقط روی همین گوشی ذخیره می‌شود.",
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        state.providers.forEachIndexed { index, provider ->
            if (index > 0) HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.busy) { viewModel.pickProvider(provider) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(provider.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
    if (state.alreadyConfigured) {
        TextButton(onClick = viewModel::finish, modifier = Modifier.fillMaxWidth()) {
            Text(t("Keep my current model and finish", "همین مدل فعلی بماند و تمام"))
        }
    }
}

@Composable
private fun ApiKeyStep(state: SetupUiState, viewModel: SetupViewModel) {
    val provider = state.provider ?: return
    val context = LocalContext.current
    Text(provider.name, style = MaterialTheme.typography.headlineSmall)
    if (provider.needsBaseUrl) {
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = viewModel::setBaseUrl,
            label = { Text(t("Base URL", "آدرس پایه")) },
            placeholder = { Text("https://example.com/v1") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OutlinedTextField(
        value = state.apiKey,
        onValueChange = viewModel::setApiKey,
        label = { Text(if (provider.needsBaseUrl) t("API key (optional)", "کلید API (اختیاری)") else t("API key", "کلید API")) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    if (provider.keyUrl.isNotEmpty()) {
        TextButton(onClick = {
            // No browser (or it is disabled): ActivityNotFoundException would close the app.
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.keyUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: android.content.ActivityNotFoundException) {
                Toast.makeText(context, provider.keyUrl, Toast.LENGTH_LONG).show()
            }
        }) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(t("Get a key from ${provider.name}", "دریافت کلید از ${provider.name}"))
        }
    }
    Button(onClick = viewModel::submitKey, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
        if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Text(t("Verify and continue", "بررسی و ادامه"))
    }
}

@Composable
private fun ModelStep(state: SetupUiState, viewModel: SetupViewModel) {
    Text(t("Pick a model", "یک مدل انتخاب کنید"), style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
        value = state.modelQuery,
        onValueChange = viewModel::setModelQuery,
        label = {
            Text(
                if (state.models.isEmpty()) t("Model ID", "شناسه‌ی مدل") else t("Search or type a model ID", "جست‌وجو یا تایپ شناسه‌ی مدل"),
            )
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val query = state.modelQuery.trim()
    if (query.isNotEmpty() && query !in state.models) {
        Button(onClick = { viewModel.pickModel(query) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(t("Use \"$query\"", "استفاده از «$query»"))
        }
    }
    val filtered = state.models.filter { query.isEmpty() || it.contains(query, ignoreCase = true) }.take(60)
    if (filtered.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            filtered.forEachIndexed { index, model ->
                if (index > 0) HorizontalDivider()
                Text(
                    model,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !state.busy) { viewModel.pickModel(model) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
    }
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
}
