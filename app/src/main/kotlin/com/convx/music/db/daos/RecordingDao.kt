package com.convx.music.db.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Insert
import com.convx.music.db.entities.RecordingSourceEntity
import com.convx.music.db.entities.LikeOutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT videoId FROM remote_like_snapshot WHERE accountScope = :scope")
    suspend fun previousRemoteLikes(scope: String): List<String>

    @Query("DELETE FROM remote_like_snapshot WHERE accountScope = :scope")
    suspend fun clearRemoteLikes(scope: String)

    @Insert
    suspend fun saveRemoteLikes(rows: List<com.convx.music.db.entities.RemoteLikeSnapshotEntity>)
    @Upsert
    suspend fun saveSource(source: RecordingSourceEntity)

    @Query("SELECT * FROM recording_source WHERE sourceId = :sourceId")
    suspend fun source(sourceId: String): RecordingSourceEntity?

    @Query("SELECT * FROM recording_source ORDER BY title COLLATE NOCASE")
    fun sources(): Flow<List<RecordingSourceEntity>>

    @Query("SELECT * FROM recording_source WHERE titleKey = :titleKey")
    suspend fun candidates(titleKey: String): List<RecordingSourceEntity>

    @Query("SELECT * FROM recording_source WHERE canonicalId = :canonicalId AND state IN ('manual', 'verified_metadata') ORDER BY bitsPerSample DESC, sampleRate DESC, sourceId")
    suspend fun verifiedSources(canonicalId: String): List<RecordingSourceEntity>

    @Query("UPDATE recording_source SET canonicalId = :canonicalId, state = :state, revision = revision + 1 WHERE sourceId = :sourceId AND revision = :revision")
    suspend fun correct(sourceId: String, canonicalId: String?, state: String, revision: Long): Int

    @Query("SELECT * FROM like_outbox WHERE accountScope = :scope")
    suspend fun pendingLikes(scope: String): List<LikeOutboxEntity>

    @Query("DELETE FROM like_outbox WHERE accountScope = :scope AND videoId = :videoId AND revision = :revision")
    suspend fun acknowledgeLike(scope: String, videoId: String, revision: Long): Int
}
