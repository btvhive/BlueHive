package com.example.bluehive.details.shared.playback

import androidx.compose.runtime.Immutable

/**
 * One "Play / from Website N" source: the name the viewer sees ([coverName],
 * e.g. "Website 1"), its routing name ([name], e.g. "VidNest", which lowercased
 * is the backend's source key and is never shown), and its page.
 *
 * The one source model of the details screens (Stage 6 of the details
 * redesign); it replaces each screen's own StreamingSource, field for field.
 */
@Immutable
data class StreamSource(
    val coverName: String,
    val name: String,
    val url: String,
)
