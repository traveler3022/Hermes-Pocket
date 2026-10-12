package com.hermes.android.di

import com.hermes.android.data.AppReleaseRepository
import com.hermes.android.data.GroupsRepository
import com.hermes.android.data.PrefsTaskRegistry
import com.hermes.android.data.ProfilesRepository
import com.hermes.android.data.ProjectsRepository
import com.hermes.android.data.ProviderSetupRepository
import com.hermes.android.data.SessionRepository
import com.hermes.android.data.SetupState
import com.hermes.android.data.TaskRegistry
import com.hermes.android.domain.groups.GroupRooms
import com.hermes.android.domain.profiles.HermesProfiles
import com.hermes.android.domain.projects.Projects
import com.hermes.android.domain.session.AgentTasks
import com.hermes.android.domain.session.ChatSessions
import com.hermes.android.domain.session.SessionCheckpoints
import com.hermes.android.domain.session.SessionContext
import com.hermes.android.domain.setup.ProviderSetup
import com.hermes.android.domain.setup.SetupProgress
import com.hermes.android.domain.updates.AppUpdates
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

    @Binds
    abstract fun bindGroupRooms(impl: GroupsRepository): GroupRooms

    @Binds
    abstract fun bindHermesProfiles(impl: ProfilesRepository): HermesProfiles

    @Binds
    abstract fun bindProjects(impl: ProjectsRepository): Projects

    @Binds
    abstract fun bindProviderSetup(impl: ProviderSetupRepository): ProviderSetup

    @Binds
    abstract fun bindSetupProgress(impl: SetupState): SetupProgress

    @Binds
    abstract fun bindAppUpdates(impl: AppReleaseRepository): AppUpdates
}
