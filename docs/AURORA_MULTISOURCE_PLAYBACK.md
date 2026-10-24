# Aurora multi-source playback review and targeted Convx adaptation (2026-10-24)

This change is on `feat/unified-flac-playback` only; production `com.convx.music` untouched. **The source-selection unit tests and GitHub build still require acceptance before shipping an APK.**

## Working playback sample

- **Kimiko Ishizaka — Bach, Goldberg Variations, BWV 988: Variatio 19 a 1 Clav.** from the 2012 Open Goldberg recording (CC0).
- Video from official OpenGoldberg artist channel: https://www.youtube.com/watch?v=3h9hXH4cVxc . This is a metadata/visual reference, **not** proof of YouTube Music's exact catalogue video ID.
- Existing owner TorBox WebDL item **2099323**, `music` tag, AirLock true. Direct authenticated WebDAV HEAD/range fixture confirmed 23,902,308 bytes, FLAC 24-bit/96kHz stereo and 85.01s, HTTP 206 at beginning and midpoint. It is intended to test real streaming/seek, **not** as the everyday recommended large format.
- Variation 4 is a second earlier-known AirLocked recording (item **1831797**); use it to test next-track transition and per-song source selection.

## Reuse audit

Source repository: https://github.com/nessli420/aurora , licensed **Apache License 2.0**. Convx uses **GPLv3**; the Apache Software Foundation documents permissive Apache 2 into GPLv3 compatibility (https://www.apache.org/licenses/GPL-compatibility). Adapt architecture and add attribution if carrying literal Aurora code. No literal copyrighted code copied into this patch; this implementation is independent.

| Aurora source | Audited mechanism | Adaptation / concern |
|--|--|--|
| `core/.../MergedBackend.kt` | Groups and names source IDs; gathers local/server matches with per-source 3s timeout, 60s metadata cache and 128-entry LRU; ranks by local/download/stream *before* format score | Convx already has `recordingDao.verifiedSources(videoId)`, `RecordingIdentity` and network source IDs. **Do not copy its default local-first ranking**: local lossy could outrank streamed FLAC. Use canonical recording identifier, verify exact edition and format and stable priority |
| `app/.../PreferredPlayback.kt` | Iterates 6 candidates under total 6s deadline; reads 1 byte with network timeout 2s; preserves `mediaId` and source info in `MediaItem` metadata | Good pattern for **bounded preflight + preserve identity**. Six serial probes may cause startup delays and failure of valid slow WebDAV. Future improvements should use last-known health and prefetch next URLs, not six serial probes on tap |
| `app/.../YoutubeMediaSource.kt` | MediaSource factory resolves preferred file before Youtube sentinel, caches next-track resolution, prevents stale completion with generation, preserves original item identity, supports HLS/DASH and separate media cache keys | Convx already has `ResolvingMediaSource` (generation cleanup), `MusicService` (verified FLAC before YTM, native module lossless → standard fallback, HLS/audio routing, source-specific cache keys) and prefetch. Reuse limited contract tests and source health patterns, avoid wholesale factory swap |

## Implemented change

`RecordingSourcePreference.choose` selects a verified FLAC file for the canonical YTM ID **without network I/O**. Manual matching overrides source ranking; otherwise choose everyday-size 16-bit/44.1kHz first, then 16-bit/48kHz, then moderate genuine FLAC formats, then hi-res. Within equivalent format prefer smaller files, stable source IDs. Existing `MusicService.onPlayerError` already quarantines failed `sourceId#revision` for ~60s and re-prepares; the next call now picks the next source (or YouTube), not the same failed one. No new downloading on Oracle or Jassi. Seven tests cover ordering, manual override, two-source failover, expiration of cooldown, all sources unavailable, rejected/uncertain links, unknown FLAC metadata.

## Remaining acceptance

- Unit-test CI on fork should pass `RecordingSourcePreferenceTest`; existing general PR build's FFTW provisioning was failing before reaching the app build (separate workflow/environment issue).
- In **Convx Debug** only: add the TorBox WebDAV server in **Settings → Network Storage**, test server and scan, play Variation 19 from network browser. A direct file play demonstrates FLAC decoder+WebDAV.
- For **automatic FLAC substitution**, select the imported recording in **Settings → Network Storage → Recording Links**, manually enter the canonical **11-character YouTube video ID** after confirming same *exact* master. Playing the song from YTM search must preserve queue mediaId/likes and select the FLAC; without link do not mislabel YTM playback as FLAC.
- Test failover: with a fixture containing two exact verified files, make first URI fail in a debug-only fake DataSource; check second FLAC chosen, then both failing → original YouTube, no loops, no likes/playlist mutations, no source byte-cache poisoning.
- Real networking test requires Android normal boot, authorized credentials inserted only through debug UI/AndroidKeyStore, and log/probe evidence for 1:25 play, seeking near 45s, and next-track transition. No screen-independent playback test was performed as part of this code review.

