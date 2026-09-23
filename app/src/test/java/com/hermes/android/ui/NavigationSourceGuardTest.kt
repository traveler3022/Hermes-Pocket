package com.hermes.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards for three render/navigation choices that live only in source and have
 * no behaviour a plain JVM test can drive. Each one broke or cost something before.
 */
class NavigationSourceGuardTest {

    // Gradle runs unit tests from the module dir; an IDE may run them from the repo root.
    private fun source(path: String): File =
        listOf(File(path), File("app/$path")).firstOrNull { it.exists() }
            ?: error("not found: $path")

    /**
     * After a pop the leaving screen stays on top for the whole fade and still takes
     * taps. A bare popBackStack() there let a tap on the chat's hamburger hit Settings'
     * back arrow and pop `chat` as well — an empty NavHost, a black screen.
     */
    @Test
    fun everyBackArrowIgnoresTapsWhileItsScreenFadesOut() {
        val nav = source("src/main/java/com/hermes/android/MainActivity.kt").readText()
        val backs = Regex("""onNavigateBack\s*=\s*([^\n]*)""").findAll(nav).map { it.groupValues[1] }.toList()
        assertTrue("no back arrows found — did the NavHost move?", backs.isNotEmpty())
        val unguarded = backs.filterNot { it.startsWith("dropUnlessResumed") }
        assertEquals("back arrows without dropUnlessResumed", emptyList<String>(), unguarded)
    }

    /** Rows of different kinds (user, reply, tool card…) must not be recycled into each other. */
    @Test
    fun chatListTellsRowsApartByKind() {
        val chat = source("src/main/java/com/hermes/android/ui/screen/ChatScreen.kt").readText()
        val call = Regex("""itemsIndexed\(\s*visibleMessages,[^)]*\)""").find(chat)?.value
        assertTrue("chat list call not found", call != null)
        assertTrue("chat list lost its contentType: $call", call!!.contains("contentType = { _, m -> m::class }"))
    }

    /** The baseline profile must still cover the app's own package after any move or rename. */
    @Test
    fun baselineProfileCoversTheAppPackage() {
        val rules = source("src/main/baseline-prof.txt").readLines()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        assertTrue("no rule for com/hermes/android in $rules", "HSPLcom/hermes/android/**->**(**)**" in rules)
        assertTrue(
            "app package moved; update baseline-prof.txt",
            source("src/main/java/com/hermes/android/MainActivity.kt").exists(),
        )
    }
}
