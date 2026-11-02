package com.example.bluehive.homeScreenSectionRules

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v592: the v572 film-strip test is over (owner, 2026-11-01). The strips cost
 * ~0.87 of a CPU core on an idle home screen (62 full redraws a second) and
 * made the sidebar crawl under its blur. Highest Trending is back to its title
 * and year, with the picture filling the whole screen.
 */
class TrendingNoStripsTest {

    @Test
    fun `no film strips anywhere`() {
        assertFalse(File("src/main/java/com/example/bluehive/homeScreenSectionRules/PanelFilmStrips.kt").exists())
        assertTrue(MainSource.all().values.none { it.contains("PanelFilmStrips") || it.contains("FILM_STRIP_HEIGHT") })
    }

    @Test
    fun `the trending panel shows its title and year, full screen`() {
        val trending = MainSource.code("trendingComponents/HomeScreenTrendingCompose.kt")
        assertTrue(trending.contains("PanelCaption("))
        assertTrue(trending.contains("val viewportW = TRENDING_VIEWPORT_W; val viewportH = TRENDING_VIEWPORT_H"))
    }
}
