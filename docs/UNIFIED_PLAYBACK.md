# Unified recording playback

Baseline: upstream `5a78db4baf4f31633d1ab1bb4c73ba19be7da24c` (2.0.4), newer than nightly tag `1a0a9f7ce4047693d6d243625bbaabdace537ea3`.

## Contract

One canonical YouTube song owns likes, playlist occurrences and queue identity. A verified network recording supplies bytes only. Never send a network URI as a YouTube video ID. Never reuse a lossy cache key for FLAC. Matching does not assert that two masters are identical merely because their ISRCs agree.

Production `com.convx.music` is excluded from all installation, data-copy and credential operations. Use the existing `.debug` application ID. Credentials remain in the existing AndroidKeyStore encrypted network-source store; no credentials in this repository or build artifacts.

## Dependency-ordered chunks

1. Network routing: bypass YouTube at both media-source and data-source boundaries; test byte positions, source reuse, headers, artwork and full-file reads with a local WebDAV fixture. Rollback: revert routing commit.
2. Recording index: bounded FLAC STREAMINFO/Vorbis parsing, conservative metadata comparison, persistent reviewed links/rejections and manual correction. Test missing/ambiguous metadata, live/remix/cover/instrumental/mastering differences and migration. Rollback: unlink sources; retain original song rows.
3. Playback: verified FLAC before existing providers; separate source cache keys; keep canonical mediaId and metadata; fail over without loops and preserve position. Test FLAC unavailable/corrupt/auth expired, seeks and subsequent replay.
4. Sync: reuse current YouTube client and playlist operations, strict pagination, durable likes outbox, readback before acknowledgement, pending-edit protection and owner scope. No remote deletion based on partial snapshots. Playlist occurrence/setVideoId identity must survive audio substitution. Test offline/restart/concurrent edit/auth expiry.
5. Delivery: run unit tests and APK build at exact commit; inspect diff and artifact; install only debug; verify authenticated bidirectional likes/playlists, cold start, FLAC duration, artwork, range requests and seeking on SM-T865. Do not mark end-to-end complete while recovery mode, Google sign-in or credentials prevent verification.

## Plan critique

Filename-derived metadata is insufficient for automatic links. ISRC can span masters. A successful HTTP mutation is insufficient evidence of sync. Existing pagination returned partial pages as complete; existing local like methods launched untracked duplicate remote writes. A server-based canonical catalogue is an alternative for several clients, but adds deployment/auth dependencies and cannot repair Android playback routing. Extend existing Convx modules first.

## Activity ledger

- VERIFIED: source audit; fork `jaskaransingh11/Convx`; debug package already isolated upstream.
- BLOCKED live device acceptance: ADB reported SM-T865 in recovery on 2026-10-24; do not change boot state as part of this feature.
- NEXT: implement and run fixture/unit/build checks, then live acceptance when Android is available.
