package com.hermes.android.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Version comparison is where an update check fails silently: get it wrong and
 * the app simply never mentions a release, with nothing to notice.
 */
class VersionCompareTest {

    @Test
    fun `a higher patch, minor or major is newer`() {
        assertTrue(isNewer("1.0.1", "1.0.0"))
        assertTrue(isNewer("1.1.0", "1.0.9"))
        assertTrue(isNewer("2.0.0", "1.9.9"))
    }

    @Test
    fun `the same version is not newer`() {
        assertFalse(isNewer("1.2.3", "1.2.3"))
    }

    @Test
    fun `an older version is not newer`() {
        assertFalse(isNewer("1.0.0", "1.0.1"))
        assertFalse(isNewer("1.9.9", "2.0.0"))
    }

    /** The case string comparison gets wrong: "1.10.0" < "1.9.0" as text. */
    @Test
    fun `double-digit segments compare as numbers, not as text`() {
        assertTrue(isNewer("1.10.0", "1.9.0"))
        assertFalse(isNewer("1.9.0", "1.10.0"))
        assertTrue(isNewer("1.0.10", "1.0.9"))
    }

    @Test
    fun `a leading v is ignored on either side`() {
        assertTrue(isNewer("v1.1.0", "1.0.0"))
        assertFalse(isNewer("v1.0.0", "v1.0.0"))
    }

    @Test
    fun `missing segments count as zero`() {
        assertFalse(isNewer("1.2", "1.2.0"))
        assertTrue(isNewer("1.3", "1.2.9"))
    }

    @Test
    fun `a suffix after the numbers is ignored`() {
        assertFalse(isNewer("1.0.0", "1.0.0-debug"))
        assertTrue(isNewer("1.0.1", "1.0.0-debug"))
    }

    /** A version that cannot be read must not produce a nag. */
    @Test
    fun `unparseable versions are never newer`() {
        assertFalse(isNewer("nightly", "1.0.0"))
        assertFalse(isNewer("1.0.0", ""))
        assertFalse(isNewer("", "1.0.0"))
    }
}
