package com.convx.storage

import com.convx.storage.domain.RecordingIdentity
import com.convx.storage.domain.RecordingMetadata
import org.junit.Assert.*
import org.junit.Test

class RecordingIdentityTest {
    private val track = RecordingMetadata("The Song", listOf("Artist"), "Album", 180_000, "USABC2600001")
    @Test fun matchesCaseAndPunctuation() = assertEquals(RecordingIdentity.Match.VERIFIED_METADATA,
        RecordingIdentity.compare(track, track.copy(title = "THE SONG!", artists = listOf("ARTIST"))))
    @Test fun isrcAloneNeverMatches() = assertEquals(RecordingIdentity.Match.DIFFERENT,
        RecordingIdentity.compare(track, track.copy(title = "Another Song")))
    @Test fun sameIsrcDifferentMasterRequiresReview() = assertNotEquals(RecordingIdentity.Match.VERIFIED_METADATA,
        RecordingIdentity.compare(track, track.copy(album = "Album 2026 Remaster")))
    @Test fun missingDurationRequiresReview() = assertEquals(RecordingIdentity.Match.NEEDS_REVIEW,
        RecordingIdentity.compare(track, track.copy(durationMs = -1)))
    @Test fun rejectsDurationMismatch() = assertEquals(RecordingIdentity.Match.DIFFERENT,
        RecordingIdentity.compare(track, track.copy(durationMs = 184_000)))
    @Test fun rejectsCoverArtist() = assertEquals(RecordingIdentity.Match.DIFFERENT,
        RecordingIdentity.compare(track, track.copy(artists = listOf("Cover Artist"))))
    @Test fun rejectsVersionChanges() {
        listOf("Live", "Remix", "Slowed", "Instrumental", "Acoustic", "Mono", "Radio Edit", "Sped Up").forEach {
            assertNotEquals(it, RecordingIdentity.Match.VERIFIED_METADATA,
                RecordingIdentity.compare(track, track.copy(version = it)))
        }
    }
    @Test fun rejectsDifferentIsrcWithIdenticalTags() = assertEquals(RecordingIdentity.Match.DIFFERENT,
        RecordingIdentity.compare(track, track.copy(isrc = "USABC2600002")))
    @Test fun requiresAlbumEvenWithIsrc() = assertEquals(RecordingIdentity.Match.NEEDS_REVIEW,
        RecordingIdentity.compare(track, track.copy(album = null)))
    @Test fun multipleCandidatesNeverAutoSelect() = assertNull(RecordingIdentity.uniqueMatch(track, mapOf("one" to track, "two" to track)))
    @Test fun oneCandidateSelected() = assertEquals("one", RecordingIdentity.uniqueMatch(track, mapOf("one" to track)))
    @Test fun nonLatinTitlesRemainDistinct() = assertNotEquals(RecordingIdentity.normalize("गीत एक"), RecordingIdentity.normalize("गीत दो"))
}
