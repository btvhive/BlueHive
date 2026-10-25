package com.example.bluehive.favorites

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Which favourites the grid shows, and in what order: the type tab, the genre
 * and the sort, applied to every favourite in [entries] (FavoritesData's list).
 *
 * Moved here from FavoritesScreen in Stage F2 of the Favorites and Home
 * redesign (docs/favorites-home-redesign-architecture.md).
 */
@Stable
internal class FavoritesFilter(private val entries: SnapshotStateList<FavoriteEntry>) {

    /** The type tab: "all", "movies" or "tv". */
    var typeTab by mutableStateOf("all")
    var genre  by mutableStateOf("All Content")
    var sort   by mutableStateOf("Newest")

    /** The Filter or Sort dropdown is open: the grid fades behind it. */
    var isAnyDropdownExpanded by mutableStateOf(false)

    /** The cards the grid shows, in order. */
    val filtered: List<FavoriteEntry> by derivedStateOf { filterAndSort(entries, typeTab, genre, sort) }
}

/**
 * [entries] as the grid shows them for [typeTab] ("all", "movies" or "tv"),
 * [genre] and [sort]. Pure, so FavoritesFilterTest pins every type, genre and
 * sort.
 */
internal fun filterAndSort(entries: List<FavoriteEntry>, typeTab: String, genre: String, sort: String): List<FavoriteEntry> {
    var items: List<FavoriteEntry> = when (typeTab) {
        "movies" -> entries.filter { it.mediaType == "movie" }
        "tv"     -> entries.filter { it.mediaType == "tv" }
        else     -> entries.toList()
    }
    if (genre != "All Content") {
        items = items.filter { entry ->
            entry.genresList.any { it.equals(genre, ignoreCase = true) }
        }
    }
    items = when (sort) {
        "Oldest"          -> items.sortedBy { it.favoriteId }
        "A–Z"             -> items.sortedBy { it.title.lowercase() }
        "Z–A"             -> items.sortedByDescending { it.title.lowercase() }
        "By Release Date" -> items.sortedByDescending { it.releaseDate ?: "" }
        else              -> items.sortedByDescending { it.favoriteId }  // Newest
    }
    return items
}
