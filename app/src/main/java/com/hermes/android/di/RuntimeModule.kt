package com.hermes.android.di

import android.content.Context
import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.InstallProgress
import com.hermes.android.runtime.remote.RemoteRuntime
import com.hermes.android.runtime.termux.InstallCompletionFlow
import com.hermes.android.runtime.termux.InstallProgressFlow
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
 * Today this binds [RemoteRuntime] — Hermes runs on the user's server and
 * the app connects over `wss://`. The Termux migration adapter
 * ([com.hermes.android.runtime.termux.TermuxBridge]) is superseded and
 * scheduled for removal.
 *
 * Per the swap contract this is the ONLY file that changes when the
 * runtime implementation is replaced; the rest of the app keeps working.
 *
 * Reference: ADR-009 (production must not require Termux)
 */
@Module
@InstallIn(SingletonComponent::class)
object RuntimeModule {

    /**
     * Bind [HermesRuntime] to [RemoteRuntime].
     */
    @Provides
    @Singleton
    fun provideHermesRuntime(runtime: RemoteRuntime): HermesRuntime = runtime

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
