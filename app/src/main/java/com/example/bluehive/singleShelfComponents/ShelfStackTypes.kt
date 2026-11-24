package com.example.bluehive.singleShelfComponents


import com.example.bluehive.models.MediaItem

/**
 * Defines the type of media content being displayed
 */
enum class MediaType {
    MOVIES,
    TV_SHOWS,
    ANIME,
}

/**
 * Defines the streaming service or category shelf
 * Shared across all media types
 */
enum class ContentShelf {
    // The shelf order on the Movies and TV Shows tabs is this enum's order
    // (2026-11-21): New Releases first (NewContent.kt), then Fresh Picks
    // (FreshPicks.kt), then What's Popular -- which now mixes in Top Rated one
    // and one, so TOP_RATED is no longer a row of its own there (ShelfStack).
    NEW_RELEASES,
    FRESH_PICKS,
    POPULAR,
    TOP_RATED,
    NETFLIX,
    PRIME_VIDEO,
    DISNEY_PLUS,
    HULU,
    PARAMOUNT_PLUS,
    APPLE_TV_PLUS,
    HBO_MAX,
    PEACOCK,
    ANIME_SERIES,
    ANIME_MOVIES,
    ANIME_NEW_SEASON,
}


/**
 * Manages a sliding window of pages in memory
 */
data class PageWindow(
    val loadedPages: MutableMap<Int, List<MediaItem>> = mutableMapOf(),
    var windowStartPage: Int = 0,
    var windowEndPage: Int = 0,
    var focusedTmdbId: Int? = null,
    var pageNumberIncrement: Int = 0
) {
    fun getAllItems(): List<MediaItem> {
        val seen = HashSet<Int>()
        val result = mutableListOf<MediaItem>()
        for (page in windowStartPage..windowEndPage) {
            val pageItems = loadedPages[page] ?: continue
            for (item in pageItems) {
                if (seen.add(item.tmdbId)) {   // add() == false means already present → skip
                    result.add(item)
                }
            }
        }
        return result
    }

    fun getPageCount(): Int = windowEndPage - windowStartPage + 1
}