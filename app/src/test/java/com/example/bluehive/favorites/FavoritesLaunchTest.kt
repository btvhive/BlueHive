package com.example.bluehive.favorites

import com.example.bluehive.api.MediaDetailResponse
import com.example.bluehive.details.shared.navigation.DetailsExtra
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FavoritesLaunch against the record of every extra the Favorites screen put
 * on a details launch before the redesign (FavoritesLaunchExtrasGolden): the
 * same keys in the same order, each value from the source the record names,
 * each with the type its putExtra needs.
 */
class FavoritesLaunchTest {

    private val movie = FavoriteEntry(
        favoriteId = 11, tmdbId = 27205, mediaType = "movie", addedAt = "2026-10-01",
        title = "Inception", genresString = "Action, Science Fiction",
    )
    private val show = FavoriteEntry(
        favoriteId = 12, tmdbId = 1396, mediaType = "tv", addedAt = "2026-10-02",
        title = "Breaking Bad", genresString = "Drama, Crime",
    )
    private val full = MediaDetailResponse(
        tmdb_id = 27205, media_type = "movie", title = "Inception", backdrop_path = "b.jpg",
        poster_path = "p.jpg", logos = "l.png", overview = "A thief who steals secrets...", vote_average = 8.4,
        content_rating = "PG-13", original_language = "en", release_date = "2010-07-15",
        genres = """[{"id":28,"name":"Action"}]""", number_of_seasons = 5, runtime = 148, status = "Released",
        youtube_trailer_url = "https://youtu.be/x", budget = 160_000_000L, revenue = 836_000_000L,
    )
    private val bare = MediaDetailResponse(
        tmdb_id = 1396, media_type = "tv", title = null, backdrop_path = null, poster_path = null, logos = null,
        overview = null, vote_average = null, content_rating = null, original_language = null,
        release_date = null, genres = null, number_of_seasons = null, runtime = null, status = null,
        youtube_trailer_url = null, budget = null, revenue = null,
    )

    private fun List<DetailsExtra>.keys() = map { it.key }
    private fun List<DetailsExtra>.of(key: String) = single { it.key == key }

    @Test
    fun `each launch carries the recorded keys in order`() {
        assertEquals(FavoritesLaunchExtrasGolden.movie.map { it.key }, FavoritesLaunch.extras(movie, full, 7).keys())
        assertEquals(FavoritesLaunchExtrasGolden.tv.map { it.key }, FavoritesLaunch.extras(show, full, 7).keys())
    }

    @Test
    fun `each extra has the type the record names`() {
        for ((entry, golden) in listOf(movie to FavoritesLaunchExtrasGolden.movie, show to FavoritesLaunchExtrasGolden.tv)) {
            val extras = FavoritesLaunch.extras(entry, full, 7)
            for (g in golden) {
                val expected = when {
                    "(Int)" in g.value -> DetailsExtra.Whole::class
                    "(Double)" in g.value -> DetailsExtra.Real::class
                    else -> DetailsExtra.Text::class
                }
                assertEquals(g.key, expected, extras.of(g.key)::class)
            }
        }
    }

    @Test
    fun `a movie opens the movie screen and any other type the TV screen`() {
        assertTrue(FavoritesLaunch.isMovie(movie))
        for (type in listOf("tv", "anime", "")) {
            val entry = show.copy(mediaType = type)
            assertFalse(type, FavoritesLaunch.isMovie(entry))
            assertEquals(type, FavoritesLaunchExtrasGolden.tv.map { it.key }, FavoritesLaunch.extras(entry, full, 7).keys())
        }
    }

    @Test
    fun `values of a full title`() {
        val tv = FavoritesLaunch.extras(show, full, 7)
        assertEquals(DetailsExtra.Text("media_type", "movie"), tv.of("media_type"))
        assertEquals(DetailsExtra.Whole("media_id", 27205), tv.of("media_id"))
        assertEquals(DetailsExtra.Text("media_title", "Inception"), tv.of("media_title"))
        assertEquals(DetailsExtra.Text("poster_url", "p.jpg"), tv.of("poster_url"))
        assertEquals(DetailsExtra.Text("backdrop_url", "b.jpg"), tv.of("backdrop_url"))
        assertEquals(DetailsExtra.Text("youtube_trailer_url", "https://youtu.be/x"), tv.of("youtube_trailer_url"))
        assertEquals(DetailsExtra.Text("overview", "A thief who steals secrets..."), tv.of("overview"))
        assertEquals(DetailsExtra.Real("vote_average", 8.4), tv.of("vote_average"))
        assertEquals(DetailsExtra.Text("contentRating", "PG-13"), tv.of("contentRating"))
        assertEquals(DetailsExtra.Text("original_language", "en"), tv.of("original_language"))
        assertEquals(DetailsExtra.Text("release_date", "2010-07-15"), tv.of("release_date"))
        assertEquals(DetailsExtra.Text("logo_url", "l.png"), tv.of("logo_url"))
        assertEquals(DetailsExtra.Whole("number_of_seasons", 5), tv.of("number_of_seasons"))
        assertEquals(DetailsExtra.Text("status", "Released"), tv.of("status"))
        assertEquals(DetailsExtra.Whole("PROFILE_ID", 7), tv.of("PROFILE_ID"))
        assertEquals(DetailsExtra.Text("SOURCE_SCREEN", "FAVORITES"), tv.of("SOURCE_SCREEN"))
    }

    @Test
    fun `missing fields fall back as before`() {
        for (entry in listOf(movie, show)) {
            val extras = FavoritesLaunch.extras(entry, bare, -1)
            assertEquals(DetailsExtra.Text("media_title", ""), extras.of("media_title"))
            assertEquals(DetailsExtra.Text("poster_url", ""), extras.of("poster_url"))
            assertEquals(DetailsExtra.Text("backdrop_url", ""), extras.of("backdrop_url"))
            assertEquals(DetailsExtra.Text("youtube_trailer_url", ""), extras.of("youtube_trailer_url"))
            assertEquals(DetailsExtra.Text("overview", ""), extras.of("overview"))
            assertEquals(DetailsExtra.Real("vote_average", 0.0), extras.of("vote_average"))
            assertEquals(DetailsExtra.Text("contentRating", "N/A"), extras.of("contentRating"))
            assertEquals(DetailsExtra.Text("original_language", "N/A"), extras.of("original_language"))
            assertEquals(DetailsExtra.Text("release_date", "N/A"), extras.of("release_date"))
            assertEquals(DetailsExtra.Text("logo_url", ""), extras.of("logo_url"))
            assertEquals(DetailsExtra.Whole("PROFILE_ID", -1), extras.of("PROFILE_ID"))
        }
        val tv = FavoritesLaunch.extras(show, bare, -1)
        assertEquals(DetailsExtra.Whole("number_of_seasons", 0), tv.of("number_of_seasons"))
        assertEquals(DetailsExtra.Text("status", "N/A"), tv.of("status"))
    }

    @Test
    fun `the logo is used only when it is a non-blank string`() {
        for (logos in listOf("", "   ", listOf("l.png"), 42)) {
            val extras = FavoritesLaunch.extras(movie, full.copy(logos = logos), 7)
            assertEquals("$logos", DetailsExtra.Text("logo_url", ""), extras.of("logo_url"))
        }
    }

    @Test
    fun `the genres come from the favourite, not the details`() {
        assertEquals(DetailsExtra.Text("genres", "Action, Science Fiction"), FavoritesLaunch.extras(movie, full, 7).of("genres"))
        assertEquals(DetailsExtra.Text("genres", "N/A"), FavoritesLaunch.extras(movie.copy(genresString = "N/A"), full, 7).of("genres"))
    }

    @Test
    fun `a type Gson left null is passed on as null`() {
        // Gson ignores Kotlin's non-null types, so media_type can be null at runtime.
        val gsonDetails = Gson().fromJson("""{"tmdb_id": 5}""", MediaDetailResponse::class.java)
        val extras = FavoritesLaunch.extras(movie, gsonDetails, 1)
        assertEquals(DetailsExtra.Text("media_type", null), extras.of("media_type"))
        assertEquals(DetailsExtra.Whole("media_id", 5), extras.of("media_id"))
        assertEquals(DetailsExtra.Text("media_title", ""), extras.of("media_title"))
    }
}
