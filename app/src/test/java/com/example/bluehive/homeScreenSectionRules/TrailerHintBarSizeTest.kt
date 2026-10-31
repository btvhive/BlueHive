package com.example.bluehive.homeScreenSectionRules

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gold hint bar (Browse | Click to play | Exit) is 30% smaller on the
 * Latest Trailers panel (v569, the owner's call); trending keeps full size.
 */
class TrailerHintBarSizeTest {

    private val hints = MainSource.code("homeScreenSectionRules/PanelHints.kt")

    @Test
    fun `the trailer panel draws the bar at 70 percent, trending at full size`() {
        assertTrue(MainSource.code("latestTrailersComponents/HomeScreenTrailerSectionCompose.kt").contains("legendScale  = 0.7f,"))
        assertTrue(!MainSource.code("trendingComponents/HomeScreenTrendingCompose.kt").contains("legendScale"))
        assertTrue(hints.contains("legendScale: Float = 1f,"))
    }

    @Test
    fun `it shrinks toward its bottom centre, so it keeps its place`() {
        val bar = hints.substringAfter("visible  = legend,").substringBefore("HintChip(edge = PANEL_HINT_GOLD")
        assertTrue(bar.contains("scaleX = legendScale; scaleY = legendScale"))
        assertTrue(bar.contains("transformOrigin = TransformOrigin(0.5f, 1f)"))
    }
}
