package com.example.bluehive.details.shared.navigation

import com.example.bluehive.details.shared.playback.DetailsLaunchExtrasGolden
import com.example.bluehive.models.MediaItem
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * DetailsIntents against the record of every extra the two details Activities
 * put on their details-to-details launches before the redesign
 * (DetailsLaunchExtrasGolden): the same keys in the same order, each value
 * from the source the record names, each with the type its putExtra needs.
 */
class DetailsIntentsTest {

    private val show = MediaItem(
        tmdbId = 1396, mediaId = 1396, title = "Breaking Bad", mediaType = "tv",
        posterUrl = "p.jpg", backdropUrl = "b.jpg", logoUrl = "l.png", overview = "A chemistry teacher...",
        releaseDate = "2008-01-20", status = "Ended", voteAverage = 8.9, voteCount = 100, popularity = 1.0,
        popularityRank = 3, originalLanguage = "en", numberOfSeasons = 5, numberOfEpisodes = 62,
        contentRating = "TV-MA", runtime = 47, budget = 3_000_000L, revenue = 4_000_000L,
        trailerUrl = "https://youtu.be/x", genres = listOf("Drama", "Crime"), similarItems = null,
        whereToWatch = null,
    )
    private val bare = MediaItem(
        tmdbId = 27205, mediaId = 27205, title = "Inception", mediaType = "movie",
        posterUrl = null, backdropUrl = null, logoUrl = null, overview = null, releaseDate = null, status = null,
        voteAverage = null, voteCount = null, popularity = null, popularityRank = null, originalLanguage = null,
        numberOfSeasons = null, numberOfEpisodes = null, contentRating = null, runtime = null, budget = null,
        revenue = null, trailerUrl = null, genres = null, similarItems = null, whereToWatch = null,
    )

    private fun List<DetailsExtra>.keys() = map { it.key }
    private fun List<DetailsExtra>.of(key: String) = single { it.key == key }
    private fun golden(name: String) = DetailsLaunchExtrasGolden.all.getValue(name).map { it.key }

    @Test
    fun `every launch carries the recorded keys in order`() {
        assertEquals(golden("tvOpenDetails"), DetailsIntents.openExtras(DetailsScreen.Tv, show, 7, "SEARCH", "breaking").keys())
        assertEquals(golden("movieOpenDetails"), DetailsIntents.openExtras(DetailsScreen.Movie, show, 7, "SEARCH", "breaking").keys())
        assertEquals(golden("tvBackToPrevious"), DetailsIntents.backExtras(DetailsScreen.Tv, show, 7, "SEARCH", "breaking").keys())
        assertEquals(golden("movieBackToPrevious"), DetailsIntents.backExtras(DetailsScreen.Movie, show, 7, "SEARCH", "breaking").keys())
    }

    @Test
    fun `the source screen and search query ride along only when set`() {
        for (screen in DetailsScreen.values()) {
            for (extras in listOf(DetailsIntents.openExtras(screen, show, 7, null, null),
                                  DetailsIntents.backExtras(screen, show, 7, null, null))) {
                assertFalse(extras.any { it.key == "SOURCE_SCREEN" || it.key == "SEARCH_QUERY" })
            }
            val onlySource = DetailsIntents.openExtras(screen, show, 7, "HISTORY", null)
            assertEquals(DetailsExtra.Text("SOURCE_SCREEN", "HISTORY"), onlySource.last())
        }
    }

    @Test
    fun `values and types of a full title`() {
        val tv = DetailsIntents.openExtras(DetailsScreen.Tv, show, 7, "SEARCH", "breaking")
        assertEquals(DetailsExtra.Text("media_type", "tv"), tv.of("media_type"))
        assertEquals(DetailsExtra.Whole("media_id", 1396), tv.of("media_id"))
        assertEquals(DetailsExtra.Real("vote_average", 8.9), tv.of("vote_average"))
        assertEquals(DetailsExtra.Whole("number_of_seasons", 5), tv.of("number_of_seasons"))
        assertEquals(DetailsExtra.Text("genres", "Drama, Crime"), tv.of("genres"))
        assertEquals(DetailsExtra.Whole("PROFILE_ID", 7), tv.of("PROFILE_ID"))
        assertEquals(DetailsExtra.Text("SEARCH_QUERY", "breaking"), tv.of("SEARCH_QUERY"))

        val movie = DetailsIntents.openExtras(DetailsScreen.Movie, show, 7, null, null)
        assertEquals(DetailsExtra.Amount("budget", 3_000_000L), movie.of("budget"))
        assertEquals(DetailsExtra.Amount("revenue", 4_000_000L), movie.of("revenue"))

        val back = DetailsIntents.backExtras(DetailsScreen.Tv, show, 7, null, null)
        assertEquals(DetailsExtra.Flag("start_in_extras", true), back.of("start_in_extras"))
        assertEquals(DetailsExtra.Amount("budget", 3_000_000L), back.of("budget"))
    }

    @Test
    fun `missing fields fall back as before`() {
        for (extras in listOf(DetailsIntents.openExtras(DetailsScreen.Tv, bare, -1, null, null),
                              DetailsIntents.backExtras(DetailsScreen.Tv, bare, -1, null, null),
                              DetailsIntents.openExtras(DetailsScreen.Movie, bare, -1, null, null),
                              DetailsIntents.backExtras(DetailsScreen.Movie, bare, -1, null, null))) {
            assertEquals(DetailsExtra.Text("poster_url", ""), extras.of("poster_url"))
            assertEquals(DetailsExtra.Text("overview", ""), extras.of("overview"))
            assertEquals(DetailsExtra.Real("vote_average", 0.0), extras.of("vote_average"))
            assertEquals(DetailsExtra.Text("contentRating", "N/A"), extras.of("contentRating"))
            assertEquals(DetailsExtra.Text("release_date", "N/A"), extras.of("release_date"))
            assertEquals(DetailsExtra.Text("genres", "N/A"), extras.of("genres"))
        }
        assertEquals(DetailsExtra.Whole("number_of_seasons", 0),
                     DetailsIntents.openExtras(DetailsScreen.Tv, bare, -1, null, null).of("number_of_seasons"))
        assertEquals(DetailsExtra.Amount("budget", 0L),
                     DetailsIntents.openExtras(DetailsScreen.Movie, bare, -1, null, null).of("budget"))
    }

    @Test
    fun `a type or title Gson left null falls back on the movie screen only`() {
        // Gson ignores Kotlin's non-null types, so these can be null at runtime.
        val gsonItem = Gson().fromJson("""{"tmdb_id": 5, "media_id": 5}""", MediaItem::class.java)
        val movie = DetailsIntents.openExtras(DetailsScreen.Movie, gsonItem, 1, null, null)
        assertEquals(DetailsExtra.Text("media_type", "movie"), movie.of("media_type"))
        assertEquals(DetailsExtra.Text("media_title", ""), movie.of("media_title"))
        val tv = DetailsIntents.openExtras(DetailsScreen.Tv, gsonItem, 1, null, null)
        assertEquals(DetailsExtra.Text("media_type", null), tv.of("media_type"))
        assertEquals(DetailsExtra.Text("media_title", null), tv.of("media_title"))
    }
}
