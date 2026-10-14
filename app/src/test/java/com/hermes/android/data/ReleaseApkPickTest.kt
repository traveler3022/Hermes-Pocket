package com.hermes.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which release asset the app downloads, and which responses can be an APK at all. */
class ReleaseApkPickTest {

    private fun asset(name: String, uploaded: Boolean = true) = ReleaseAsset(
        name = name,
        url = "https://github.com/traveler3022/Hermes-Pocket/releases/download/v3.1.0/$name",
        size = 33_201_200,
        contentType = "application/vnd.android.package-archive",
        uploaded = uploaded,
    )

    @Test
    fun `app-release apk is picked over other apks`() {
        val picked = pickReleaseApk(listOf(asset("notes.txt"), asset("other.apk"), asset("app-release.apk")))
        assertEquals("app-release.apk", picked?.name)
        assertEquals(33_201_200L, picked?.size)
    }

    @Test
    fun `debug apks, unfinished uploads and plain http are never picked`() {
        assertNull(pickReleaseApk(listOf(asset("app-debug.apk"))))
        assertNull(pickReleaseApk(listOf(asset("app-release.apk", uploaded = false))))
        assertNull(pickReleaseApk(listOf(asset("app-release.apk").copy(url = "http://example.com/app-release.apk"))))
        assertNull(pickReleaseApk(emptyList()))
    }

    @Test
    fun `binary content types pass, pages do not`() {
        assertTrue(isApkContentType("application/vnd.android.package-archive"))
        assertTrue(isApkContentType("application/octet-stream"))
        assertTrue(isApkContentType(null))
        assertFalse(isApkContentType("text/html; charset=utf-8"))
        assertFalse(isApkContentType("application/json"))
    }
}
