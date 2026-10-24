package com.convx.storage.domain

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

data class RecordingMetadata(
    val title: String,
    val artists: List<String>,
    val album: String?,
    val durationMs: Long,
    val isrc: String? = null,
    val version: String? = null,
    val sampleRate: Int = 0,
    val bitsPerSample: Int = 0,
)

/** Conservative candidate verification, not an acoustic fingerprint or a mastering identifier. */
object RecordingIdentity {
    enum class Match { VERIFIED_METADATA, NEEDS_REVIEW, DIFFERENT }

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private val designations = Regex(
        "\\b(live|remix|remastered?|instrumental|karaoke|cover|acoustic|slowed|sped up|radio edit|mono|stereo|demo|remastering)\\b",
    )

    private fun version(metadata: RecordingMetadata): String =
        normalize(listOfNotNull(metadata.title, metadata.album, metadata.version).joinToString(" "))
            .let { text -> designations.findAll(text).map { it.value }.sorted().joinToString("|") }

    fun compare(a: RecordingMetadata, b: RecordingMetadata): Match {
        val title = normalize(a.title)
        if (title.isEmpty() || title != normalize(b.title)) return Match.DIFFERENT
        val artists = a.artists.map(::normalize).filter(String::isNotEmpty).toSet()
        if (artists.isEmpty() || artists != b.artists.map(::normalize).filter(String::isNotEmpty).toSet()) return Match.DIFFERENT
        if (version(a) != version(b)) return Match.DIFFERENT
        if (a.durationMs <= 0 || b.durationMs <= 0) return Match.NEEDS_REVIEW
        if (abs(a.durationMs - b.durationMs) > 2_000) return Match.DIFFERENT
        val aIsrc = a.isrc?.replace("-", "")?.uppercase(Locale.ROOT)?.takeIf { it.matches(Regex("[A-Z]{2}[A-Z0-9]{3}[0-9]{7}")) }
        val bIsrc = b.isrc?.replace("-", "")?.uppercase(Locale.ROOT)?.takeIf { it.matches(Regex("[A-Z]{2}[A-Z0-9]{3}[0-9]{7}")) }
        if (aIsrc != null && bIsrc != null && aIsrc != bIsrc) return Match.DIFFERENT
        // Album/version evidence remains mandatory even with an ISRC: remasters can reuse it.
        val album = a.album?.let(::normalize).orEmpty()
        if (album.isEmpty() || album != b.album?.let(::normalize)) return Match.NEEDS_REVIEW
        if (normalize(a.version.orEmpty()) != normalize(b.version.orEmpty())) return Match.NEEDS_REVIEW
        return Match.VERIFIED_METADATA
    }

    fun uniqueMatch(target: RecordingMetadata, candidates: Map<String, RecordingMetadata>): String? =
        candidates.filterValues { compare(target, it) == Match.VERIFIED_METADATA }.keys.singleOrNull()
}
