package com.hermes.android.data

import com.hermes.android.domain.groups.GroupRooms
import com.hermes.android.domain.groups.GroupRooms.Member
import com.hermes.android.domain.groups.GroupRooms.Room
import com.hermes.android.domain.groups.GroupRooms.Event
import com.hermes.android.domain.groups.GroupRooms.LogPage
import com.hermes.android.domain.groups.GroupRooms.Approval
import com.hermes.android.domain.groups.GroupRooms.Status
import com.hermes.android.domain.profiles.HermesProfiles
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
) : GroupRooms {
    /**
     * Whether this gateway drives rooms. False on a gateway without the room worker:
     * an older Hermes, or one started without it.
     */
    override suspend fun available(): Boolean {
        val result = gatewayClient.request(GatewayMethods.GROUPS_CAPABILITIES, trackSession = false) as? JsonObject
        return result?.bool("driver") == true
    }

    override suspend fun list(): List<Room> {
        val result = gatewayClient.request(GatewayMethods.GROUPS_LIST, trackSession = false) as? JsonObject
            ?: return emptyList()
        return (result["rooms"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.toRoom() }
            .sortedByDescending { it.updatedAt }
    }

    override suspend fun room(roomId: String): Room? {
        val result = gatewayClient.request(
            GatewayMethods.GROUPS_STATE,
            mapOf("room_id" to JsonPrimitive(roomId)),
            trackSession = false,
        ) as? JsonObject
        return (result?.get("room") as? JsonObject)?.toRoom()
    }

    /** Seats [profiles] (2 to 6) in a new room called [name]. */
    override suspend fun create(name: String, profiles: List<HermesProfiles.Profile>): Room {
        val members = profiles.map { profile ->
            JsonObject(
                mapOf(
                    "member_id" to JsonPrimitive("m-${profile.name}"),
                    "profile" to JsonPrimitive(profile.name),
                    "handle" to JsonPrimitive(GroupRooms.handleFor(profile)),
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
    override suspend fun send(roomId: String, text: String, clientId: String) {
        gatewayClient.request(
            GatewayMethods.GROUPS_SEND,
            mapOf(
                "room_id" to JsonPrimitive(roomId),
                "event_id" to JsonPrimitive(clientId),
                "payload" to JsonObject(
                    mapOf("text" to JsonPrimitive(text), "thread_id" to JsonPrimitive(GroupRooms.MAIN_THREAD)),
                ),
            ),
            trackSession = false,
        )
    }

    /** The room log after [sinceSeq], all pages of it. */
    override suspend fun log(roomId: String, sinceSeq: Long): LogPage {
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

    override suspend fun status(roomId: String): Status? {
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
    override suspend fun stop(roomId: String) {
        gatewayClient.request(
            GatewayMethods.GROUPS_STOP,
            mapOf("room_id" to JsonPrimitive(roomId), "cancel_id" to JsonPrimitive("app-stop-${UUID.randomUUID()}")),
            trackSession = false,
        )
    }

    /** Answers a member's held command once: [allow] runs it this one time, otherwise it is refused. */
    override suspend fun approve(roomId: String, approval: Approval, allow: Boolean) {
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

    override suspend fun retry(roomId: String, taskId: String) {
        gatewayClient.request(
            GatewayMethods.GROUPS_RETRY,
            mapOf("room_id" to JsonPrimitive(roomId), "task_id" to JsonPrimitive(taskId)),
            trackSession = false,
        )
    }

    override suspend fun rename(roomId: String, name: String) {
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
    override suspend fun delete(roomId: String) {
        gatewayClient.request(
            GatewayMethods.GROUPS_DISBAND,
            mapOf("room_id" to JsonPrimitive(roomId)),
            trackSession = false,
        )
    }

    private companion object {
        const val LOG_PAGE = 200
        const val MAX_ROOM_NAME_CHARS = 200
        const val MAX_LABEL_CHARS = 200
    }
}

private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong()

private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

private fun JsonObject.toRoom(): GroupRooms.Room? {
    val id = str("room_id").ifEmpty { return null }
    if ((this["disbanded_at"] as? JsonPrimitive)?.contentOrNull != null) return null
    val members = (this["members"] as? JsonArray).orEmpty().mapNotNull { element ->
        val member = element as? JsonObject ?: return@mapNotNull null
        GroupRooms.Member(
            memberId = member.str("member_id"),
            profile = member.str("profile"),
            handle = member.str("handle"),
            displayName = member.str("display_name"),
        ).takeIf { it.memberId.isNotEmpty() }
    }
    return GroupRooms.Room(
        roomId = id,
        name = str("name").ifBlank { id },
        members = members,
        latestSeq = long("latest_seq") ?: 0,
        updatedAt = str("updated_at").toDoubleOrNull() ?: 0.0,
    )
}

private fun JsonObject.toEvent(): GroupRooms.Event? {
    val seq = long("seq") ?: return null
    val actor = this["actor"] as? JsonObject
    return GroupRooms.Event(
        seq = seq,
        kind = str("kind"),
        actorKind = actor?.str("kind").orEmpty(),
        actorId = actor?.str("id").orEmpty(),
        payload = this["payload"] as? JsonObject ?: JsonObject(emptyMap()),
        createdAt = str("created_at").toDoubleOrNull() ?: 0.0,
    )
}

private fun JsonObject.toApproval(): GroupRooms.Approval? {
    val approval = this["approval"] as? JsonObject
    val requestId = str("request_id").ifEmpty { approval?.str("request_id").orEmpty() }
    if (requestId.isEmpty()) return null
    return GroupRooms.Approval(
        memberId = str("member_id"),
        taskId = str("task_id"),
        executionGeneration = long("execution_generation")?.toInt() ?: 0,
        requestId = requestId,
        command = approval?.str("command").orEmpty(),
        description = approval?.str("description").orEmpty(),
    )
}
