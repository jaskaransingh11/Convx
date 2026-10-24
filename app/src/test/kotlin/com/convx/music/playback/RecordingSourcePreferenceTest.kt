package com.convx.music.playback

import com.convx.music.db.entities.RecordingSourceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingSourcePreferenceTest {
    private fun source(
        id: String,
        bits: Int = 16,
        rate: Int = 44_100,
        size: Long = 22_000_000L,
        state: String = "verified_metadata",
        revision: Long = 0L,
    ) = RecordingSourceEntity(
        sourceId = id, titleKey = "test", title = "Song", artists = "Artist",
        album = "Album", durationMs = 180_000L, isrc = null, version = null,
        sampleRate = rate, bitsPerSample = bits, fileSize = size, canonicalId = "abcdefghijk",
        state = state, revision = revision
    )

    @Test fun compactCdQualityWinsOverHiResWhenBothVerified() {
        val huge = source("hires", bits = 24, rate = 192_000, size = 140_000_000)
        val compact = source("cd")
        assertEquals(compact, RecordingSourcePreference.choose(listOf(huge, compact), emptyMap(), 100))
    }

    @Test fun manualChoiceWinsOverAutomaticQualityRanking() {
        val compact = source("cd")
        val override = source("manual", bits = 24, rate = 96_000, state = "manual")
        assertEquals(override, RecordingSourcePreference.choose(listOf(compact, override), emptyMap(), 100))
    }

    @Test fun failedSourceFallsThroughToNextVerifiedCopy() {
        val compact = source("cd", revision = 5)
        val alternate = source("hires", bits = 24, rate = 96_000)
        assertEquals(alternate, RecordingSourcePreference.choose(
            listOf(compact, alternate), mapOf("cd#5" to 60_100L), 100))
        assertEquals(compact, RecordingSourcePreference.choose(
            listOf(compact, alternate), mapOf("cd#5" to 60_100L), 60_101L))
    }

    @Test fun noHealthyVerifiedCopyFallsBackToYoutube() {
        val compact = source("cd")
        assertNull(RecordingSourcePreference.choose(
            listOf(compact), mapOf("cd#0" to 60_100L), 100))
    }

    @Test fun unresolvedAndRejectedSourcesAreNeverPlayed() {
        assertNull(RecordingSourcePreference.choose(
            listOf(source("unresolved", state = "unmatched"), source("no", state = "rejected")), emptyMap(), 0))
    }

    @Test fun lessBloatedFileWinsTieWithoutReorderingCanonicalIdentity() {
        val big = source("big", size = 50_000_000)
        val small = source("small", size = 28_000_000)
        assertEquals(small, RecordingSourcePreference.choose(listOf(big, small), emptyMap(), 0))
    }

    @Test fun lowResolutionOrIncompleteMetadataRanksLast() {
        val unknown = source("unknown", bits = 0, rate = 0)
        val cd = source("cd")
        assertEquals(cd, RecordingSourcePreference.choose(listOf(unknown, cd), emptyMap(), 0))
    }
}
