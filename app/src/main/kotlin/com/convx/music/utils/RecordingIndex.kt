package com.convx.music.utils

import com.convx.music.db.MusicDatabase
import com.convx.music.db.entities.RecordingSourceEntity
import com.convx.music.db.entities.Song
import com.convx.storage.data.NetworkMusicRepository
import com.convx.storage.domain.NetworkTrack
import com.convx.storage.domain.RecordingIdentity
import com.convx.storage.domain.RecordingMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object RecordingIndex {
    fun metadata(song: Song) = RecordingMetadata(song.title, song.artists.map { it.name },
        song.song.albumName, song.song.duration.toLong() * 1000)

    suspend fun index(database: MusicDatabase, repository: NetworkMusicRepository, track: NetworkTrack) = withContext(Dispatchers.IO) {
        if (!track.id.substringBefore('?').endsWith(".flac", true)) return@withContext
        val dao = database.recordingDao
        val old = dao.source(track.id)
        if (old != null && old.fileSize == track.size) return@withContext
        val tags = try { repository.readFlacMetadata(track.id) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { return@withContext }
        database.withTransaction {
            // Metadata changing invalidates automatic decisions and requests review of manual links.
            dao.saveSource(RecordingSourceEntity(track.id, RecordingIdentity.normalize(tags.title), tags.title,
                tags.artists.joinToString("\u001f"), tags.album, tags.durationMs, tags.isrc, tags.version,
                tags.sampleRate, tags.bitsPerSample, track.size, revision = (old?.revision ?: -1) + 1))
            getSongById(track.id)?.song?.let { song ->
                updateFromSync(song.copy(title = tags.title.ifBlank { song.title },
                    duration = (tags.durationMs / 1000).toInt(), albumName = tags.album ?: song.albumName))
            }
        }
    }

    suspend fun reconcile(database: MusicDatabase) = withContext(Dispatchers.IO) {
        val songs = database.recordingCanonicalSongs()
        val byTitle = songs.groupBy { RecordingIdentity.normalize(it.title) }
        val sources = database.recordingDao.sources().first().filter { it.state == "unmatched" }
        // More than one plausible file or canonical song requires review, even with identical ISRCs.
        val proposals = sources.mapNotNull { source ->
            val match = RecordingIdentity.uniqueMatch(source.metadata(), byTitle[source.titleKey].orEmpty().associate { it.id to metadata(it) })
            match?.let { source to it }
        }.groupBy { it.second }
        proposals.values.filter { it.size == 1 }.flatten().forEach { (source, id) ->
            database.recordingDao.correct(source.sourceId, id, "verified_metadata", source.revision)
        }
    }

    suspend fun correct(database: MusicDatabase, source: RecordingSourceEntity, canonicalId: String?) {
        require(canonicalId == null || canonicalId.matches(Regex("[A-Za-z0-9_-]{11}")))
        database.withTransaction {
            if (canonicalId != null) require(getSongById(canonicalId)?.song?.isLocal == false)
            check(recordingDao.correct(source.sourceId, canonicalId, if (canonicalId == null) "rejected" else "manual", source.revision) == 1) {
                "Recording changed; refresh before saving"
            }
        }
    }
}
