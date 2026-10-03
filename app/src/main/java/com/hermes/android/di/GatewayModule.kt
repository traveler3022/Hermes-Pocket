package com.hermes.android.di

import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.OkHttpGatewayClient
import com.hermes.android.gateway.redactCredentials
import com.hermes.android.runtime.remote.RemoteAuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module that binds the active [GatewayClient] implementation.
 *
 * ## Swap point
 *
 * Today this binds [OkHttpGatewayClient] (production).
 * For unit tests, a `FakeGatewayClient` can be bound instead in a test-only module.
 *
 * Reference: Phase 1.5 Rule 1 (Strict Layer Dependency) — only this file
 * in the DI layer knows about the concrete implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
object GatewayModule {

    @Provides
    @Singleton
    fun provideGatewayClient(impl: OkHttpGatewayClient): GatewayClient = impl

    @Provides
    @Singleton
    fun provideOkHttpClient(remoteAuth: RemoteAuthInterceptor): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            timber.log.Timber.d("[OkHttp] ${redactCredentials(message)}")
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            // After the logger, which logs each request before the sign-in is added
            // (the ticket on the logged response line is redacted).
            .addInterceptor(remoteAuth)
            .pingInterval(15, TimeUnit.SECONDS) // ping every 15s; if pong
            // doesn't arrive, OkHttp fires onFailure → reconnect kicks in.
            // 15s is low enough to detect stale connections through TLS proxies.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // WebSocket — no read timeout
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = false
    }
}
