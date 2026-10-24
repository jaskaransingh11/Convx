package com.convx.music.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.convx.storage.domain.RecordingMetadata

@Entity(tableName = "recording_source", indices = [Index("canonicalId"), Index("titleKey")])
data class RecordingSourceEntity(
    @PrimaryKey val sourceId: String,
    val titleKey: String,
    val title: String,
    val artists: String,
    val album: String?,
    val durationMs: Long,
    val isrc: String?,
    val version: String?,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val fileSize: Long,
    val canonicalId: String? = null,
    val state: String = "unmatched",
    val revision: Long = 0,
) {
    fun metadata() = RecordingMetadata(title, artists.split('\u001f').filter(String::isNotBlank), album,
        durationMs, isrc, version, sampleRate, bitsPerSample)
}

@Entity(tableName = "like_outbox", primaryKeys = ["accountScope", "videoId"])
data class LikeOutboxEntity(
    val accountScope: String,
    val videoId: String,
    val desiredLiked: Boolean,
    val revision: Long,
)

@Entity(tableName = "remote_like_snapshot", primaryKeys = ["accountScope", "videoId"])
data class RemoteLikeSnapshotEntity(val accountScope: String, val videoId: String)
