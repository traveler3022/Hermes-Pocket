package com.hermes.android.di

import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.InstallProgress
import com.hermes.android.runtime.SwitchableHermesRuntime
import com.hermes.android.runtime.termux.InstallCompletionFlow
import com.hermes.android.runtime.termux.InstallProgressFlow
import com.hermes.android.runtime.termux.TermuxInstallProgressReceiver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Singleton

/**
 * Hilt module that binds the active [HermesRuntime] implementation.
 *
 * ## Swap point
 *
 * Binds [SwitchableHermesRuntime], which routes to the Termux bridge or the
 * built-in proot Linux runtime depending on the user's selection.
 *
 * Reference: ADR-001 (Termux migration), ADR-009 (production embedded Python)
 */
@Module
@InstallIn(SingletonComponent::class)
object RuntimeModule {

    @Provides
    @Singleton
    fun provideHermesRuntime(router: SwitchableHermesRuntime): HermesRuntime = router

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
