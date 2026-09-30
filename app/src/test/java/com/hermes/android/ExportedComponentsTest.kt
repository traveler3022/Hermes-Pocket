package com.hermes.android

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * What other apps can reach, as the manifest declares it. Each of these closed a way for any
 * app to read the built-in Linux (API keys in ~/.hermes/.env) or to let a program out of it.
 */
class ExportedComponentsTest {

    // Gradle runs unit tests from the module dir; an IDE may run them from the repo root.
    private val components: List<Element> by lazy {
        val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .firstOrNull { it.exists() } ?: error("AndroidManifest.xml not found")
        val application = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifest)
            .getElementsByTagName("application").item(0) as Element
        (0 until application.childNodes.length).mapNotNull { application.childNodes.item(it) as? Element }
    }

    private fun component(name: String): Element =
        components.firstOrNull { it.android("name") == name } ?: error("$name is not in the manifest")

    private fun Element.android(attribute: String): String = getAttributeNS(ANDROID, attribute)

    /** It opens any URL it is given, and hands what it can't show to other apps with a read grant. */
    @Test
    fun `the file viewer is not exported`() {
        assertEquals("false", component(".ui.viewer.FileViewerActivity").android("exported"))
    }

    /** The Files app's way in; launched through it the viewer takes only a Linux Files document. */
    @Test
    fun `other apps reach the viewer only through the Linux Files alias`() {
        val ways = components.filter { it.android("targetActivity") == ".ui.viewer.FileViewerActivity" }
        assertEquals(listOf(".ui.viewer.LinuxFileViewer"), ways.map { it.android("name") })
    }

    /** Approving a program is the user's own tap inside Hermes. */
    @Test
    fun `the program warning is not exported`() {
        assertEquals("false", component(".ui.viewer.FileGateActivity").android("exported"))
    }

    @Test
    fun `only the system's Files app binds to the Linux Files provider`() {
        assertEquals(
            "android.permission.MANAGE_DOCUMENTS",
            component(".runtime.linux.LinuxFilesProvider").android("permission"),
        )
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
