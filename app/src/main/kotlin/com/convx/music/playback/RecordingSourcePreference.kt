package com.convx.music.playback

import com.convx.music.db.entities.RecordingSourceEntity

/**
 * Choose only a previously verified recording. The canonical YouTube mediaId remains the queue,
 * likes, and playlist identity; this selector only chooses the audio bytes.
 *
 * Aurora's multi-source candidate strategy inspired this ordering, not its synchronous network
 * probe. Selection has no network I/O and does not delay the start of YouTube fallback.
 */
internal object RecordingSourcePreference {
    fun choose(
        sources: List<RecordingSourceEntity>,
        unavailableUntilMillis: Map<String, Long>,
        nowMillis: Long,
    ): RecordingSourceEntity? =
        sources.asSequence()
            .filter { it.state == "manual" || it.state == "verified_metadata" }
            .filter { (unavailableUntilMillis[it.sourceId + "#" + it.revision] ?: 0L) <= nowMillis }
            .sortedWith(
                compareBy<RecordingSourceEntity> { if (it.state == "manual") 0 else 1 }
                    .thenBy(::formatRank)
                    .thenBy { it.fileSize.takeIf { size -> size > 0L } ?: Long.MAX_VALUE }
                    .thenBy { it.sourceId }
            )
            .firstOrNull()

    private fun formatRank(source: RecordingSourceEntity): Int = when {
        source.bitsPerSample == 16 && source.sampleRate == 44_100 -> 0
        source.bitsPerSample == 16 && source.sampleRate == 48_000 -> 1
        source.bitsPerSample in 17..24 && source.sampleRate in 44_100..48_000 -> 2
        source.bitsPerSample in 16..24 && source.sampleRate in 1..96_000 -> 3
        source.bitsPerSample >= 16 && source.sampleRate > 0 -> 4
        else -> 5
    }
}
