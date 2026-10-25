package com.example.bluehive.home

import com.example.bluehive.singleShelfComponents.MediaType


/**
 * What the home screen's one ShelfStack call does differently on each tab: the
 * media type, where focus returns to from the sidebar, whether the stack
 * reports its first page (the entry overlay waits on the Movies one), and the
 * log lines each of the three calls it replaced wrote, word for word.
 *
 * Since Stage H1 of the Favorites and Home redesign; read off the old calls by
 * script, and pinned by HomeShelvesTest.
 */
internal class HomeShelfTab private constructor(
    val mediaType: MediaType,
    val returnTarget: SidebarReturnTarget,
    val reportsFirstPage: Boolean,
    /** Logged each time the stack is composed, as tag to message; null logs nothing. */
    val renderLog: Pair<String, String>?,
    /** Starts the FOCUS_DEBUG line a focus change logs; null logs nothing. */
    val focusLog: String?,
    val shelfChangedLog: String,
    val firstShelfLog: String,
) {
    companion object {
        private val movies = HomeShelfTab(
            mediaType        = MediaType.MOVIES,
            returnTarget     = SidebarReturnTarget.MOVIES_SHELF,
            reportsFirstPage = true,
            renderLog        = null,
            focusLog         = null,
            shelfChangedLog  = "📺 Movies shelf changed to:",
            firstShelfLog    = "📺 First shelf status:",
        )
        private val tvShows = HomeShelfTab(
            mediaType        = MediaType.TV_SHOWS,
            returnTarget     = SidebarReturnTarget.TV_SHOWS_SHELF,
            reportsFirstPage = false,
            renderLog        = "FOCUS_DEBUG" to "🎬 Rendering TV Shows shelf",
            focusLog         = "TV Shows ShelfStack focus changed:",
            shelfChangedLog  = "📺 TV shelf changed to:",
            firstShelfLog    = "📺 TV first shelf status:",
        )
        private val anime = HomeShelfTab(
            mediaType        = MediaType.ANIME,
            returnTarget     = SidebarReturnTarget.ANIME_SHELF,
            reportsFirstPage = false,
            renderLog        = "ANIME_DEBUG" to "🎌 Shelf rendering, shelves count check",
            focusLog         = "Anime ShelfStack focus changed:",
            shelfChangedLog  = "🎌 Anime shelf changed to:",
            firstShelfLog    = "🎌 Anime first shelf status:",
        )

        fun of(tab: CatalogTab): HomeShelfTab = when (tab) {
            CatalogTab.MOVIES -> movies
            CatalogTab.TV_SHOWS -> tvShows
            CatalogTab.ANIME -> anime
        }
    }
}
