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

    @Test
    fun `the test build picks app-debug apk and marks it`() {
        val picked = pickTestBuildApk(listOf(asset("notes.txt"), asset("app-debug.apk")))
        assertEquals("app-debug.apk", picked?.name)
        assertTrue(picked?.isTestBuild == true)
        assertNull(pickTestBuildApk(listOf(asset("app-release.apk"))))
        assertFalse(pickReleaseApk(listOf(asset("app-release.apk")))?.isTestBuild ?: true)
    }

    @Test
    fun `debug-latest notes give branch, commit and time`() {
        val build = parseTestBuild("Branch: claude/pairing\r\nCommit: 1FCE48B9\nBuilt: 2026-10-14 02:40 UTC")
        assertEquals("claude/pairing", build?.branch)
        assertEquals("1fce48b9", build?.commit)
        assertEquals("2026-10-14 02:40 UTC", build?.built)
        assertNull(parseTestBuild("Branch: main\nBuilt: now"))
        assertNull(parseTestBuild("Commit: not-a-sha"))
    }

    @Test
    fun `commits match by prefix, an unknown commit never matches`() {
        assertTrue(sameCommit("1fce48b9", "1FCE48B9"))
        assertTrue(sameCommit("1fce48b9", "1fce48b9c0ffee"))
        assertFalse(sameCommit("1fce48b9", "2fce48b9"))
        assertFalse(sameCommit("1fce48b9", ""))
    }
}
