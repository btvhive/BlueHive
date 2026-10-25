package com.example.bluehive.favorites

import com.example.bluehive.api.MediaDetailResponse
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * toFavoriteEntry, which turns one batch-details answer into a grid card's
 * model: the genres JSON and every fallback, as they were before the redesign.
 */
class FavoriteEntryTest {

    private fun details(
        genres: Any? = """[{"id":28,"name":"Action"},{"id":878,"name":"Science Fiction"}]""",
        logos: Any? = "l.png",
        title: String? = "Inception",
    ) = MediaDetailResponse(
        tmdb_id = 27205, media_type = "movie", title = title, backdrop_path = "b.jpg", poster_path = "p.jpg",
        logos = logos, overview = "A thief who steals secrets...", vote_average = 8.4, content_rating = "PG-13",
        original_language = "en", release_date = "2010-07-15", genres = genres, number_of_seasons = 2,
        runtime = 148, status = "Released", youtube_trailer_url = "https://youtu.be/x",
        budget = 160_000_000L, revenue = 836_000_000L,
    )

    @Test
    fun `every field comes from the details, the id and date from the favourite`() {
        assertEquals(
            FavoriteEntry(
                favoriteId = 11, tmdbId = 27205, mediaType = "movie", addedAt = "2026-10-01",
                title = "Inception", posterPath = "p.jpg", backdropPath = "b.jpg", releaseDate = "2010-07-15",
                voteAverage = 8.4, overview = "A thief who steals secrets...", contentRating = "PG-13",
                originalLanguage = "en", numberOfSeasons = 2, runtime = 148, status = "Released",
                trailerUrl = "https://youtu.be/x", logoUrl = "l.png",
                genresList = listOf("Action", "Science Fiction"), genresString = "Action, Science Fiction",
            ),
            details().toFavoriteEntry(favoriteId = 11, addedAt = "2026-10-01"),
        )
    }

    @Test
    fun `a genre without a name is skipped`() {
        val entry = details(genres = """[{"id":1},{"id":18,"name":"Drama"}]""").toFavoriteEntry(1, "d")
        assertEquals(listOf("Drama"), entry.genresList)
        assertEquals("Drama", entry.genresString)
    }

    @Test
    fun `no usable genres give an empty list and N-A`() {
        // An empty array; none at all; not a string (Gson turns a JSON array
        // into a list); not JSON; and an array of names instead of objects,
        // where the first element throws and the whole list is dropped.
        for (genres in listOf("[]", null, "", listOf("Action"), "Action", """["Drama"]""")) {
            val entry = details(genres = genres).toFavoriteEntry(1, "d")
            assertEquals("$genres", emptyList<String>(), entry.genresList)
            assertEquals("$genres", "N/A", entry.genresString)
        }
    }

    @Test
    fun `the logo is kept only as a non-blank string`() {
        assertEquals("l.png", details(logos = "l.png").toFavoriteEntry(1, "d").logoUrl)
        for (logos in listOf(null, "", "  ", listOf("l.png"))) {
            assertEquals("$logos", "", details(logos = logos).toFavoriteEntry(1, "d").logoUrl)
        }
    }

    @Test
    fun `a missing title becomes Unknown`() {
        assertEquals("Unknown", details(title = null).toFavoriteEntry(1, "d").title)
    }
}
