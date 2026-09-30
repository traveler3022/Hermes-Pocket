package com.hermes.android.runtime.linux

import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The check a file of the built-in Linux passes on its way out to the phone (the Files app,
 * "Open with another app"). Anything the agent downloaded may be malware, and inside the Linux
 * it can only reach Hermes; on the phone only a program can do harm. So a program leaves only
 * after the user confirms it, and a program named like a photo or a document never leaves.
 * Decided by the file's bytes, not its name.
 */
@Singleton
class FileGate @Inject constructor() {

    enum class Risk { SAFE, PROGRAM, DISGUISED }

    private val approvals = ConcurrentHashMap<String, Long>()

    fun riskOf(file: File): Risk {
        if (!isProgram(file)) return Risk.SAFE
        val ext = file.name.substringAfterLast('.', "").lowercase()
        return if (ext in PassiveExtensions) Risk.DISGUISED else Risk.PROGRAM
    }

    /** The user confirmed [file]: other apps may read it for a while, as long as it is unchanged. */
    fun approve(file: File, now: Long = System.currentTimeMillis()) {
        approvals[key(file)] = now + APPROVAL_MILLIS
    }

    fun isApproved(file: File, now: Long = System.currentTimeMillis()): Boolean {
        val until = approvals[key(file)] ?: return false
        return now < until
    }

    /**
     * Why another app may not have [file] now, or null when it may: data always leaves, a
     * program only while the user's confirmation lasts, a disguised program never. The one
     * rule every way out of the Linux (the Files app, "Open with another app") applies.
     */
    fun heldBack(file: File, now: Long = System.currentTimeMillis()): Risk? {
        val risk = riskOf(file)
        return risk.takeUnless { it == Risk.SAFE || (it == Risk.PROGRAM && isApproved(file, now)) }
    }

    /** Path, size and time: a file swapped after the user confirmed it needs a new confirmation. */
    private fun key(file: File): String {
        val real = runCatching { file.canonicalFile }.getOrDefault(file)
        return "${real.path}|${real.length()}|${real.lastModified()}"
    }

    private fun isProgram(file: File): Boolean {
        val head = ByteArray(4)
        val read = runCatching { file.inputStream().use { it.read(head) } }.getOrDefault(-1)
        if (read < 2) return false
        return when {
            read >= 4 && head.startsWith(0x7F, 'E'.code, 'L'.code, 'F'.code) -> true // Linux/Android binary
            read >= 4 && head.startsWith('d'.code, 'e'.code, 'x'.code, '\n'.code) -> true // Android bytecode
            head.startsWith('M'.code, 'Z'.code) -> isWindowsProgram(file)
            read >= 4 && head.startsWith('P'.code, 'K'.code, 3, 4) -> zipHoldsApp(file)
            else -> false
        }
    }

    /** "MZ" alone could open a text file; a program also has "PE\0\0" where offset 0x3C points. */
    private fun isWindowsProgram(file: File): Boolean = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 0x40) return@use false
            raf.seek(0x3C)
            val offset = Integer.reverseBytes(raf.readInt()).toLong() and 0xFFFFFFFFL
            if (offset + 4 > raf.length()) return@use false
            raf.seek(offset)
            val sig = ByteArray(4).also(raf::readFully)
            sig.startsWith('P'.code, 'E'.code, 0, 0)
        }
    }.getOrDefault(false)

    /** An APK is a zip with AndroidManifest.xml; APKS/XAPK/APKM and plain zips carry .apk files. */
    private fun zipHoldsApp(file: File): Boolean = runCatching {
        ZipFile(file).use { zip ->
            zip.entries().asSequence().any { entry ->
                val name = entry.name
                name == "AndroidManifest.xml" || name.endsWith(".apk", ignoreCase = true) ||
                    name.endsWith(".dex", ignoreCase = true)
            }
        }
    }.getOrDefault(false)

    private fun ByteArray.startsWith(vararg bytes: Int): Boolean =
        bytes.indices.all { this[it] == bytes[it].toByte() }

    private companion object {
        /** How long a confirmed program may be read by other apps (a copy, an install). */
        const val APPROVAL_MILLIS = 10 * 60 * 1000L

        /** Names of files that are only data: a program under one of these is hiding. */
        val PassiveExtensions = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "svg",
            "mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v",
            "mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac", "amr",
            "pdf", "txt", "md", "csv", "json", "html", "htm", "xml", "log",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "epub",
        )
    }
}
