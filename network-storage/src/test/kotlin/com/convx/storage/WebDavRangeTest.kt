package com.convx.storage

import com.convx.storage.data.WebDavFileSource
import com.convx.storage.domain.NetworkServer
import com.convx.storage.domain.StorageException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class WebDavRangeTest {
    @Test fun exactPartialContentSupportsSeekingAndSequentialReads() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(206)
                    .setHeader("Content-Range", "bytes 4-9/10")
                    .setBody("456789")
            )
            val dav = WebDavFileSource()
            val source = NetworkServer.WebDav("test", "Test fixture", server.url("/music/").toString())
            dav.open(source, "track.flac").use { file ->
                val buffer = ByteArray(3)
                assertEquals(3, file.read(4, buffer, 0, buffer.size))
                assertEquals("456", buffer.toString(Charsets.UTF_8))
                assertEquals(10L, file.length)
                assertEquals(3, file.read(7, buffer, 0, buffer.size))
                assertEquals("789", buffer.toString(Charsets.UTF_8))
            }
            val request = server.takeRequest()
            assertEquals("bytes=4-", request.getHeader("Range"))
            assertEquals("/music/track.flac", request.path)
            dav.close()
        }
    }

    @Test fun serverIgnoringRangeCanResumeBySkipping() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("0123456789"))
            val dav = WebDavFileSource()
            val source = NetworkServer.WebDav("test", "Test fixture", server.url("/music/").toString())
            dav.open(source, "track.flac").use { file ->
                val buffer = ByteArray(6)
                assertEquals(6, file.read(4, buffer, 0, buffer.size))
                assertEquals("456789", buffer.toString(Charsets.UTF_8))
                assertEquals(10L, file.length)
            }
            assertEquals("bytes=4-", server.takeRequest().getHeader("Range"))
        }
    }

    @Test(expected = StorageException::class)
    fun rejectsContentRangeForWrongSeekPosition() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(206)
                    .setHeader("Content-Range", "bytes 0-5/10")
                    .setBody("012345")
            )
            val dav = WebDavFileSource()
            val source = NetworkServer.WebDav("test", "Test fixture", server.url("/music/").toString())
            dav.open(source, "track.flac").use { file ->
                file.read(4, ByteArray(2), 0, 2)
            }
        }
    }
}
