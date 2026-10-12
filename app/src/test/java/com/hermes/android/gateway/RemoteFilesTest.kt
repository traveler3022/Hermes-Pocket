package com.hermes.android.gateway

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** [RemoteFiles] against a stand-in for Hermes' FileResponse: Range, If-Range, ETag, 416. */
class RemoteFilesTest {

    private val dir: File = Files.createTempDirectory("remote-files").toFile()
    private val server = FakeServer(ByteArray(200_000) { (it % 251).toByte() }, "\"v1\"")
    private val context = mockk<Context> { every { cacheDir } returns dir }
    private val files = RemoteFiles(context, OkHttpClient.Builder().addInterceptor(server).build())
    private val url = "https://box.ts.net/api/files/download?path=%2Froot%2Fout%2Fplot.png"

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `a file is fetched whole under its own name`() = runBlocking {
        val file = files.fetch(url)
        assertEquals("plot.png", file.name)
        assertArrayEquals(server.content, file.readBytes())
    }

    @Test
    fun `a cut transfer goes on from where it stopped`() = runBlocking {
        server.cutAfter = 70_000
        val file = files.fetch(url)
        assertArrayEquals(server.content, file.readBytes())
        assertEquals(2, server.requests.size)
        assertEquals("bytes=70000-", server.requests[1].header("Range"))
        assertEquals("\"v1\"", server.requests[1].header("If-Range"))
    }

    @Test
    fun `an unchanged copy costs one byte`() = runBlocking {
        files.fetch(url)
        server.requests.clear()
        server.bytesSent = 0
        val file = files.fetch(url)
        assertArrayEquals(server.content, file.readBytes())
        assertEquals(listOf("bytes=0-0"), server.requests.map { it.header("Range") })
        assertEquals(1L, server.bytesSent)
    }

    @Test
    fun `a changed file replaces the copy`() = runBlocking {
        files.fetch(url)
        server.content = ByteArray(50_000) { 7 }
        server.etag = "\"v2\""
        val file = files.fetch(url)
        assertArrayEquals(server.content, file.readBytes())
    }

    @Test
    fun `a changed file cut on its way in is finished, not kept half`() = runBlocking {
        files.fetch(url)
        server.content = ByteArray(150_000) { 9 }
        server.etag = "\"v2\""
        server.cutAfter = 40_000
        val file = files.fetch(url)
        assertArrayEquals(server.content, file.readBytes())
        // The next look must not take the old copy for current under the new tag.
        server.requests.clear()
        assertArrayEquals(server.content, files.fetch(url).readBytes())
        assertEquals(listOf("bytes=0-0"), server.requests.map { it.header("Range") })
    }

    @Test
    fun `without the server the copy on disk is used`() = runBlocking {
        files.fetch(url)
        server.offline = true
        assertArrayEquals(server.content, files.fetch(url).readBytes())
    }

    @Test
    fun `the token in a link is not part of the file`() = runBlocking {
        val first = files.fetch("http://127.0.0.1:9119/api/files/download?path=%2Froot%2Fa.txt&token=one")
        val second = files.fetch("http://127.0.0.1:9119/api/files/download?path=%2Froot%2Fa.txt&token=two")
        assertEquals(first, second)
    }

    @Test
    fun `only the server's download endpoint is a server file`() {
        assertTrue(files.isServerFile(url))
        assertFalse(files.isServerFile("https://picsum.photos/400"))
        assertFalse(files.isServerFile("https://box.ts.net/api/files/download"))
        assertFalse(files.isServerFile("file:///data/rootfs/root/plot.png"))
    }

    private class FakeServer(var content: ByteArray, var etag: String) : Interceptor {
        val requests = mutableListOf<Request>()
        var cutAfter: Int? = null
        var offline = false
        var bytesSent = 0L

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += request
            if (offline) throw IOException("offline")
            val builder = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("")
                .header("ETag", etag)
            val range = request.header("Range")
            val ifRange = request.header("If-Range")
            var start = 0
            var end = content.size - 1
            var partial = false
            if (range != null && (ifRange == null || ifRange == etag)) {
                val match = Regex("""bytes=(\d+)-(\d*)""").find(range) ?: error("bad range $range")
                start = match.groupValues[1].toInt()
                if (start >= content.size) {
                    return builder.code(416).header("Content-Range", "bytes */${content.size}")
                        .body(ByteArray(0).toResponseBody()).build()
                }
                end = match.groupValues[2].toIntOrNull()?.coerceAtMost(content.size - 1) ?: end
                partial = true
            }
            val slice = content.copyOfRange(start, end + 1)
            if (partial) builder.code(206).header("Content-Range", "bytes $start-$end/${content.size}") else builder.code(200)
            val cut = cutAfter?.also { cutAfter = null }
            bytesSent += cut?.coerceAtMost(slice.size) ?: slice.size
            return builder.body(if (cut != null) cutBody(slice, cut) else slice.toResponseBody()).build()
        }

        /** Declares all of [bytes] but delivers [cutAfter] of them, then the connection drops. */
        private fun cutBody(bytes: ByteArray, cutAfter: Int): ResponseBody {
            val delivered = Buffer().write(bytes, 0, cutAfter)
            val source = object : Source {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (delivered.size == 0L) throw IOException("connection reset")
                    return delivered.read(sink, byteCount)
                }
                override fun timeout(): Timeout = Timeout.NONE
                override fun close() = Unit
            }
            return source.buffer().asResponseBody(null, bytes.size.toLong())
        }
    }
}
