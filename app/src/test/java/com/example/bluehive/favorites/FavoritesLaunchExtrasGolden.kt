package com.example.bluehive.favorites

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * EVERY EXTRA THE FAVORITES SCREEN PUTS ON A DETAILS LAUNCH TODAY, recorded at
 * v405 before the Favorites and Home redesign touches any of it
 * (docs/favorites-home-redesign-architecture.md, contract F-C6).
 *
 * Read off FavoritesScreen's navigateToDetails (sidebarComponents/
 * SidebarFavorites.kt) by script, in source order, then checked by hand. Each
 * entry is the key and where its value comes from: `details` is the
 * getMediaDetails answer fetched on the press, `entry` the pressed card's
 * FavoriteEntry. These differ from DetailsIntents on purpose (§9, #5).
 *
 * Stage F1 moves the launch into FavoritesLaunch, which builds these as a typed
 * list; its test checks that list against this record. Until then the test
 * below keeps the record internally consistent.
 */
object FavoritesLaunchExtrasGolden {

    data class Extra(val key: String, val value: String)

    /** entry.mediaType is "movie": MoviesDetailsScreenCompose. No budget or revenue. */
    val movie = listOf(
        Extra("media_type", "details.media_type"),
        Extra("media_id", "details.tmdb_id (Int)"),
        Extra("media_title", "details.title ?: \"\""),
        Extra("poster_url", "details.poster_path ?: \"\""),
        Extra("backdrop_url", "details.backdrop_path ?: \"\""),
        Extra("youtube_trailer_url", "details.youtube_trailer_url ?: \"\""),
        Extra("overview", "details.overview ?: \"\""),
        Extra("vote_average", "details.vote_average ?: 0.0 (Double)"),
        Extra("contentRating", "details.content_rating ?: \"N/A\""),
        Extra("original_language", "details.original_language ?: \"N/A\""),
        Extra("release_date", "details.release_date ?: \"N/A\""),
        Extra("logo_url", "(details.logos as? String)?.takeIf { it.isNotBlank() } ?: \"\""),
        Extra("genres", "entry.genresString (the batch's genre names joined with \", \"; \"N/A\" when none)"),
        Extra("PROFILE_ID", "profileId (Int)"),
        Extra("SOURCE_SCREEN", "SourceScreen.FAVORITES (always)"),
    )

    /**
     * Any other entry.mediaType: TVShowsDetailsScreenCompose. The movie list
     * with the season count and status before PROFILE_ID.
     */
    val tv = movie.flatMap {
        if (it.key == "PROFILE_ID") listOf(
            Extra("number_of_seasons", "details.number_of_seasons ?: 0 (Int)"),
            Extra("status", "details.status ?: \"N/A\""),
            it,
        ) else listOf(it)
    }

    val all: Map<String, List<Extra>> = mapOf(
        "movie" to movie,
        "tv" to tv,
    )
}

class FavoritesLaunchExtrasGoldenTest {

    @Test
    fun `no launch lists a key twice`() {
        for ((name, extras) in FavoritesLaunchExtrasGolden.all) {
            val keys = extras.map { it.key }
            assertEquals("$name has a duplicate key", keys.size, keys.toSet().size)
        }
    }

    @Test
    fun `the counts match what the source carries today`() {
        assertEquals(
            mapOf("movie" to 15, "tv" to 17),
            FavoritesLaunchExtrasGolden.all.mapValues { it.value.size },
        )
    }
}
