package com.hermes.android.data

import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Group Chat: Hermes' hosted rooms (`groups.*`, `tui_gateway/methods_groups.py`).
 *
 * The gateway runs the discussion itself (`gateway/hosted_room_discussion.py`): who
 * answers a message (everyone, or the `@handle`s it names), up to three rounds,
 * `(pass)` for "nothing to add", at most ten member replies per message. Everything
 * lands in a durable room log. So a room keeps going while the app is closed, and the
 * app only creates rooms, sends the user's messages and reads that log.
 *
 * Every member is a profile on this gateway, and a room seats 2 to 6 of them. The
 * members are fixed when the room is created (the server has no call to change them).
 */
@Singleton
class GroupsRepository @Inject constructor(
    private val gatewayClient: GatewayClient,
) {
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

    /**
     * Whether this gateway drives rooms. False on a gateway without the room worker:
     * an older Hermes, or one started without it.
     */
    suspend fun available(): Boolean {
        val result = gatewayClient.request(GatewayMethods.GROUPS_CAPABILITIES, trackSession = false) as? JsonObject
        return result?.bool("driver") == true
    }

    suspend fun list(): List<Room> {
        val result = gatewayClient.request(GatewayMethods.GROUPS_LIST, trackSession = false) as? JsonObject
            ?: return emptyList()
        return (result["rooms"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.toRoom() }
            .sortedByDescending { it.updatedAt }
    }

    suspend fun room(roomId: String): Room? {
        val result = gatewayClient.request(
            GatewayMethods.GROUPS_STATE,
            mapOf("room_id" to JsonPrimitive(roomId)),
            trackSession = false,
        ) as? JsonObject
        return (result?.get("room") as? JsonObject)?.toRoom()
    }

    /** Seats [profiles] (2 to 6) in a new room called [name]. */
    suspend fun create(name: String, profiles: List<ProfilesRepository.Profile>): Room {
        val members = profiles.map { profile ->
            JsonObject(
                mapOf(
                    "member_id" to JsonPrimitive("m-${profile.name}"),
                    "profile" to JsonPrimitive(profile.name),
                    "handle" to JsonPrimitive(handleFor(profile)),
                    "display_name" to JsonPrimitive(profile.title.take(MAX_LABEL_CHARS)),
                ),
            )
        }
        val result = gatewayClient.request(
            GatewayMethods.GROUPS_CREATE,
            mapOf(
                "room_id" to JsonPrimitive("room-${UUID.randomUUID()}"),
                "name" to JsonPrimitive(name.trim().take(MAX_ROOM_NAME_CHARS)),
                "members" to JsonArray(members),
            ),
            trackSession = false,
        ) as? JsonObject
        return (result?.get("room") as? JsonObject)?.toRoom()
            ?: throw IllegalStateException("groups.create: no room in the answer")
    }

    /**
     * Posts the user's [text]. [clientId] makes a resend of the same message a no-op on
     * the server, so a send retried after a dropped connection is not posted twice.
     */
    suspend fun send(roomId: String, text: String, clientId: String) {
        gatewayClient.request(
            GatewayMethods.GROUPS_SEND,
            mapOf(
                "room_id" to JsonPrimitive(roomId),
                "event_id" to JsonPrimitive(clientId),
                "payload" to JsonObject(
                    mapOf("text" to JsonPrimitive(text), "thread_id" to JsonPrimitive(MAIN_THREAD)),
                ),
            ),
            trackSession = false,
        )
    }

    /** The room log after [sinceSeq], all pages of it. */
    suspend fun log(roomId: String, sinceSeq: Long): LogPage {
        val events = mutableListOf<Event>()
        var cursor = sinceSeq
        var latest = sinceSeq
        while (true) {
            val page = gatewayClient.request(
                GatewayMethods.GROUPS_LOG,
                mapOf(
                    "room_id" to JsonPrimitive(roomId),
                    "since_seq" to JsonPrimitive(cursor),
                    "limit" to JsonPrimitive(LOG_PAGE),
                ),
                trackSession = false,
            ) as? JsonObject ?: break
            (page["events"] as? JsonArray).orEmpty().mapNotNullTo(events) { (it as? JsonObject)?.toEvent() }
            latest = maxOf(latest, page.long("latest_seq") ?: latest)
            val next = page.long("cursor") ?: cursor
            if (page.bool("has_more") != true || next <= cursor) break
            cursor = next
        }
        return LogPage(events, latest)
    }

    suspend fun status(roomId: String): Status? {
        val result = gatewayClient.request(
            GatewayMethods.GROUPS_STATE,
            mapOf("room_id" to JsonPrimitive(roomId)),
            trackSession = false,
        ) as? JsonObject
        val driver = result?.get("driver_status") as? JsonObject ?: return null
        val actions = (driver["pending_actions"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        return Status(
            working = driver.bool("working") == true,
            approvals = actions.filter { it.str("kind") == "approval" }.mapNotNull { it.toApproval() },
            retryTaskIds = actions.filter { it.str("kind") == "retry" }.map { it.str("task_id") }.filter { it.isNotEmpty() },
        )
    }

    /** Cancels the turns queued or running in the room; what was said stays. */
    suspend fun stop(roomId: String) {
        gatewayClient.request(
            GatewayMethods.GROUPS_STOP,
            mapOf("room_id" to JsonPrimitive(roomId), "cancel_id" to JsonPrimitive("app-stop-${UUID.randomUUID()}")),
            trackSession = false,
        )
    }

    /** Answers a member's held command once: [allow] runs it this one time, otherwise it is refused. */
    suspend fun approve(roomId: String, approval: Approval, allow: Boolean) {
        gatewayClient.request(
            GatewayMethods.GROUPS_APPROVE,
            mapOf(
                "room_id" to JsonPrimitive(roomId),
                "member_id" to JsonPrimitive(approval.memberId),
                "task_id" to JsonPrimitive(approval.taskId),
                "execution_generation" to JsonPrimitive(approval.executionGeneration),
                // The room accepts only these two (hosted_room_service.approve_room_task).
                "choice" to JsonPrimitive(if (allow) "once" else "deny"),
                "request_id" to JsonPrimitive(approval.requestId),
            ),
            trackSession = false,
        )
    }

    suspend fun retry(roomId: String, taskId: String) {
        gatewayClient.request(
            GatewayMethods.GROUPS_RETRY,
            mapOf("room_id" to JsonPrimitive(roomId), "task_id" to JsonPrimitive(taskId)),
            trackSession = false,
        )
    }

    suspend fun rename(roomId: String, name: String) {
        gatewayClient.request(
            GatewayMethods.GROUPS_RENAME,
            mapOf(
                "room_id" to JsonPrimitive(roomId),
                "event_id" to JsonPrimitive("rename-${UUID.randomUUID()}"),
                "name" to JsonPrimitive(name.trim().take(MAX_ROOM_NAME_CHARS)),
            ),
            trackSession = false,
        )
    }

    /** Ends the room for good: running turns stop and the room and its log are gone. */
    suspend fun delete(roomId: String) {
        gatewayClient.request(
            GatewayMethods.GROUPS_DISBAND,
            mapOf("room_id" to JsonPrimitive(roomId)),
            trackSession = false,
        )
    }

    companion object {
        /** The app keeps one conversation per room; the server supports several threads. */
        const val MAIN_THREAD = "main"
        const val MIN_MEMBERS = 2
        const val MAX_MEMBERS = 6
        private const val LOG_PAGE = 200
        private const val MAX_ROOM_NAME_CHARS = 200
        private const val MAX_LABEL_CHARS = 200

        /**
         * The `@` handle a profile answers to in rooms. The main profile is `@hermes`, as in
         * Hermes' own Bot Mode; no other profile can take that name (it is reserved).
         */
        fun handleFor(profile: ProfilesRepository.Profile): String =
            if (profile.isDefault) "hermes" else profile.name
    }
}

private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong()

private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

private fun JsonObject.toRoom(): GroupsRepository.Room? {
    val id = str("room_id").ifEmpty { return null }
    if ((this["disbanded_at"] as? JsonPrimitive)?.contentOrNull != null) return null
    val members = (this["members"] as? JsonArray).orEmpty().mapNotNull { element ->
        val member = element as? JsonObject ?: return@mapNotNull null
        GroupsRepository.Member(
            memberId = member.str("member_id"),
            profile = member.str("profile"),
            handle = member.str("handle"),
            displayName = member.str("display_name"),
        ).takeIf { it.memberId.isNotEmpty() }
    }
    return GroupsRepository.Room(
        roomId = id,
        name = str("name").ifBlank { id },
        members = members,
        latestSeq = long("latest_seq") ?: 0,
        updatedAt = str("updated_at").toDoubleOrNull() ?: 0.0,
    )
}

private fun JsonObject.toEvent(): GroupsRepository.Event? {
    val seq = long("seq") ?: return null
    val actor = this["actor"] as? JsonObject
    return GroupsRepository.Event(
        seq = seq,
        kind = str("kind"),
        actorKind = actor?.str("kind").orEmpty(),
        actorId = actor?.str("id").orEmpty(),
        payload = this["payload"] as? JsonObject ?: JsonObject(emptyMap()),
        createdAt = str("created_at").toDoubleOrNull() ?: 0.0,
    )
}

private fun JsonObject.toApproval(): GroupsRepository.Approval? {
    val approval = this["approval"] as? JsonObject
    val requestId = str("request_id").ifEmpty { approval?.str("request_id").orEmpty() }
    if (requestId.isEmpty()) return null
    return GroupsRepository.Approval(
        memberId = str("member_id"),
        taskId = str("task_id"),
        executionGeneration = long("execution_generation")?.toInt() ?: 0,
        requestId = requestId,
        command = approval?.str("command").orEmpty(),
        description = approval?.str("description").orEmpty(),
    )
}
