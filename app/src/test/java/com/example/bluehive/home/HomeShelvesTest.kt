package com.example.bluehive.home

import com.example.bluehive.singleShelfComponents.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HomeShelfTab, what the home screen's one ShelfStack call does differently on
 * each tab, against the three calls it replaced (v409, 8880e5b); and the
 * carousels' pause rule.
 */
class HomeShelvesTest {

    @Test
    fun `each tab shows its own media and returns to its own shelf`() {
        assertEquals(MediaType.MOVIES, HomeShelfTab.of(CatalogTab.MOVIES).mediaType)
        assertEquals(MediaType.TV_SHOWS, HomeShelfTab.of(CatalogTab.TV_SHOWS).mediaType)
        assertEquals(MediaType.ANIME, HomeShelfTab.of(CatalogTab.ANIME).mediaType)
        assertEquals(SidebarReturnTarget.MOVIES_SHELF, HomeShelfTab.of(CatalogTab.MOVIES).returnTarget)
        assertEquals(SidebarReturnTarget.TV_SHOWS_SHELF, HomeShelfTab.of(CatalogTab.TV_SHOWS).returnTarget)
        assertEquals(SidebarReturnTarget.ANIME_SHELF, HomeShelfTab.of(CatalogTab.ANIME).returnTarget)
    }

    @Test
    fun `only the Movies stack reports its first page`() {
        assertTrue(HomeShelfTab.of(CatalogTab.MOVIES).reportsFirstPage)
        assertFalse(HomeShelfTab.of(CatalogTab.TV_SHOWS).reportsFirstPage)
        assertFalse(HomeShelfTab.of(CatalogTab.ANIME).reportsFirstPage)
    }

    @Test
    fun `the log lines are the old calls' own`() {
        val movies = HomeShelfTab.of(CatalogTab.MOVIES)
        assertNull(movies.renderLog)
        assertNull(movies.focusLog)
        assertEquals("📺 Movies shelf changed to:", movies.shelfChangedLog)
        assertEquals("📺 First shelf status:", movies.firstShelfLog)

        val tv = HomeShelfTab.of(CatalogTab.TV_SHOWS)
        assertEquals("FOCUS_DEBUG" to "🎬 Rendering TV Shows shelf", tv.renderLog)
        assertEquals("TV Shows ShelfStack focus changed:", tv.focusLog)
        assertEquals("📺 TV shelf changed to:", tv.shelfChangedLog)
        assertEquals("📺 TV first shelf status:", tv.firstShelfLog)

        val anime = HomeShelfTab.of(CatalogTab.ANIME)
        assertEquals("ANIME_DEBUG" to "🎌 Shelf rendering, shelves count check", anime.renderLog)
        assertEquals("Anime ShelfStack focus changed:", anime.focusLog)
        assertEquals("🎌 Anime shelf changed to:", anime.shelfChangedLog)
        assertEquals("🎌 Anime first shelf status:", anime.firstShelfLog)
    }

    @Test
    fun `the carousels pause while the sidebar is open or another screen is on top`() {
        assertFalse(carouselPaused(sidebarFocused = false, homeInForeground = true))
        assertTrue(carouselPaused(sidebarFocused = true, homeInForeground = true))
        assertTrue(carouselPaused(sidebarFocused = false, homeInForeground = false))
        assertTrue(carouselPaused(sidebarFocused = true, homeInForeground = false))
    }
}
