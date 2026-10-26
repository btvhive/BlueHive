package com.example.bluehive.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The home screen's state holders since Stage H2: their start values, the tab
 * neighbours, and when the Continue Watching panel counts as shown.
 */
class HomeStateTest {

    @Test
    fun `each tab's neighbour is the next one along, round to the first`() {
        assertEquals(CatalogTab.TV_SHOWS, neighborOf(CatalogTab.MOVIES))
        assertEquals(CatalogTab.ANIME, neighborOf(CatalogTab.TV_SHOWS))
        assertEquals(CatalogTab.MOVIES, neighborOf(CatalogTab.ANIME))
    }

    @Test
    fun `Continue Watching shows once content has started on the first shelf, unless the profile has no history`() {
        assertTrue(continueWatchingPanelShown(startContent = true, isOnFirstShelf = true, hasHistory = true))
        // Not known yet: shown, so a profile with history never sees the layout change.
        assertTrue(continueWatchingPanelShown(startContent = true, isOnFirstShelf = true, hasHistory = null))
        assertFalse(continueWatchingPanelShown(startContent = true, isOnFirstShelf = true, hasHistory = false))
        assertFalse(continueWatchingPanelShown(startContent = false, isOnFirstShelf = true, hasHistory = true))
        assertFalse(continueWatchingPanelShown(startContent = true, isOnFirstShelf = false, hasHistory = true))
    }

    @Test
    fun `the holder asks the entry overlay whether content has started`() {
        val entry = HomeEntryTransition()
        val shelves = HomeShelfState()
        assertFalse(shelves.continueWatchingShown(entry))
        entry.startContent = true
        assertTrue(shelves.continueWatchingShown(entry))
        shelves.continueWatchingHasHistory = false
        assertFalse(shelves.continueWatchingShown(entry))
    }

    @Test
    fun `start values are the old screen's`() {
        val entry = HomeEntryTransition()
        assertTrue(entry.showEntryTransition)
        assertFalse(entry.minTransitionElapsed)
        assertFalse(entry.firstShelfDataReady)
        assertFalse(entry.startContent)

        val sidebar = HomeSidebarState()
        assertFalse(sidebar.isSidebarFocused)
        assertFalse(sidebar.sidebarCanFocus)
        assertNull(sidebar.sidebarReturnTarget)
        assertEquals(SidebarReturnTarget.SEARCH_BAR, sidebar.lastFocusedTarget)

        val shelves = HomeShelfState()
        assertTrue(shelves.isOnFirstShelf)
        assertNull(shelves.enterShelfAt)
        assertNull(shelves.scrollShelfTo)
        assertNull(shelves.continueWatchingHasHistory)

        val tabs = CatalogTabsState()
        assertEquals(CatalogTab.MOVIES, tabs.selectedTab)
        assertNull(tabs.pendingFocusRequester)
        assertFalse(tabs.radioSoundArmed)
    }

    @Test
    fun `opening the sidebar records where from and asks for focus`() {
        val sidebar = HomeSidebarState()
        sidebar.openSidebar(SidebarReturnTarget.TRENDING)
        assertEquals(SidebarReturnTarget.TRENDING, sidebar.sidebarReturnTarget)
        assertTrue(sidebar.sidebarCanFocus)
        assertTrue(sidebar.requestSidebarFocus)
    }
}
