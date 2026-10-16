package com.hermes.android.data

import android.util.Base64
import com.hermes.android.domain.voice.VoiceAudio
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayMethods
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** [VoiceAudio] over `/api/audio/transcribe` and `/api/audio/speak` (hermes_cli/web_routers/audio.py). */
@Singleton
class VoiceAudioRepository @Inject constructor(
    private val dashboard: DashboardApi,
    private val gateway: GatewayClient,
) : VoiceAudio {

    override suspend fun serverSttAvailable(): Boolean {
        val result = gateway.request(
            GatewayMethods.VOICE_TOGGLE,
            mapOf("action" to JsonPrimitive("status")),
            trackSession = false,
        ) as? JsonObject ?: return false
        return (result["stt_available"] as? JsonPrimitive)?.booleanOrNull == true
    }

    override suspend fun transcribe(wav: ByteArray): String {
        val encoded = Base64.encodeToString(wav, Base64.NO_WRAP)
        val answer = dashboard.call("POST", "/api/audio/transcribe", buildJsonObject {
            put("data_url", "data:audio/wav;base64,$encoded")
            put("mime_type", "audio/wav")
        }, slow = true) as? JsonObject ?: throw IOException("No answer from Hermes")
        return (answer["transcript"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
    }

    override suspend fun speak(text: String): VoiceAudio.SpokenAudio {
        val answer = dashboard.call("POST", "/api/audio/speak", buildJsonObject { put("text", text) }) as? JsonObject
            ?: throw IOException("No answer from Hermes")
        val dataUrl = (answer["data_url"] as? JsonPrimitive)?.contentOrNull
            ?: throw IOException("Hermes sent no audio")
        val comma = dataUrl.indexOf(',')
        if (!dataUrl.startsWith("data:") || comma < 0) throw IOException("Hermes sent no audio")
        val mime = (answer["mime_type"] as? JsonPrimitive)?.contentOrNull
            ?: dataUrl.substring(5, comma).substringBefore(';')
        return VoiceAudio.SpokenAudio(Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT), mime)
    }
}
