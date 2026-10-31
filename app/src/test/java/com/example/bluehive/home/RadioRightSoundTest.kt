package com.example.bluehive.home

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RIGHT off the end of the radio row lands on Continue Watching, which makes
 * no sound when focus arrives, so the radio button plays the move itself
 * (owner, 2026-10-31). RIGHT onto another radio button stays silent here: that
 * button announces its own arrival.
 */
class RadioRightSoundTest {

    private val code = MainSource.code("home/CatalogTabs.kt")

    @Test
    fun `TV SHOWS and ANIME play the move sound when RIGHT leaves for Continue Watching`() {
        val right = Regex("""Key\.DirectionRight -> \{\s*if \(rightLeavesRow\) BlueHiveApplication\.playNavClickSound\(\)\s*false\s*\}""")
        assertEquals(2, right.findAll(code).count())
        val tv = code.substringAfter("internal fun TvShowsRadioButton(").substringBefore("internal fun AnimeRadioButton(")
        val anime = code.substringAfter("internal fun AnimeRadioButton(")
        assertTrue(right.containsMatchIn(tv))
        assertTrue(right.containsMatchIn(anime))
    }

    @Test
    fun `only the moves that really land on Continue Watching count`() {
        // TV SHOWS reaches it only when ANIME is the selected (unfocusable) tab.
        assertTrue(code.contains("rightLeavesRow = tabs.selectedTab == CatalogTab.ANIME && shelves.continueWatchingShown(entry),"))
        // ANIME always does, when the panel is there at all.
        assertTrue(code.contains("rightLeavesRow = shelves.continueWatchingShown(entry),"))
        // MOVIES' RIGHT always lands on a radio button, which speaks for itself.
        val movies = code.substringAfter("internal fun MoviesRadioButton(").substringBefore("internal fun TvShowsRadioButton(")
        assertTrue(Regex("""Key\.DirectionLeft,\s*Key\.DirectionRight -> false""").containsMatchIn(movies))
    }
}
