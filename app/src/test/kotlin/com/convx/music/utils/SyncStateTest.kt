package com.convx.music.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStateTest {
    private val completed = SyncState(
        likedSongs = SyncStatus.Completed,
        librarySongs = SyncStatus.Completed,
        uploadedSongs = SyncStatus.Completed,
        likedAlbums = SyncStatus.Completed,
        uploadedAlbums = SyncStatus.Completed,
        artists = SyncStatus.Completed,
        playlists = SyncStatus.Completed,
    )

    @Test fun allSevenStagesMustFinish() {
        assertEquals(emptyList<String>(), completed.incompleteSections())
        assertEquals(7, SyncState().incompleteSections().size)
    }

    @Test fun failedPlaylistPreventsFalseFullSuccess() {
        val failed = completed.copy(playlists = SyncStatus.Error("Remote page incomplete"))
        assertEquals(listOf("playlists"), failed.incompleteSections())
    }

    @Test fun pendingLikesPreventFalseFullSuccess() {
        val pending = completed.copy(likedSongs = SyncStatus.Error("Pending delivery verification"))
        assertEquals(listOf("liked songs"), pending.incompleteSections())
    }
}
