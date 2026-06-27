package com.hermes.android.di

import android.content.Context
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.InstallProgress
import com.hermes.android.runtime.termux.InstallCompletionFlow
import com.hermes.android.runtime.termux.InstallProgressFlow
import com.hermes.android.runtime.termux.TermuxBridge
import com.hermes.android.runtime.termux.TermuxInstallProgressReceiver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Singleton

/**
 * Hilt module that binds the active [HermesRuntime] implementation.
 *
 * ## Swap point
 *
 * Today this binds [TermuxBridge] (migration phase, ADR-001).
 * When the Embedded Python runtime is ready (ADR-009), this is the ONLY
 * file that needs to change — replace `TermuxBridge::class` with
 * `EmbeddedPythonRuntime::class` and the rest of the app keeps working.
 *
 * Reference: ADR-001 (Termux migration), ADR-009 (production embedded Python)
 */
@Module
@InstallIn(SingletonComponent::class)
object RuntimeModule {

    /**
     * Bind [HermesRuntime] to [TermuxBridge].
     *
     * TODO (future): swap to EmbeddedPythonRuntime when ready.
     */
    @Provides
    @Singleton
    fun provideHermesRuntime(bridge: TermuxBridge): HermesRuntime = bridge

    /**
     * Shared state flow for install progress. Bridged between
     * [com.hermes.android.runtime.termux.TermuxInstallProgressReceiver]
     * and [TermuxBridge].
     */
    @Provides
    @Singleton
    @InstallProgressFlow
    fun provideInstallProgressFlow(): MutableStateFlow<InstallProgress?> =
        MutableStateFlow(null)

    /**
     * Shared state flow for install completion status.
     */
    @Provides
    @Singleton
    @InstallCompletionFlow
    fun provideInstallCompletionFlow():
        MutableStateFlow<TermuxInstallProgressReceiver.InstallCompletion> =
        MutableStateFlow(TermuxInstallProgressReceiver.InstallCompletion.Pending)
}

/**
 * Provides the [Context] needed by Termux components.
 * Already provided by Hilt's built-in [ApplicationContext] — this is just
 * a placeholder module for any additional context-related bindings we
 * may need later.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
