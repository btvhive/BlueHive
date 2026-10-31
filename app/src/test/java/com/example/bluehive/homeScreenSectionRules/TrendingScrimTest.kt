package com.example.bluehive.homeScreenSectionRules

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertTrue
import org.junit.Test

/** The trending backdrop's dark overlay ends at 40% black (v573; 80% before). */
class TrendingScrimTest {

    @Test
    fun `the trending overlay runs from clear to 40 percent black`() {
        val code = MainSource.code("trendingComponents/HomeScreenTrendingCompose.kt")
        assertTrue(code.contains("colors = listOf(Color.Transparent, Color(0x66000000))"))
        // The trailers panel keeps its own 60%.
        val trailers = MainSource.code("latestTrailersComponents/HomeScreenTrailerAdapterCompose.kt")
        assertTrue(trailers.contains("colors = listOf(Color.Transparent, Color(0x99000000))"))
    }
}
