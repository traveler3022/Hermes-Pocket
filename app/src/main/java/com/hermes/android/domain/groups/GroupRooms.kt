package com.hermes.android.domain.groups

import com.hermes.android.domain.profiles.HermesProfiles
import kotlinx.serialization.json.JsonObject

/** Group chats: rooms where several profiles answer together (Hermes' own rooms). */
interface GroupRooms {

    /** Whether this gateway drives rooms; false on one without the room worker. */
    suspend fun available(): Boolean

    suspend fun list(): List<Room>

    suspend fun room(roomId: String): Room?

    /** Seats [profiles] ([MIN_MEMBERS] to [MAX_MEMBERS]) in a new room called [name]. */
    suspend fun create(name: String, profiles: List<HermesProfiles.Profile>): Room

    /** Posts the user's [text]; [clientId] makes a resend of the same message a no-op. */
    suspend fun send(roomId: String, text: String, clientId: String)

    /** The room log after [sinceSeq], all pages of it. */
    suspend fun log(roomId: String, sinceSeq: Long): LogPage

    suspend fun status(roomId: String): Status?

    /** Cancels the turns queued or running in the room; what was said stays. */
    suspend fun stop(roomId: String)

    /** Answers a member's held command once: [allow] runs it this one time, otherwise it is refused. */
    suspend fun approve(roomId: String, approval: Approval, allow: Boolean)

    suspend fun retry(roomId: String, taskId: String)

    suspend fun rename(roomId: String, name: String)

    /** Ends the room for good: running turns stop and the room and its log are gone. */
    suspend fun delete(roomId: String)

    data class Member(
        val memberId: String,
        val profile: String,
        /** What `@` mentions match in the room. */
        val handle: String,
        val displayName: String,
    ) {
        val title: String get() = displayName.ifBlank { handle }
    }

    data class Room(
        val roomId: String,
        val name: String,
        val members: List<Member>,
        val latestSeq: Long,
        val updatedAt: Double,
    )

    /** One row of the room log, as the server wrote it. */
    data class Event(
        val seq: Long,
        val kind: String,
        val actorKind: String,
        val actorId: String,
        val payload: JsonObject,
        val createdAt: Double,
    )

    data class LogPage(val events: List<Event>, val latestSeq: Long)

    /** A command a member wants to run, held until the user allows or denies it. */
    data class Approval(
        val memberId: String,
        val taskId: String,
        val executionGeneration: Int,
        val requestId: String,
        val command: String,
        val description: String,
    )

    /** What the room's worker is doing now (`groups.state` → `driver_status`). */
    data class Status(
        val working: Boolean,
        val approvals: List<Approval>,
        /** Turns whose outcome is unknown (the worker restarted mid-turn): the user may run them again. */
        val retryTaskIds: List<String>,
    )

    companion object {
        /** The app keeps one conversation per room; the server supports several threads. */
        const val MAIN_THREAD = "main"
        const val MIN_MEMBERS = 2
        const val MAX_MEMBERS = 6

        /**
         * The `@` handle a profile answers to in rooms. The main profile is `@hermes`, as in
         * Hermes' own Bot Mode; no other profile can take that name (it is reserved).
         */
        fun handleFor(profile: HermesProfiles.Profile): String =
            if (profile.isDefault) "hermes" else profile.name
    }
}
