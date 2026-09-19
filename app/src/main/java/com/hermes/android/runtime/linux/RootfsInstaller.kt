package com.hermes.android.runtime.linux

import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Downloads, verifies and unpacks the Ubuntu Base rootfs used by [ProotLinuxRuntime]. */
@Singleton
class RootfsInstaller @Inject constructor(
    private val environment: ProotEnvironment,
) {

    /** [onProgress] receives a message and a 0–100 fraction of this step. */
    suspend fun install(onProgress: (String, Int) -> Unit) = withContext(Dispatchers.IO) {
        val archive = File(environment.baseDir, "rootfs.tar.gz")
        environment.baseDir.mkdirs()
        download(archive, onProgress)

        onProgress("Extracting Ubuntu…", 60)
        val staging = File(environment.baseDir, "rootfs.staging")
        deleteTree(staging)
        staging.mkdirs()
        GZIPInputStream(archive.inputStream().buffered(), 64 * 1024).use { extractTar(it, staging) }
        archive.delete()

        configure(staging)
        deleteTree(environment.rootfsDir)
        if (!staging.renameTo(environment.rootfsDir)) throw IOException("Could not move rootfs into place")
        environment.markRootfsReady()
        onProgress("Ubuntu is ready", 100)
    }

    private suspend fun download(target: File, onProgress: (String, Int) -> Unit) {
        if (target.isFile && sha256(target) == ROOTFS_SHA256) return
        val partial = File(target.path + ".part")
        val connection = URL(ROOTFS_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        try {
            if (connection.responseCode != 200) throw IOException("Rootfs download failed: HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 }
            var done = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val percent = total?.let { (done * 55 / it).toInt() } ?: 0
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress("Downloading Ubuntu (${done / 1_048_576} MB)…", percent)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (sha256(partial) != ROOTFS_SHA256) {
            partial.delete()
            throw IOException("Rootfs checksum mismatch — download was corrupted, try again")
        }
        partial.renameTo(target)
    }

    private fun configure(root: File) {
        File(root, "etc/resolv.conf").apply {
            delete()
            writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        }
        File(root, "etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost ip6-localhost ip6-loopback\n")
        // proot cannot switch to the unprivileged _apt user.
        File(root, "etc/apt/apt.conf.d/99hermes2").writeText(
            "APT::Sandbox::User \"root\";\nAcquire::Retries \"3\";\n",
        )
        File(root, "tmp").apply { mkdirs(); Os.chmod(path, 0b111_111_111 or 0x200) }
        File(root, "root").mkdirs()
    }

    private suspend fun extractTar(input: InputStream, destination: File) {
        val header = ByteArray(512)
        var longName: String? = null
        var longLink: String? = null
        val hardLinks = mutableListOf<Pair<File, File>>()

        while (true) {
            currentCoroutineContext().ensureActive()
            if (input.readFully(header) < 512 || header.all { it.toInt() == 0 }) break
            val size = header.octal(124, 12)
            val type = header[156].toInt().toChar()
            val name = longName ?: header.tarPath()
            val link = longLink ?: header.string(157, 100)
            longName = null
            longLink = null

            when (type) {
                'L' -> { longName = input.readString(size); continue }
                'K' -> { longLink = input.readString(size); continue }
                'x' -> {
                    val pax = parsePax(input.readString(size))
                    longName = pax["path"]
                    longLink = pax["linkpath"]
                    continue
                }
                'g' -> { input.skipFully(padded(size)); continue }
            }

            val relative = name.removePrefix("./").trimEnd('/')
            if (relative.isEmpty()) { input.skipFully(padded(size)); continue }
            if (relative.startsWith("/") || relative.split('/').any { it == ".." }) {
                throw IOException("Refusing to extract outside rootfs: $name")
            }
            val out = File(destination, relative)
            val mode = header.octal(100, 8).toInt() and 0xFFF

            when (type) {
                '5' -> { out.mkdirs(); Os.chmod(out.path, mode or 0x1C0) }
                '2' -> {
                    out.parentFile?.mkdirs()
                    out.delete()
                    Os.symlink(link, out.path)
                }
                '1' -> hardLinks += out to File(destination, link.removePrefix("./"))
                '0', '\u0000', '7' -> {
                    out.parentFile?.mkdirs()
                    out.delete()
                    out.outputStream().use { output -> input.copyExactly(output, size) }
                    input.skipFully(padded(size) - size)
                    Os.chmod(out.path, mode or 0x180)
                }
                else -> input.skipFully(padded(size)) // device nodes, fifos: not needed under proot
            }
        }
        for ((linkFile, targetFile) in hardLinks) {
            linkFile.parentFile?.mkdirs()
            linkFile.delete()
            runCatching { Os.link(targetFile.path, linkFile.path) }
                .onFailure { targetFile.copyTo(linkFile, overwrite = true) }
        }
    }

    private fun parsePax(text: String): Map<String, String> =
        text.lineSequence().mapNotNull { line ->
            val record = line.substringAfter(' ', "")
            val eq = record.indexOf('=')
            if (eq <= 0) null else record.substring(0, eq) to record.substring(eq + 1)
        }.toMap()

    private fun padded(size: Long): Long = (size + 511) / 512 * 512

    private fun ByteArray.string(offset: Int, length: Int): String {
        var end = offset
        while (end < offset + length && this[end].toInt() != 0) end++
        return String(this, offset, end - offset, Charsets.UTF_8)
    }

    private fun ByteArray.tarPath(): String {
        val prefix = string(345, 155)
        val name = string(0, 100)
        return if (prefix.isEmpty()) name else "$prefix/$name"
    }

    private fun ByteArray.octal(offset: Int, length: Int): Long =
        string(offset, length).trim().takeIf { it.isNotEmpty() }?.toLong(8) ?: 0L

    private fun InputStream.readFully(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    private fun InputStream.readString(size: Long): String {
        val bytes = ByteArray(size.toInt())
        if (readFully(bytes) < bytes.size) throw IOException("Truncated tar entry")
        skipFully(padded(size) - size)
        return String(bytes, Charsets.UTF_8).trimEnd('\u0000', '\n')
    }

    private fun InputStream.copyExactly(output: java.io.OutputStream, size: Long) {
        val buffer = ByteArray(64 * 1024)
        var remaining = size
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw IOException("Truncated tar entry")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun InputStream.skipFully(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) remaining -= skipped
            else if (read() < 0) throw IOException("Truncated tar entry") else remaining--
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val ROOTFS_URL =
            "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-arm64.tar.gz"
        private const val ROOTFS_SHA256 = "a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2"
    }
}

/** Recursive delete that never follows symbolic links (the rootfs has links like /dev/fd). */
internal fun deleteTree(root: File) {
    val path = root.toPath()
    if (!java.nio.file.Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
    java.nio.file.Files.walkFileTree(path, object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
        override fun visitFile(file: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes) =
            java.nio.file.FileVisitResult.CONTINUE.also { java.nio.file.Files.delete(file) }

        override fun visitFileFailed(file: java.nio.file.Path, exc: IOException) =
            java.nio.file.FileVisitResult.CONTINUE.also { java.nio.file.Files.deleteIfExists(file) }

        override fun postVisitDirectory(dir: java.nio.file.Path, exc: IOException?) =
            java.nio.file.FileVisitResult.CONTINUE.also { java.nio.file.Files.delete(dir) }
    })
}
