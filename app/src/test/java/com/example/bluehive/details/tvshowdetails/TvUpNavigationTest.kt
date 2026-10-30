package com.example.bluehive.details.tvshowdetails

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v525, the user's map of Up on the TV page: the recommended row -> the
 * season picker; the season picker -> Add to Favorites; any episode -> Search.
 */
class TvUpNavigationTest {

    @Test
    fun `up from the row tries the season picker first, then the episode, then Play Trailer`() {
        assertEquals(listOf("picker", "episode 4", "trailer"), aboveRowOrder("picker", "episode 4", "trailer"))
        // No episode on screen: the picker, then the trailer.
        assertEquals(listOf("picker", "trailer"), aboveRowOrder("picker", null, "trailer"))
    }

    @Test
    fun `the row's Up uses that order`() {
        val map = MainSource.code("details/tvshowdetails/TvFocusMap.kt")
        assertTrue(map.contains("val targets = aboveRowOrder(seasonPicker, "))
        val screen = MainSource.code("details/tvshowdetails/TvShowDetailsScreen.kt")
        assertTrue(screen.contains("focusMap.focusAboveRow(episodesState)"))
    }

    @Test
    fun `up from the season picker lands on Add to Favorites`() {
        val picker = MainSource.code("details/tvshowdetails/SeasonAccordion.kt")
        assertTrue(picker.contains("Key.DirectionUp -> focusWithSound(focusMap.favorite)"))
    }

    @Test
    fun `up from any episode lands on Search`() {
        val row = MainSource.code("details/tvshowdetails/EpisodeRow.kt")
        assertTrue(row.contains("Key.DirectionUp -> focusWithSound(focusMap.search)"))
        // The header's Search button is the one the focus map points at.
        val screen = MainSource.code("details/tvshowdetails/TvShowDetailsScreen.kt")
        assertTrue(screen.contains("searchFocusRequester   = focusMap.search,"))
        val header = MainSource.code("details/shared/layout/DetailsHeaderButtons.kt")
        assertTrue(header.contains("focusRequester = searchFocusRequester ?: remember { FocusRequester() },"))
    }
}
