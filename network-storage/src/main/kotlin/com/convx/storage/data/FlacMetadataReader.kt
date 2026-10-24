package com.convx.storage.data

import com.convx.storage.domain.RecordingMetadata
import com.convx.storage.domain.RemoteFile
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads metadata blocks only; never downloads audio or allocates an unbounded tag/picture block. */
object FlacMetadataReader {
    private const val MAX_BLOCK_BYTES = 1_048_576
    private const val MAX_HEADER_BYTES = 16_777_216L

    fun read(file: RemoteFile): RecordingMetadata {
        fun bytes(position: Long, size: Int): ByteArray {
            require(size in 0..MAX_BLOCK_BYTES)
            val result = ByteArray(size)
            var count = 0
            while (count < size) {
                val read = file.read(position + count, result, count, size - count)
                if (read <= 0) throw EOFException("Truncated FLAC metadata")
                count += read
            }
            return result
        }
        if (!bytes(0, 4).contentEquals(byteArrayOf(102, 76, 97, 67))) throw IOException("Not FLAC")
        val tags = linkedMapOf<String, MutableList<String>>()
        var position = 4L
        var sampleRate = 0
        var bits = 0
        var duration = 0L
        var first = true
        repeat(256) {
            if (position + 4 > MAX_HEADER_BYTES) throw IOException("FLAC header limit")
            val header = bytes(position, 4)
            val type = header[0].toInt() and 0x7f
            val last = header[0].toInt() and 0x80 != 0
            val size = ((header[1].toInt() and 255) shl 16) or ((header[2].toInt() and 255) shl 8) or (header[3].toInt() and 255)
            position += 4
            if (position + size > MAX_HEADER_BYTES || (first && (type != 0 || size != 34))) throw IOException("Invalid FLAC header")
            first = false
            if (type == 0) {
                val block = bytes(position, size)
                if (block.size != 34) throw IOException("Invalid STREAMINFO")
                var packed = 0L
                for (index in 10..17) packed = (packed shl 8) or (block[index].toLong() and 255)
                sampleRate = (packed ushr 44).toInt()
                bits = ((packed ushr 36) and 31).toInt() + 1
                val samples = packed and 0xfffffffffL
                duration = if (sampleRate > 0) samples * 1000 / sampleRate else 0
            } else if (type == 4) {
                if (size > MAX_BLOCK_BYTES) throw IOException("FLAC comment limit")
                val input = ByteBuffer.wrap(bytes(position, size)).order(ByteOrder.LITTLE_ENDIAN)
                fun string(): String {
                    if (input.remaining() < 4) throw IOException("Invalid FLAC comments")
                    val length = input.int
                    if (length < 0 || length > input.remaining()) throw IOException("Invalid FLAC comment length")
                    return ByteArray(length).also(input::get).toString(Charsets.UTF_8)
                }
                string() // vendor
                if (input.remaining() < 4) throw IOException("Invalid FLAC comments")
                val count = input.int
                if (count !in 0..10_000) throw IOException("FLAC comment count limit")
                repeat(count) {
                    val tag = string()
                    val separator = tag.indexOf('=')
                    if (separator > 0) tags.getOrPut(tag.take(separator).uppercase(java.util.Locale.ROOT)) { mutableListOf() }.add(tag.drop(separator + 1))
                }
            }
            position += size
            if (last) return RecordingMetadata(
                title = tags["TITLE"]?.firstOrNull().orEmpty(),
                artists = tags["ARTIST"].orEmpty().filter(String::isNotBlank),
                album = tags["ALBUM"]?.firstOrNull(), durationMs = duration,
                isrc = tags["ISRC"]?.firstOrNull(), version = tags["VERSION"]?.firstOrNull(),
                sampleRate = sampleRate, bitsPerSample = bits,
            )
        }
        throw IOException("FLAC metadata block limit")
    }
}
