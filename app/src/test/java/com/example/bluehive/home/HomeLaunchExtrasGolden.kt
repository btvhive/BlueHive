package com.example.bluehive.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * EVERY EXTRA THE HOME SCREEN PUTS ON A DETAILS LAUNCH TODAY, recorded at v405
 * before the Favorites and Home redesign touches the screen around it
 * (docs/favorites-home-redesign-architecture.md).
 *
 * Read off HomeScreenCompose.openDetailsScreen by script, in source order, then
 * checked by hand. Every title opened from the home screen's trending panel and
 * shelves goes through it (they get it as onMediaClick). Each entry is the key
 * and where its value comes from; `media` is the MediaItem pressed. There is no
 * SOURCE_SCREEN, so Back from that details page finishes back to this screen.
 * These differ from DetailsIntents on purpose (§9, #5).
 *
 * The launch stays in the Activity, which the redesign does not move; this
 * record and its test guard it while the screen around it is split.
 */
object HomeLaunchExtrasGolden {

    data class Extra(val key: String, val value: String)

    /** media.mediaType, lowercased, is "tv": TVShowsDetailsScreenCompose. PROFILE_ID comes first. */
    val tv = listOf(
        Extra("PROFILE_ID", "profileId (Int)"),
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
        Extra("genres", "media.genres?.joinToString(\", \") ?: \"N/A\""),
        Extra("number_of_seasons", "media.numberOfSeasons ?: 0 (Int)"),
        Extra("status", "media.status ?: \"N/A\""),
    )

    /**
     * Anything else (unlike Favorites, an unknown type opens the MOVIE screen):
     * MoviesDetailsScreenCompose, with budget and revenue where the TV list has
     * the season count and status.
     */
    val movie = tv.dropLast(2) + listOf(
        Extra("budget", "media.budget ?: 0L (Long)"),
        Extra("revenue", "media.revenue ?: 0L (Long)"),
    )

    val all: Map<String, List<Extra>> = mapOf(
        "tv" to tv,
        "movie" to movie,
    )
}

class HomeLaunchExtrasGoldenTest {

    @Test
    fun `no launch lists a key twice`() {
        for ((name, extras) in HomeLaunchExtrasGolden.all) {
            val keys = extras.map { it.key }
            assertEquals("$name has a duplicate key", keys.size, keys.toSet().size)
        }
    }

    @Test
    fun `the counts match what the source carries today`() {
        assertEquals(
            mapOf("tv" to 16, "movie" to 16),
            HomeLaunchExtrasGolden.all.mapValues { it.value.size },
        )
    }
}
