package com.hermes.android.di

import com.hermes.android.runtime.HermesRuntime
import com.hermes.android.runtime.remote.RemoteRuntime
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds the active [HermesRuntime] implementation.
 *
 * ## Swap point
 *
 * Today this binds [RemoteRuntime] — Hermes runs on the user's server and
 * the app connects over `wss://`. The Termux migration adapter has been
 * removed as the production runtime is now remote server-based.
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
}

/**
 * Placeholder module for any additional context-related bindings we
 * may need later.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
