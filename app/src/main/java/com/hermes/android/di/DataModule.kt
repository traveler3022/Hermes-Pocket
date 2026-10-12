package com.hermes.android.di

import com.hermes.android.data.PrefsTaskRegistry
import com.hermes.android.data.SessionRepository
import com.hermes.android.data.TaskRegistry
import com.hermes.android.domain.session.AgentTasks
import com.hermes.android.domain.session.ChatSessions
import com.hermes.android.domain.session.SessionCheckpoints
import com.hermes.android.domain.session.SessionContext
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

    // One SessionRepository (a singleton) behind each of the views the screens use.
    @Binds
    abstract fun bindChatSessions(impl: SessionRepository): ChatSessions

    @Binds
    abstract fun bindAgentTasks(impl: SessionRepository): AgentTasks

    @Binds
    abstract fun bindSessionContext(impl: SessionRepository): SessionContext

    @Binds
    abstract fun bindSessionCheckpoints(impl: SessionRepository): SessionCheckpoints
}
