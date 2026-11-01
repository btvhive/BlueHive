package com.example.bluehive.api

// Live TV answers from bluehive-live-api (its own container on the VPS; Caddy
// sends /api/live/* there). Here in api/ because proguard-rules.pro keeps this
// package whole: Gson fills these fields by reflection, and R8 would otherwise
// strip them from a release build.

/** GET /api/live/channels */
data class LiveChannelsResponse(
    val rows: List<LiveRowDto> = emptyList(),
    val count: Int = 0,
)

data class LiveRowDto(
    val name: String,
    /** The row's colour as #RRGGBB, used for the details panel's top band. */
    val color: String?,
    val channels: List<LiveChannelDto> = emptyList(),
)

data class LiveChannelDto(
    val id: String,
    val name: String,
    val row: String,
    /** Display code ("UK", "US"), or "" when the site gives none. */
    val flag: String?,
    /** "FHD", "HD" or "4K". */
    val quality: String?,
    /** Id of this channel's backup feed, used by the server if this one fails. */
    val backup: String?,
    /** "ok" / "off" / "unknown": the channel's last resolve on the server. */
    val status: String?,
    /**
     * A Local News station's states (home state first); empty for one that
     * serves everyone. Null on every other channel, and from a server before
     * build h (2026-11-01).
     */
    val states: List<String>? = null,
)

/** POST /api/live/{channel}/session */
data class LiveSessionResponse(
    val channel: String,
    /** The id actually playing: the backup's when the main feed was down. */
    val served: String,
    val backup: Boolean = false,
    val name: String,
    /** Path on the API host, carrying its own 6 h grant (no headers needed). */
    val playlist: String,
    val grant_ttl_s: Int = 0,
    /**
     * Headers the TV must send on its chunk requests: zlive's Origin/Referer,
     * without which its transcode.cfd proxy (PBS Kids, Nicktoons) answers 403.
     * Null from a server older than 2026-10-28's "b" image.
     */
    val headers: Map<String, String>? = null,
)
