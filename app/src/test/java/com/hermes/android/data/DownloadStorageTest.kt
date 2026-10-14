package com.hermes.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadStorageTest {

    @Test
    fun `name comes from the card, else from the URL`() {
        assertEquals("Pruna - Nomen 3.mp3", downloadFileName("file:///x/root/Pruna%20-%20Nomen%203.mp3", "Pruna - Nomen 3.mp3"))
        assertEquals("song.mp3", downloadFileName("file:///x/root/song.mp3", ""))
        assertEquals(
            "report.pdf",
            downloadFileName("http://10.0.0.2:9119/api/files/download?path=%2Froot%2Freport.pdf&token=abc", ""),
        )
    }

    @Test
    fun `a name without an extension takes the file's own`() {
        assertEquals("chart.png", downloadFileName("file:///x/root/plot.png", "chart"))
        assertEquals("Caddyfile", downloadFileName("file:///x/root/Caddyfile", ""))
    }

    @Test
    fun `unsafe names are cleaned`() {
        assertEquals("passwd", downloadFileName("http://h/a", "../../etc/passwd"))
        assertEquals("hermes_file", downloadFileName("http://h/", ".."))
    }

    @Test
    fun `same file is reused and a different one gets a free name`() {
        val taken = mutableMapOf("a.mp3" to "one")
        fun state(content: String): (String) -> NameState = { name ->
            when (taken[name]) {
                null -> NameState.Free
                content -> NameState.Same
                else -> NameState.Other
            }
        }
        assertEquals("a.mp3", freeName("a.mp3", state("one")))
        assertEquals("a (1).mp3", freeName("a.mp3", state("two")))
        taken["a (1).mp3"] = "three"
        assertEquals("a (2).mp3", freeName("a.mp3", state("two")))
        assertEquals("b", freeName("b") { NameState.Free })
    }
}
