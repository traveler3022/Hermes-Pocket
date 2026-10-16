package com.hermes.android.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTextTest {

    @Test
    fun `sentences come out as they complete and code stays silent`() {
        val buffer = IncrementalSpeechSentenceBuffer()
        assertEquals(emptyList<String>(), buffer.append("Here is the plan for today"))
        val first = buffer.append(", step by step. Then ")
        assertEquals(listOf("Here is the plan for today, step by step."), first)
        assertEquals(emptyList<String>(), buffer.append("run this:\n```\nrm -rf build. Done.\n"))
        val tail = buffer.append("```\nand we are finished here.") + buffer.flush()
        assertEquals(listOf("Then run this. and we are finished here."), tail)
    }

    @Test
    fun `persian question mark ends a sentence`() {
        val (sentences, rest) = cutSentences("امروز هوا چطوره و برنامه چیه؟ بعدش", flush = false)
        assertEquals(listOf("امروز هوا چطوره و برنامه چیه؟"), sentences)
        assertEquals("بعدش", rest)
    }

    @Test
    fun `markdown links and files are not read out`() {
        assertEquals("See the docs for details.", sanitizeTextForSpeech("See the [docs](https://x.y/z) for `details`."))
        assertFalse(sanitizeTextForSpeech("Saved report-2026-10-16.xlsx now").contains("xlsx"))
    }

    @Test
    fun `echo of the spoken reply is recognised, a real reply is not`() {
        val spoken = "The weather in Tehran today is sunny with a high of twenty five degrees."
        assertTrue(isTtsEcho("weather in tehran today is sunny", spoken))
        assertFalse(isTtsEcho("no I meant tomorrow", spoken))
    }

    @Test
    fun `stop and hold phrases match only whole utterances`() {
        assertTrue(isVoiceStopCommand("بسه"))
        assertTrue(isVoiceStopCommand("Hermes, stop!"))
        assertFalse(isVoiceStopCommand("stop the docker container"))
        assertTrue(isVoiceHoldCommand("وایسا"))
        assertFalse(isVoiceHoldCommand("وایسا ببینم فایل کجاست"))
    }
}
