package com.convx.music.utils

import com.music.innertube.YouTube
import java.security.MessageDigest

/** Channel-scoped identity, independent of expiring cookies. Never persist credentials in the outbox. */
fun currentYouTubeSyncScope(): String? = YouTube.dataSyncId?.takeIf(String::isNotBlank)?.let { id ->
    MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
}
