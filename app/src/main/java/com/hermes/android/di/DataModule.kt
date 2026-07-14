package com.hermes.android.di

import com.hermes.android.data.PrefsTaskRegistry
import com.hermes.android.data.TaskRegistry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bindings for the `data/` layer (Milestone A). Interfaces exist so the
 * repository's contract tests run on the JVM with in-memory fakes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindTaskRegistry(impl: PrefsTaskRegistry): TaskRegistry
}
