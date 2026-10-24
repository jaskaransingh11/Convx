package com.convx.storage

import com.convx.storage.data.FlacMetadataReader
import com.convx.storage.domain.RemoteFile
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class FlacMetadataReaderTest {
    private class MemoryFile(private val data: ByteArray) : RemoteFile {
        override val length = data.size.toLong()
        override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= data.size) return -1
            val count = minOf(length, 3, data.size - position.toInt()) // exercise short network reads
            data.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
            return count
        }
        override fun close() = Unit
    }
    private fun fixture(): ByteArray {
        val output = ByteArrayOutputStream()
        output.write("fLaC".toByteArray())
        output.write(byteArrayOf(0, 0, 0, 34))
        val stream = ByteArray(34)
        val packed = (48_000L shl 44) or (1L shl 41) or (23L shl 36) or 8_640_000L
        ByteBuffer.wrap(stream, 10, 8).putLong(packed)
        output.write(stream)
        val comments = ByteArrayOutputStream()
        fun number(n: Int) { comments.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array()) }
        fun string(s: String) { val b = s.toByteArray(); number(b.size); comments.write(b) }
        string("Convx fixture")
        val tags = listOf("TITLE=Song", "ARTIST=One", "ARTIST=Two", "ALBUM=Album", "ISRC=USABC2600001", "VERSION=Studio")
        number(tags.size); tags.forEach(::string)
        val block = comments.toByteArray()
        output.write(byteArrayOf(0x84.toByte(), 0, (block.size shr 8).toByte(), block.size.toByte()))
        output.write(block)
        return output.toByteArray()
    }
    @Test fun parsesDurationAndRepeatedArtistsFromShortReads() {
        val tags = FlacMetadataReader.read(MemoryFile(fixture()))
        assertEquals(180_000L, tags.durationMs)
        assertEquals(48_000, tags.sampleRate)
        assertEquals(24, tags.bitsPerSample)
        assertEquals(listOf("One", "Two"), tags.artists)
        assertEquals("USABC2600001", tags.isrc)
    }
    @Test(expected = java.io.IOException::class) fun rejectsTruncatedHeader() { FlacMetadataReader.read(MemoryFile(fixture().copyOf(45))) }
    @Test(expected = java.io.IOException::class) fun rejectsNonFlac() { FlacMetadataReader.read(MemoryFile("RIFFwave".toByteArray())) }
    @Test(expected = java.io.IOException::class) fun rejectsOversizedCommentBeforeAllocation() {
        val data = fixture(); data[40] = 0x7f; data[41] = 0xff.toByte()
        FlacMetadataReader.read(MemoryFile(data))
    }
}
