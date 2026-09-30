package com.hermes.android.service

import android.net.Uri
import androidx.media3.common.MediaItem
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlaybackServiceTest {

    @Test
    fun `an item's id is a digest that leaves the gateway token out`() {
        val url = "https://gateway.example/files/song.mp3?token=s3cret"
        val id = AudioPlaybackService.itemId(url)
        assertFalse(id.contains("s3cret"))
        assertTrue(id.matches(Regex("[0-9a-f]{64}")))
        assertEquals(id, AudioPlaybackService.itemId(url))
        assertNotEquals(id, AudioPlaybackService.itemId("https://gateway.example/files/other.mp3?token=s3cret"))
    }

    @Test
    fun `only Hermes adds items, each with its URI`() {
        val hermes = "com.hermes.android"
        val withUri = MediaItem.Builder().setUri(mockk<Uri>()).build()
        val idOnly = MediaItem.Builder().setMediaId("file:///data/data/com.hermes.android/files/secret").build()
        assertTrue(AudioPlaybackService.mayAddItems(hermes, hermes, listOf(withUri)))
        assertFalse(AudioPlaybackService.mayAddItems("com.other.app", hermes, listOf(withUri)))
        assertFalse(AudioPlaybackService.mayAddItems(hermes, hermes, listOf(withUri, idOnly)))
    }
}
