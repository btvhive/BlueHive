package com.example.bluehive.details.shared.playback

import com.example.bluehive.webview.hls.HlsExtract
import com.example.bluehive.webview.hls.HlsPlayerActivity
import com.example.bluehive.webview.miruro.MiruroExtractorActivity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * EVERY EXTRA EVERY DETAILS-SCREEN LAUNCH CARRIES TODAY, recorded at v376
 * (commit ac47236) before the redesign touches any of it
 * (docs/details-redesign-architecture.md, contract C4).
 *
 * Read off the source by script, in source order, then checked by hand against
 * the code. Each entry is the key and where its value comes from. A dropped
 * extra fails silently on the TV: a missing AUDIO hands a dub viewer the sub on
 * a mid-episode refetch, a missing EXTRACT_SOURCE re-extracts the wrong source.
 *
 * Stage 6 moves all of these launches into shared/playback/PlayerIntents.kt.
 * Its tests build each launch with known inputs and must produce exactly these
 * keys, with exactly these values. Until then this file is the record, and the
 * test below only keeps it internally consistent.
 */
object DetailsLaunchExtrasGolden {

    data class Extra(val key: String, val value: String)

    // ── HlsPlayerActivity: the source chain (VidNest, VidLove, VidFast, MoviesAPI…) ──

    val tvHlsPlayer = listOf(
        Extra("M3U8_URL", "body.m3u8"),
        Extra("M3U8_FALLBACK_URL", "body.proxy_url"),
        Extra("REFERER", "body.referer"),
        Extra("IS_HLS", "body.isHls ?: true"),
        Extra("UA", "body.user_agent"),
        Extra("TMDB_ID", "tmdbId (Int)"),
        Extra(HlsExtract.EXTRA_EXTRACTOR_CLASS, "\"\" (blank on purpose: ask the backend to re-extract)"),
        Extra("EMBED_URL", "src.url"),
        Extra("EXTRACT_SOURCE", "src.name.lowercase()"),
        Extra("PROFILE_ID", "profileId (Int)"),
        Extra("MEDIA_TYPE", "mediaType"),
        Extra("MEDIA_TITLE", "mediaTitle"),
        Extra("SEASON_NUMBER", "pendingSeason (Int)"),
        Extra("EPISODE_NUMBER", "pendingEpisode (Int)"),
        Extra("EPISODE_NAME", "pendingEpisodeName (String?)"),
        Extra("SOURCE_NAME", "src.name"),
        Extra("SUBTITLE_URL", "body.subtitle_url"),
        Extra(HlsPlayerActivity.EXTRA_START_AT_MS, "startAtMs (Long): 0 for a press, the dead source's spot for a failover"),
    )

    /** The TV list minus SEASON_NUMBER, EPISODE_NUMBER and EPISODE_NAME. */
    val movieHlsPlayer = tvHlsPlayer.filterNot { it.key in setOf("SEASON_NUMBER", "EPISODE_NUMBER", "EPISODE_NAME") }

    // ── MiruroPlayerActivity after an ON-DEVICE extract (MiruroExtractorActivity result) ──

    val tvMiruroOnDevice = listOf(
        Extra("M3U8_URL", "result m3u8"),
        Extra("M3U8_FALLBACK_URL", "animeRelayUrl(m3u8, tmdbId, \"anime_tv\", mediaTitle, profileId, " +
            "miruroSourceLabel(null, capNames?.getOrNull(capDef)), pendingSeason, pendingEpisode)"),
        Extra("REFERER", "result referer"),
        Extra("UA", "result ua"),
        Extra("PROFILE_ID", "profileId (Int)"),
        Extra("MEDIA_ID", "tmdbId.toString() (String, not Int)"),
        Extra("MEDIA_TYPE", "mediaType"),
        Extra("MEDIA_TITLE", "mediaTitle"),
        Extra("SEASON_NUMBER", "pendingSeason (Int)"),
        Extra("EPISODE_NUMBER", "pendingEpisode (Int)"),
        Extra("EPISODE_NAME", "pendingEpisodeName (String?)"),
        Extra("SOURCE_NAME", "miruroSourceLabel(null, capNames?.getOrNull(capDef))"),
        Extra("AUDIO", "if (pendingDub) \"dub\" else \"sub\""),
        Extra("SERVER", "triedName = lastExtractServer ?: the captured default server's name"),
    )

    /**
     * The TV list minus season, episode and episode name. The relay url is
     * built with "anime_movie" and no season or episode.
     */
    val movieMiruroOnDevice = tvMiruroOnDevice
        .filterNot { it.key in setOf("SEASON_NUMBER", "EPISODE_NUMBER", "EPISODE_NAME") }
        .map {
            if (it.key == "M3U8_FALLBACK_URL") Extra(it.key, "animeRelayUrl(m3u8, tmdbId, \"anime_movie\", " +
                "mediaTitle, profileId, miruroSourceLabel(null, capNames?.getOrNull(capDef)))")
            else it
        }

    // ── MiruroPlayerActivity after a BACKEND extract (/api/extract, source=miruro) ──

    val tvMiruroBackend = listOf(
        Extra("M3U8_URL", "body.m3u8"),
        Extra("M3U8_FALLBACK_URL", "body.proxy_url (already tagged server-side)"),
        Extra("REFERER", "body.referer"),
        Extra("IS_HLS", "body.isHls ?: true"),
        Extra("UA", "body.user_agent"),
        Extra("PROFILE_ID", "profileId (Int)"),
        Extra("MEDIA_ID", "tmdbId.toString() (String, not Int)"),
        Extra("MEDIA_TYPE", "mediaType"),
        Extra("MEDIA_TITLE", "mediaTitle"),
        Extra("SEASON_NUMBER", "pendingSeason (Int)"),
        Extra("EPISODE_NUMBER", "pendingEpisode (Int)"),
        Extra("EPISODE_NAME", "pendingEpisodeName (String?)"),
        Extra("SOURCE_NAME", "miruroSourceLabel(body.servers, server)"),
        Extra("AUDIO", "if (playingDub) \"dub\" else \"sub\" (playingDub = dub && the backend really returned dub)"),
        Extra("SERVER", "server ?: serverOptions.getOrNull(serverDefaultIdx)?.name"),
    )

    /** The TV list minus SEASON_NUMBER, EPISODE_NUMBER and EPISODE_NAME. */
    val movieMiruroBackend = tvMiruroBackend.filterNot { it.key in setOf("SEASON_NUMBER", "EPISODE_NUMBER", "EPISODE_NAME") }

    // ── MiruroExtractorActivity (the on-device fallback). Same on both screens. ──

    val extractorExtract = listOf(
        Extra(MiruroExtractorActivity.EXTRA_URL, "the Miruro url"),
        Extra(MiruroExtractorActivity.EXTRA_DUB, "dub (Boolean)"),
        Extra(MiruroExtractorActivity.EXTRA_MODE, "MiruroExtractorActivity.MODE_EXTRACT"),
        Extra(MiruroExtractorActivity.EXTRA_SERVER_NAME, "server, ONLY when server is not blank"),
        Extra(MiruroExtractorActivity.EXTRA_CAPTURE_SERVERS, "true, ONLY when no server was named " +
            "and the server list for this url + track is not cached yet"),
    )

    val extractorEnumerate = listOf(
        Extra(MiruroExtractorActivity.EXTRA_URL, "the Miruro url"),
        Extra(MiruroExtractorActivity.EXTRA_DUB, "dub (Boolean)"),
        Extra(MiruroExtractorActivity.EXTRA_MODE, "MiruroExtractorActivity.MODE_ENUMERATE"),
    )

    // ── Details → details, inside the two Activities (DetailsIntents, Stage 9) ──

    /** TVShowsDetailsScreenCompose.openMediaDetails — always opens the TV screen. */
    val tvOpenDetails = listOf(
        Extra("media_type", "media.mediaType"),
        Extra("media_id", "media.tmdbId (Int)"),
        Extra("media_title", "media.title"),
        Extra("poster_url", "media.posterUrl ?: \"\""),
        Extra("backdrop_url", "media.backdropUrl ?: \"\""),
        Extra("youtube_trailer_url", "media.trailerUrl ?: \"\""),
        Extra("overview", "media.overview ?: \"\""),
        Extra("vote_average", "media.voteAverage ?: 0.0 (Double)"),
        Extra("contentRating", "media.contentRating ?: \"N/A\""),
        Extra("original_language", "media.originalLanguage ?: \"N/A\""),
        Extra("release_date", "media.releaseDate ?: \"N/A\""),
        Extra("logo_url", "media.logoUrl ?: \"\""),
        Extra("number_of_seasons", "media.numberOfSeasons ?: 0 (Int)"),
        Extra("status", "media.status ?: \"N/A\""),
        Extra("genres", "media.genres?.joinToString(\", \") ?: \"N/A\""),
        Extra("PROFILE_ID", "profileId (Int)"),
        Extra("SOURCE_SCREEN", "sourceScreen, ONLY when not null"),
        Extra("SEARCH_QUERY", "searchQuery, ONLY when not null"),
    )

    /** TV Back with a previous title on the history stack. Carries budget/revenue it never reads. */
    val tvBackToPrevious = listOf(
        Extra("media_type", "previous.mediaType"),
        Extra("media_id", "previous.tmdbId (Int)"),
        Extra("media_title", "previous.title"),
        Extra("poster_url", "previous.posterUrl ?: \"\""),
        Extra("backdrop_url", "previous.backdropUrl ?: \"\""),
        Extra("youtube_trailer_url", "previous.trailerUrl ?: \"\""),
        Extra("overview", "previous.overview ?: \"\""),
        Extra("vote_average", "previous.voteAverage ?: 0.0 (Double)"),
        Extra("contentRating", "previous.contentRating ?: \"N/A\""),
        Extra("original_language", "previous.originalLanguage ?: \"N/A\""),
        Extra("release_date", "previous.releaseDate ?: \"N/A\""),
        Extra("budget", "previous.budget ?: 0L (Long)"),
        Extra("revenue", "previous.revenue ?: 0L (Long)"),
        Extra("logo_url", "previous.logoUrl ?: \"\""),
        Extra("genres", "previous.genres?.joinToString(\", \") ?: \"N/A\""),
        Extra("number_of_seasons", "previous.numberOfSeasons ?: 0 (Int)"),
        Extra("status", "previous.status ?: \"N/A\""),
        Extra("start_in_extras", "true"),
        Extra("PROFILE_ID", "profileId (Int)"),
        Extra("SOURCE_SCREEN", "sourceScreen, ONLY when not null"),
        Extra("SEARCH_QUERY", "searchQuery, ONLY when not null"),
    )

    /** MoviesDetailsScreenCompose.openMediaDetails — always opens the movie screen. */
    val movieOpenDetails = listOf(
        Extra("media_type", "media.mediaType ?: \"movie\""),
        Extra("media_id", "media.tmdbId (Int)"),
        Extra("media_title", "media.title ?: \"\""),
        Extra("poster_url", "media.posterUrl ?: \"\""),
        Extra("backdrop_url", "media.backdropUrl ?: \"\""),
        Extra("youtube_trailer_url", "media.trailerUrl ?: \"\""),
        Extra("overview", "media.overview ?: \"\""),
        Extra("vote_average", "media.voteAverage ?: 0.0 (Double)"),
        Extra("contentRating", "media.contentRating ?: \"N/A\""),
        Extra("original_language", "media.originalLanguage ?: \"N/A\""),
        Extra("release_date", "media.releaseDate ?: \"N/A\""),
        Extra("budget", "media.budget ?: 0L (Long)"),
        Extra("revenue", "media.revenue ?: 0L (Long)"),
        Extra("logo_url", "media.logoUrl ?: \"\""),
        Extra("genres", "media.genres?.joinToString(\", \") ?: \"N/A\""),
        Extra("PROFILE_ID", "profileId (Int)"),
        Extra("SOURCE_SCREEN", "sourceScreen, ONLY when not null"),
        Extra("SEARCH_QUERY", "searchQuery, ONLY when not null"),
    )

    /** Movie Back with a previous title on the history stack: the open list plus start_in_extras. */
    val movieBackToPrevious = movieOpenDetails.flatMap {
        if (it.key == "PROFILE_ID") listOf(Extra("start_in_extras", "true"), it) else listOf(it)
    }.map { Extra(it.key, it.value.replace("media.", "previous.")) }

    val all: Map<String, List<Extra>> = mapOf(
        "tvHlsPlayer" to tvHlsPlayer,
        "movieHlsPlayer" to movieHlsPlayer,
        "tvMiruroOnDevice" to tvMiruroOnDevice,
        "movieMiruroOnDevice" to movieMiruroOnDevice,
        "tvMiruroBackend" to tvMiruroBackend,
        "movieMiruroBackend" to movieMiruroBackend,
        "extractorExtract" to extractorExtract,
        "extractorEnumerate" to extractorEnumerate,
        "tvOpenDetails" to tvOpenDetails,
        "tvBackToPrevious" to tvBackToPrevious,
        "movieOpenDetails" to movieOpenDetails,
        "movieBackToPrevious" to movieBackToPrevious,
    )
}

class DetailsLaunchExtrasGoldenTest {

    @Test
    fun `no launch lists a key twice`() {
        for ((name, extras) in DetailsLaunchExtrasGolden.all) {
            val keys = extras.map { it.key }
            assertEquals("$name has a duplicate key", keys.size, keys.toSet().size)
        }
    }

    @Test
    fun `the counts match what the source carries today`() {
        val counts = DetailsLaunchExtrasGolden.all.mapValues { it.value.size }
        assertEquals(
            mapOf(
                "tvHlsPlayer" to 18, "movieHlsPlayer" to 15,
                "tvMiruroOnDevice" to 14, "movieMiruroOnDevice" to 11,
                "tvMiruroBackend" to 15, "movieMiruroBackend" to 12,
                "extractorExtract" to 5, "extractorEnumerate" to 3,
                "tvOpenDetails" to 18, "tvBackToPrevious" to 21,
                "movieOpenDetails" to 18, "movieBackToPrevious" to 19,
            ),
            counts,
        )
    }
}
