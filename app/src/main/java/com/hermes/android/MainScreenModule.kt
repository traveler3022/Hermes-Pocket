package com.hermes.android

import android.content.Intent
import com.hermes.android.service.MainScreenIntent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Notifications ([MainScreenIntent]) open [MainActivity]. */
@Module
@InstallIn(SingletonComponent::class)
object MainScreenModule {
    @Provides
    fun mainScreenIntent(): MainScreenIntent = MainScreenIntent { context -> Intent(context, MainActivity::class.java) }
}
