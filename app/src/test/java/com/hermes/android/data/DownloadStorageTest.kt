package com.hermes.android.data

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadStorageTest {

    @get:Rule val tmp = TemporaryFolder()

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
        val dir = tmp.newFolder()
        java.io.File(dir, "a.mp3").writeText("one")
        assertEquals("a.mp3", freeTarget(dir, "a.mp3") { it.readText() == "one" }.name)
        assertEquals("a (1).mp3", freeTarget(dir, "a.mp3") { it.readText() == "two" }.name)
        java.io.File(dir, "a (1).mp3").writeText("three")
        assertEquals("a (2).mp3", freeTarget(dir, "a.mp3") { it.readText() == "two" }.name)
        assertEquals("b", freeTarget(dir, "b") { false }.name)
    }
}
