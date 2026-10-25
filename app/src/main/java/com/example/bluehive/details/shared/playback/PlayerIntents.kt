package com.example.bluehive.details.shared.playback

import android.content.Context
import android.content.Intent
import com.example.bluehive.api.ExtractResponse
import com.example.bluehive.webview.hls.HlsExtract
import com.example.bluehive.webview.hls.HlsPlayerActivity
import com.example.bluehive.webview.miruro.MiruroExtractorActivity
import com.example.bluehive.webview.miruro.MiruroPlayerActivity

/**
 * One Intent extra, typed the way its launch has always put it, so it goes
 * through the same putExtra overload: an Int stays an Int (TMDB_ID), a String
 * stays a String (MEDIA_ID is the tmdb id AS TEXT), a Long stays a Long.
 */
sealed interface PlayerExtra {
    val key: String

    data class Text(override val key: String, val value: String?) : PlayerExtra
    data class Whole(override val key: String, val value: Int) : PlayerExtra
    data class Millis(override val key: String, val value: Long) : PlayerExtra
    data class Flag(override val key: String, val value: Boolean) : PlayerExtra
}

/** Puts [extras] on this Intent, each with the overload of its type. */
fun Intent.putPlayerExtras(extras: List<PlayerExtra>): Intent = apply {
    for (extra in extras) {
        when (extra) {
            is PlayerExtra.Text -> putExtra(extra.key, extra.value)
            is PlayerExtra.Whole -> putExtra(extra.key, extra.value)
            is PlayerExtra.Millis -> putExtra(extra.key, extra.value)
            is PlayerExtra.Flag -> putExtra(extra.key, extra.value)
        }
    }
}

/**
 * Every player and extractor launch the details screens make, with every extra
 * each one carries (contract C4 in docs/details-redesign-architecture.md).
 *
 * A dropped extra fails silently on the TV: a missing AUDIO hands a dub viewer
 * the sub on a mid-episode refetch, a missing EXTRACT_SOURCE re-extracts the
 * wrong source. So the extras are built as plain lists ([PlayerExtra]) that the
 * JVM tests compare key by key, value by value and type by type against the
 * record taken before the redesign (DetailsLaunchExtrasGolden), and each
 * Intent function only wraps its list. An episode adds SEASON_NUMBER,
 * EPISODE_NUMBER and EPISODE_NAME after MEDIA_TITLE; a movie never carries them.
 *
 * Moved here from the two screens in Stage 6 of the details redesign.
 */
object PlayerIntents {

    private fun episodeExtras(request: PlaybackRequest): List<PlayerExtra> = when (request) {
        is PlaybackRequest.Movie -> emptyList()
        is PlaybackRequest.Episode -> listOf(
            PlayerExtra.Whole("SEASON_NUMBER", request.season),
            PlayerExtra.Whole("EPISODE_NUMBER", request.episode),
            PlayerExtra.Text("EPISODE_NAME", request.episodeName),
        )
    }

    // ── HlsPlayerActivity: a source of the chain (VidNest, VidLove, VidFast, MoviesAPI…) ──

    fun hlsPlayerExtras(
        request: PlaybackRequest,
        body: ExtractResponse,
        /** The source's page: src.url. */
        embedUrl: String,
        /** The source's routing name: src.name. */
        sourceName: String,
        profileId: Int,
        mediaType: String,
        mediaTitle: String,
        /** Where to start, in ms: 0 for a press, the dead source's spot for a failover. */
        startAtMs: Long,
    ): List<PlayerExtra> = listOf(
        PlayerExtra.Text("M3U8_URL", body.m3u8),
        // DIRECT FIRST, relay only after a confirmed failure. Re-extracting
        // cannot fix a blocked CDN — the source pins one host per title and
        // returns the same one — so this is what actually recovers a
        // filtered line. Already tagged with the title by /api/extract, so
        // the fallback lands in bluehive.relay_sessions attributed rather
        // than as an anonymous byte count.
        //
        // The player mints the grant itself on the failure path
        // (HlsPlayerActivity.switchToRelay -> mintRelayGrant); passing the
        // URL is all this screen has to do. Without this extra, relayUrl is
        // null in the player and switchToRelay returns false immediately —
        // which is why movies had no relay at all before.
        PlayerExtra.Text("M3U8_FALLBACK_URL", body.proxy_url),
        // The EMBED PAGE origin, not the manifest host — the CDN validates
        // it, and sending the manifest's own host 403s every segment while
        // extraction still looks like it worked.
        PlayerExtra.Text("REFERER", body.referer),
        PlayerExtra.Flag("IS_HLS", body.isHls ?: true),
        PlayerExtra.Text("UA", body.user_agent),
        PlayerExtra.Whole("TMDB_ID", request.tmdbId),
        // Blank ON PURPOSE: these sources have no on-device extractor. The
        // player reads it as "ask the backend instead" and calls
        // /api/extract?refresh=1 — see HlsPlayerActivity.reextractViaBackend.
        PlayerExtra.Text(HlsExtract.EXTRA_EXTRACTOR_CLASS, ""),
        PlayerExtra.Text("EMBED_URL", embedUrl),
        // Which source to re-ask on a mid-play failure; without it the
        // player would always re-extract VidFast. The display name IS the
        // backend key once lowercased — see usesBackendExtraction.
        PlayerExtra.Text("EXTRACT_SOURCE", sourceName.lowercase()),
        PlayerExtra.Whole("PROFILE_ID", profileId),
        PlayerExtra.Text("MEDIA_TYPE", mediaType),
        PlayerExtra.Text("MEDIA_TITLE", mediaTitle),
    ) + episodeExtras(request) + listOf(
        PlayerExtra.Text("SOURCE_NAME", sourceName),
        PlayerExtra.Text("SUBTITLE_URL", body.subtitle_url),
        PlayerExtra.Millis(HlsPlayerActivity.EXTRA_START_AT_MS, startAtMs),
    )

    fun hlsPlayer(
        context: Context,
        request: PlaybackRequest,
        body: ExtractResponse,
        embedUrl: String,
        sourceName: String,
        profileId: Int,
        mediaType: String,
        mediaTitle: String,
        startAtMs: Long,
    ): Intent = Intent(context, HlsPlayerActivity::class.java).putPlayerExtras(
        hlsPlayerExtras(request, body, embedUrl, sourceName, profileId, mediaType, mediaTitle, startAtMs)
    )

    // ── MiruroPlayerActivity after an ON-DEVICE extract (MiruroExtractorActivity's result) ──

    fun miruroOnDeviceExtras(
        request: PlaybackRequest,
        m3u8: String,
        referer: String?,
        userAgent: String?,
        /** miruroSourceLabel(null, the captured default server's name). */
        sourceName: String,
        profileId: Int,
        mediaType: String,
        mediaTitle: String,
        /** The track asked for: pendingDub. */
        dub: Boolean,
        /** The provider this playback started on -- see SERVER below. */
        server: String?,
    ): List<PlayerExtra> = listOf(
        PlayerExtra.Text("M3U8_URL", m3u8),                  // try the CDN directly
        PlayerExtra.Text("M3U8_FALLBACK_URL", relayUrl(request, m3u8, mediaTitle, profileId, sourceName)),  // relay if that's blocked
        PlayerExtra.Text("REFERER", referer),
        PlayerExtra.Text("UA", userAgent),
        PlayerExtra.Whole("PROFILE_ID", profileId),
        PlayerExtra.Text("MEDIA_ID", request.tmdbId.toString()),
        PlayerExtra.Text("MEDIA_TYPE", mediaType),
        PlayerExtra.Text("MEDIA_TITLE", mediaTitle),
    ) + episodeExtras(request) + listOf(
        PlayerExtra.Text("SOURCE_NAME", sourceName),
        // AUDIO EXISTS ONLY FOR MID-PLAYBACK RE-EXTRACTION, and it is not
        // optional. Miruro signs its segment URLs with a token that dies
        // after roughly nine minutes — MEASURED 2026-08-27, Mushoku Tensei
        // s1e6 froze 121 segments in and every request after that was a 403
        // — so recovering means fetching a FRESH manifest mid-episode.
        // Asking for one without naming the track hands a dub viewer the
        // sub, which is worse than the freeze it replaces: it looks like it
        // worked.
        //
        // Derived from the SAME value as SOURCE_NAME, deliberately. If those
        // two ever disagree, history records one track while the player
        // refetches the other.
        PlayerExtra.Text("AUDIO", if (dub) "dub" else "sub"),
        // SERVER pins the provider for any mid-playback re-extraction, and
        // HOW it is resolved matters as much as that it is sent. triedName is
        // what the failover bookkeeping just computed: the manually chosen
        // provider when there was one, and otherwise the DEFAULT resolved to
        // its actual name instead of left null.
        //
        // Sending null would re-extract against whatever Miruro defaults to at
        // that moment, which is not necessarily the provider this playback
        // started on — the list is reordered as servers come and go, so a
        // recovery could silently move the viewer to a different source
        // mid-episode.
        PlayerExtra.Text("SERVER", server),
    )

    fun miruroOnDevice(
        context: Context,
        request: PlaybackRequest,
        m3u8: String,
        referer: String?,
        userAgent: String?,
        sourceName: String,
        profileId: Int,
        mediaType: String,
        mediaTitle: String,
        dub: Boolean,
        server: String?,
    ): Intent = Intent(context, MiruroPlayerActivity::class.java).putPlayerExtras(
        miruroOnDeviceExtras(request, m3u8, referer, userAgent, sourceName, profileId, mediaType, mediaTitle, dub, server)
    )

    /**
     * The relay fallback for an on-device extract, tagged with the title the
     * backend never saw: "anime_movie" for a film, "anime_tv" with the season
     * and episode for an episode.
     */
    private fun relayUrl(
        request: PlaybackRequest,
        m3u8: String,
        mediaTitle: String,
        profileId: Int,
        sourceName: String,
    ): String = when (request) {
        is PlaybackRequest.Movie -> animeRelayUrl(
            m3u8 = m3u8, tmdbId = request.tmdbId, mediaType = "anime_movie",
            title = mediaTitle, profileId = profileId,
            sourceName = sourceName,
        )
        is PlaybackRequest.Episode -> animeRelayUrl(
            m3u8 = m3u8, tmdbId = request.tmdbId, mediaType = "anime_tv",
            title = mediaTitle, profileId = profileId,
            sourceName = sourceName,
            season = request.season, episode = request.episode,
        )
    }

    // ── MiruroPlayerActivity after a BACKEND extract (/api/extract, source=miruro) ──

    fun miruroBackendExtras(
        request: PlaybackRequest,
        body: ExtractResponse,
        /** miruroSourceLabel(body.servers, server). */
        sourceName: String,
        profileId: Int,
        mediaType: String,
        mediaTitle: String,
        /** The track actually extracted: dub asked for AND the backend really returned dub. */
        playingDub: Boolean,
        /** server ?: the default server's name -- see SERVER below. */
        server: String?,
    ): List<PlayerExtra> = listOf(
        PlayerExtra.Text("M3U8_URL", body.m3u8),
        // Tagged server-side already, so no animeRelayUrl() here — that
        // helper exists for the on-device path, where the backend never
        // sees the playback and the relay row would be anonymous.
        PlayerExtra.Text("M3U8_FALLBACK_URL", body.proxy_url),
        PlayerExtra.Text("REFERER", body.referer),
        PlayerExtra.Flag("IS_HLS", body.isHls ?: true),
        PlayerExtra.Text("UA", body.user_agent),
        PlayerExtra.Whole("PROFILE_ID", profileId),
        PlayerExtra.Text("MEDIA_ID", request.tmdbId.toString()),
        PlayerExtra.Text("MEDIA_TYPE", mediaType),
        PlayerExtra.Text("MEDIA_TITLE", mediaTitle),
    ) + episodeExtras(request) + listOf(
        // Reflects the track actually extracted — this string is what
        // watch history and relay_sessions record, so a hardcoded "Sub"
        // would mislabel every dub playback. `playingDub`, not `dub`: a
        // last-resort sub is recorded as the sub it is.
        PlayerExtra.Text("SOURCE_NAME", sourceName),
        // See the on-device launch above for why AUDIO is carried. `playingDub`
        // rather than `dub` for the same reason SOURCE_NAME uses it: a
        // last-resort sub must re-extract as the sub it actually is.
        PlayerExtra.Text("AUDIO", if (playingDub) "dub" else "sub"),
        // SERVER is best-effort and only exists on this path — the backend was
        // told which one to use, so a refetch can ask for the same one instead
        // of redrawing whichever the site defaults to. Manual selection wins;
        // failing that, the default resolved to its name. Null only when the
        // picker never ran and there is nothing to resolve from — the backend
        // then picks its own default, exactly as it did for the original
        // extraction.
        PlayerExtra.Text("SERVER", server),
    )

    fun miruroBackend(
        context: Context,
        request: PlaybackRequest,
        body: ExtractResponse,
        sourceName: String,
        profileId: Int,
        mediaType: String,
        mediaTitle: String,
        playingDub: Boolean,
        server: String?,
    ): Intent = Intent(context, MiruroPlayerActivity::class.java).putPlayerExtras(
        miruroBackendExtras(request, body, sourceName, profileId, mediaType, mediaTitle, playingDub, server)
    )

    // ── MiruroExtractorActivity: the on-device fallback. The same on both screens. ──

    fun extractorExtractExtras(
        url: String,
        dub: Boolean,
        /** A named server, or null/blank for the default. */
        server: String?,
        /** No server list is cached yet for this url and track. */
        listNotCached: Boolean,
    ): List<PlayerExtra> {
        val extras = mutableListOf<PlayerExtra>(
            PlayerExtra.Text(MiruroExtractorActivity.EXTRA_URL, url),
            PlayerExtra.Flag(MiruroExtractorActivity.EXTRA_DUB, dub),
            PlayerExtra.Text(MiruroExtractorActivity.EXTRA_MODE, MiruroExtractorActivity.MODE_EXTRACT),
        )
        if (!server.isNullOrBlank()) {
            extras += PlayerExtra.Text(MiruroExtractorActivity.EXTRA_SERVER_NAME, server)
        } else if (listNotCached) {
            // Default server, list not cached yet → grab the provider list
            // in this same pass so "Choose a server" is instant afterward.
            extras += PlayerExtra.Flag(MiruroExtractorActivity.EXTRA_CAPTURE_SERVERS, true)
        }
        return extras
    }

    fun extractorExtract(context: Context, url: String, dub: Boolean, server: String?, listNotCached: Boolean): Intent =
        Intent(context, MiruroExtractorActivity::class.java)
            .putPlayerExtras(extractorExtractExtras(url, dub, server, listNotCached))

    fun extractorEnumerateExtras(url: String, dub: Boolean): List<PlayerExtra> = listOf(
        PlayerExtra.Text(MiruroExtractorActivity.EXTRA_URL, url),
        PlayerExtra.Flag(MiruroExtractorActivity.EXTRA_DUB, dub),
        PlayerExtra.Text(MiruroExtractorActivity.EXTRA_MODE, MiruroExtractorActivity.MODE_ENUMERATE),
    )

    fun extractorEnumerate(context: Context, url: String, dub: Boolean): Intent =
        Intent(context, MiruroExtractorActivity::class.java)
            .putPlayerExtras(extractorEnumerateExtras(url, dub))
}
