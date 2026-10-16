package com.hermes.android.voice

import java.text.Normalizer

/*
 * Spoken stop command, ported from Hermes Desktop (apps/desktop/src/lib/voice-stop-word.ts, MIT,
 * Nous Research): only a WHOLE utterance that is a stop phrase ends the conversation, so
 * "stop the container" still goes to Hermes. Persian phrases added beside the English list.
 */

private val STOP_PHRASES = listOf(
    "stop", "stop listening", "stop it", "stop please", "please stop", "stop stop", "that is all", "that's all",
    "never mind", "nevermind", "end conversation", "end the conversation", "goodbye", "good bye", "bye", "cancel",
    "بسه", "بس کن", "کافیه", "کافی است", "تمومش کن", "تموم", "خداحافظ", "خدانگهدار", "ولش کن", "بیخیال",
    "پایان", "تمام", "قطع کن", "تمومه",
)

private val ADDRESS_PREFIXES = listOf("hey hermes", "hermes", "ok", "okay", "hey", "هرمس", "هی هرمس")

private fun normalize(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase()
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace("‌", " ")
        .replace(Regex("\\p{P}+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun stripAddress(text: String): String {
    for (prefix in ADDRESS_PREFIXES) {
        if (text == prefix) continue
        if (text.startsWith("$prefix ")) return text.substring(prefix.length + 1).trim()
    }
    return text
}

private val NORMALIZED_PHRASES = STOP_PHRASES.map(::normalize).toSet()

/** True when the whole [transcript] is a stop command, optionally addressed to Hermes. */
fun isVoiceStopCommand(transcript: String): Boolean {
    val normalized = normalize(transcript)
    if (normalized.isEmpty()) return false
    return normalized in NORMALIZED_PHRASES || stripAddress(normalized) in NORMALIZED_PHRASES
}

/*
 * "Wait": stop talking but stay in the conversation. Not in the desktop, where the barge-in
 * itself is the pause; here a lone "وایسا" would otherwise go to Hermes as a new turn.
 */
private val HOLD_PHRASES = listOf(
    "wait", "hold on", "one sec", "one second", "pause", "shh", "quiet",
    "وایسا", "واستا", "صبر کن", "یه لحظه", "یک لحظه", "یه دقیقه", "ساکت", "هیس", "گوش کن",
).map(::normalize).toSet()

/** True when the whole [transcript] only asks Hermes to stop talking and listen. */
fun isVoiceHoldCommand(transcript: String): Boolean {
    val normalized = normalize(transcript)
    if (normalized.isEmpty()) return false
    return normalized in HOLD_PHRASES || stripAddress(normalized) in HOLD_PHRASES
}
