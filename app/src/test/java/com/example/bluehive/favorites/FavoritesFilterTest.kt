package com.example.bluehive.favorites

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * filterAndSort, the list the Favorites grid shows: every type tab, every genre
 * and sort the dropdowns offer, as FavoritesScreen built it before the
 * redesign. And FavoritesFilter, which keeps it up to date.
 */
class FavoritesFilterTest {

    private fun fav(id: Int, title: String, type: String, released: String? = null, vararg genres: String) =
        FavoriteEntry(
            favoriteId = id, tmdbId = 1000 + id, mediaType = type, addedAt = "2026-10-0$id",
            title = title, releaseDate = released, genresList = genres.toList(),
        )

    private val dune = fav(1, "Dune", "movie", "2024-03-01", "Science Fiction", "Adventure")
    private val bear = fav(2, "the Bear", "tv", "2022-06-23", "Comedy", "Drama")
    private val arrival = fav(3, "Arrival", "movie", null, "Drama", "Science Fiction")
    private val severance = fav(4, "Severance", "tv", "2022-02-18", "Sci-Fi & Fantasy", "Drama", "Mystery")
    private val all = listOf(dune, bear, arrival, severance)

    private fun ids(list: List<FavoriteEntry>) = list.map { it.favoriteId }

    @Test
    fun `the type tabs`() {
        assertEquals(listOf(4, 3, 2, 1), ids(filterAndSort(all, "all", "All Content", "Newest")))
        assertEquals(listOf(3, 1), ids(filterAndSort(all, "movies", "All Content", "Newest")))
        assertEquals(listOf(4, 2), ids(filterAndSort(all, "tv", "All Content", "Newest")))
        // Any other value counts as All Media.
        assertEquals(listOf(4, 3, 2, 1), ids(filterAndSort(all, "anime", "All Content", "Newest")))
    }

    @Test
    fun `a genre keeps the titles that have it, in any case`() {
        assertEquals(listOf(4, 3, 2), ids(filterAndSort(all, "all", "Drama", "Newest")))
        assertEquals(listOf(4, 3, 2), ids(filterAndSort(all, "all", "dRAMA", "Newest")))
        assertEquals(listOf(3, 1), ids(filterAndSort(all, "all", "Science Fiction", "Newest")))
        assertEquals(listOf(4), ids(filterAndSort(all, "tv", "Mystery", "Newest")))
        assertEquals(emptyList<Int>(), ids(filterAndSort(all, "movies", "Comedy", "Newest")))
        assertEquals(emptyList<Int>(), ids(filterAndSort(all, "all", "Western", "Newest")))
    }

    @Test
    fun `every genre the Filter dropdown offers is matched by name`() {
        for (genre in genreOptions.drop(1)) {
            val entry = fav(9, "X", "movie", null, genre.uppercase())
            assertEquals(genre, listOf(9), ids(filterAndSort(listOf(entry), "all", genre, "Newest")))
        }
        // "All Content" filters nothing out, not even a title with no genres.
        assertEquals("All Content", genreOptions.first())
        assertEquals(listOf(9), ids(filterAndSort(listOf(fav(9, "X", "movie")), "all", "All Content", "Newest")))
    }

    @Test
    fun `every sort the Sort dropdown offers`() {
        val expected = linkedMapOf(
            "Newest" to listOf(4, 3, 2, 1),
            "Oldest" to listOf(1, 2, 3, 4),
            "A–Z" to listOf(3, 1, 4, 2), // Arrival, Dune, Severance, the Bear: case ignored
            "Z–A" to listOf(2, 4, 1, 3),
            "By Release Date" to listOf(1, 2, 4, 3), // newest first, a missing date last
        )
        assertEquals(sortOptions, expected.keys.toList())
        for ((sort, order) in expected) {
            assertEquals(sort, order, ids(filterAndSort(all, "all", "All Content", sort)))
        }
    }

    @Test
    fun `an unknown sort is Newest`() {
        assertEquals(listOf(4, 3, 2, 1), ids(filterAndSort(all, "all", "All Content", "Popularity")))
    }

    @Test
    fun `equal sort keys keep the server's order`() {
        val twins = listOf(fav(5, "Alien", "movie", "2020-01-01"), fav(6, "alien", "movie", "2020-01-01"))
        for (sort in listOf("A–Z", "Z–A", "By Release Date")) {
            assertEquals(sort, listOf(5, 6), ids(filterAndSort(twins, "all", "All Content", sort)))
        }
    }

    @Test
    fun `type, genre and sort together`() {
        assertEquals(listOf(2, 4), ids(filterAndSort(all, "tv", "Drama", "Oldest")))
        assertEquals(listOf(3), ids(filterAndSort(all, "movies", "drama", "Z–A")))
    }

    @Test
    fun `the holder's list follows the filter and the favourites`() {
        val data = FavoritesData()
        val filter = FavoritesFilter(data.entries)
        data.entries.addAll(all)
        assertEquals(listOf(4, 3, 2, 1), ids(filter.filtered))
        filter.typeTab = "movies"
        assertEquals(listOf(3, 1), ids(filter.filtered))
        filter.sort = "Oldest"
        assertEquals(listOf(1, 3), ids(filter.filtered))
        filter.genre = "Drama"
        assertEquals(listOf(3), ids(filter.filtered))
        data.entries.add(fav(7, "Tár", "movie", null, "Drama"))
        assertEquals(listOf(3, 7), ids(filter.filtered))
    }
}
