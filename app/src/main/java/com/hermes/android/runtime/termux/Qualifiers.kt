package com.hermes.android.runtime.termux

import com.hermes.android.runtime.InstallProgress
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Qualifier

/**
 * Hilt qualifier for the [MutableStateFlow]<[InstallProgress]> that bridges
 * the install progress BroadcastReceiver and the [TermuxBridge].
 *
 * Kept inside the termux package because only Termux-specific code creates
 * the bindings; the abstract [com.hermes.android.runtime.HermesRuntime]
 * surface remains Termux-free.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class InstallProgressFlow

/**
 * Hilt qualifier for the install completion flow.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class InstallCompletionFlow
