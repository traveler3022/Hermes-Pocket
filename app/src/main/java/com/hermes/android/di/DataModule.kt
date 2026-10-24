package com.hermes.android.di

import com.hermes.android.data.AppReleaseRepository
import com.hermes.android.data.CronBlueprintsRepository
import com.hermes.android.data.CronJobsRepository
import com.hermes.android.data.ServerHermesUpdatesRepository
import com.hermes.android.data.DownloadStorage
import com.hermes.android.data.GroupsRepository
import com.hermes.android.data.AchievementsRepository
import com.hermes.android.data.ArchivedChatsRepository
import com.hermes.android.data.CredentialVaultRepository
import com.hermes.android.data.ForeignSessionsRepository
import com.hermes.android.data.ServerOpsRepository
import com.hermes.android.data.ModelSlotsRepository
import com.hermes.android.data.LearningRepository
import com.hermes.android.data.MemoryProvidersRepository
import com.hermes.android.data.MessagingGatewayRepository
import com.hermes.android.data.PrefsTableColumnWidths
import com.hermes.android.data.PrefsTaskRegistry
import com.hermes.android.data.ProfilesRepository
import com.hermes.android.data.ProjectsRepository
import com.hermes.android.data.ProviderSetupRepository
import com.hermes.android.data.PairingRepository
import com.hermes.android.data.TelegramSetupRepository
import com.hermes.android.data.ToolsetSetupRepository
import com.hermes.android.data.EnvKeysRepository
import com.hermes.android.data.ConfigEditorRepository
import com.hermes.android.data.McpServerSwitchRepository
import com.hermes.android.data.UsageAnalyticsRepository
import com.hermes.android.data.WebhooksRepository
import com.hermes.android.data.SkillsAdminRepository
import com.hermes.android.data.PluginCatalogRepository
import com.hermes.android.data.ServerKanbanRepository
import com.hermes.android.data.SessionRepository
import com.hermes.android.data.SetupState
import com.hermes.android.data.StorageCleaner
import com.hermes.android.data.StorageFiles
import com.hermes.android.data.TaskRegistry
import com.hermes.android.domain.achievements.Achievements
import com.hermes.android.domain.session.ArchivedChats
import com.hermes.android.domain.vault.CredentialVault
import com.hermes.android.domain.session.ForeignSessions
import com.hermes.android.domain.ops.ServerOps
import com.hermes.android.domain.config.ModelSlots
import com.hermes.android.domain.cron.CronBlueprints
import com.hermes.android.domain.cron.CronJobs
import com.hermes.android.domain.updates.ServerHermesUpdates
import com.hermes.android.domain.files.Downloads
import com.hermes.android.domain.files.ServerFiles
import com.hermes.android.domain.groups.GroupRooms
import com.hermes.android.domain.messaging.MessagingGateway
import com.hermes.android.domain.messaging.MessagingPairing
import com.hermes.android.domain.messaging.TelegramSetup
import com.hermes.android.domain.analytics.UsageAnalytics
import com.hermes.android.domain.messaging.Webhooks
import com.hermes.android.domain.tools.ToolsetSetup
import com.hermes.android.domain.config.EnvKeys
import com.hermes.android.domain.config.ConfigEditor
import com.hermes.android.domain.tools.McpServerSwitch
import com.hermes.android.domain.tools.SkillsAdmin
import com.hermes.android.domain.tools.PluginCatalog
import com.hermes.android.domain.kanban.KanbanRepository
import com.hermes.android.domain.markdown.TableColumnWidths
import com.hermes.android.domain.media.MediaPlayers
import com.hermes.android.domain.memory.LearningJourney
import com.hermes.android.domain.memory.MemoryProviders
import com.hermes.android.domain.notify.AppVisibility
import com.hermes.android.domain.notify.ApprovalAlerts
import com.hermes.android.domain.profiles.HermesProfiles
import com.hermes.android.domain.projects.Projects
import com.hermes.android.domain.session.AgentTasks
import com.hermes.android.domain.session.ChatSessions
import com.hermes.android.domain.session.SessionCheckpoints
import com.hermes.android.domain.session.SessionContext
import com.hermes.android.domain.setup.ProviderSetup
import com.hermes.android.domain.setup.SetupProgress
import com.hermes.android.domain.storage.FileCleaner
import com.hermes.android.domain.storage.JunkCleaner
import com.hermes.android.domain.updates.AppUpdates
import com.hermes.android.gateway.RemoteFiles
import com.hermes.android.service.AppForegroundState
import com.hermes.android.service.ApprovalNotificationManager
import com.hermes.android.service.MediaStreams
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

    @Binds
    abstract fun bindTableColumnWidths(impl: PrefsTableColumnWidths): TableColumnWidths

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
    abstract fun bindToolsetSetup(impl: ToolsetSetupRepository): ToolsetSetup

    @Binds
    abstract fun bindEnvKeys(impl: EnvKeysRepository): EnvKeys

    @Binds
    abstract fun bindConfigEditor(impl: ConfigEditorRepository): ConfigEditor

    @Binds
    abstract fun bindMcpServerSwitch(impl: McpServerSwitchRepository): McpServerSwitch

    @Binds
    abstract fun bindWebhooks(impl: WebhooksRepository): Webhooks

    @Binds
    abstract fun bindUsageAnalytics(impl: UsageAnalyticsRepository): UsageAnalytics

    @Binds
    abstract fun bindSkillsAdmin(impl: SkillsAdminRepository): SkillsAdmin

    @Binds
    abstract fun bindPluginCatalog(impl: PluginCatalogRepository): PluginCatalog

    @Binds
    abstract fun bindTelegramSetup(impl: TelegramSetupRepository): TelegramSetup

    @Binds
    abstract fun bindMessagingPairing(impl: PairingRepository): MessagingPairing

    @Binds
    abstract fun bindMessagingGateway(impl: MessagingGatewayRepository): MessagingGateway

    @Binds
    abstract fun bindSetupProgress(impl: SetupState): SetupProgress

    @Binds
    abstract fun bindAppUpdates(impl: AppReleaseRepository): AppUpdates

    @Binds
    abstract fun bindKanban(impl: ServerKanbanRepository): KanbanRepository

    @Binds
    abstract fun bindCronBlueprints(impl: CronBlueprintsRepository): CronBlueprints

    @Binds
    abstract fun bindCronJobs(impl: CronJobsRepository): CronJobs

    @Binds
    abstract fun bindServerHermesUpdates(impl: ServerHermesUpdatesRepository): ServerHermesUpdates

    @Binds
    abstract fun bindMemoryProviders(impl: MemoryProvidersRepository): MemoryProviders

    @Binds
    abstract fun bindLearningJourney(impl: LearningRepository): LearningJourney

    @Binds
    abstract fun bindAchievements(impl: AchievementsRepository): Achievements

    @Binds
    abstract fun bindArchivedChats(impl: ArchivedChatsRepository): ArchivedChats

    @Binds
    abstract fun bindCredentialVault(impl: CredentialVaultRepository): CredentialVault

    @Binds
    abstract fun bindForeignSessions(impl: ForeignSessionsRepository): ForeignSessions

    @Binds
    abstract fun bindServerOps(impl: ServerOpsRepository): ServerOps

    @Binds
    abstract fun bindModelSlots(impl: ModelSlotsRepository): ModelSlots

    // Files, storage, media and notifications, behind the interfaces the screens use.
    @Binds
    abstract fun bindJunkCleaner(impl: StorageCleaner): JunkCleaner

    @Binds
    abstract fun bindFileCleaner(impl: StorageFiles): FileCleaner

    @Binds
    abstract fun bindDownloads(impl: DownloadStorage): Downloads

    @Binds
    abstract fun bindServerFiles(impl: RemoteFiles): ServerFiles

    @Binds
    abstract fun bindMediaPlayers(impl: MediaStreams): MediaPlayers

    @Binds
    abstract fun bindApprovalAlerts(impl: ApprovalNotificationManager): ApprovalAlerts

    @Binds
    abstract fun bindAppVisibility(impl: AppForegroundState): AppVisibility
}
