package com.hermes.android.gateway

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The `source` Hermes files the app's sessions under. */
object SessionSource {
    /**
     * The user's chats. Without one Hermes files the session as "tui" and tells the model
     * there is no attachment channel ("this is a TUI, I can't send files"); "desktop" is its
     * graphical chat, where files arrive as MEDIA:/path and render.
     */
    const val CHAT = "desktop"

    /** Work launched from the Task Desk. */
    const val TASK = "pocket_task"
}

/**
 * Params for attaching to [sessionId] with [method], session.resume or session.activate.
 * session.resume takes the source from the request, not the stored chat: without it a
 * reopened chat came back as "tui" and the model said it could not send files.
 * session.activate has no `source` and rejects the request if it is sent
 * (tui_gateway/contracts/sessions.py: SessionActivateParams, extra inputs forbidden).
 */
fun sessionAttachParams(method: String, sessionId: String): Map<String, JsonElement> = buildJsonObject {
    put("session_id", sessionId)
    if (method == GatewayMethods.SESSION_RESUME) put("source", SessionSource.CHAT)
}.toMap()
