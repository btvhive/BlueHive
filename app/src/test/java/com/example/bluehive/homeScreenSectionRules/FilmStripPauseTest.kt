package com.example.bluehive.homeScreenSectionRules

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v590: the sidebar's options crawled at ~7 fps because the trending panel's
 * film strips kept moving under the sidebar's full-screen blur, and every
 * moved hole made the onn box blur the whole home page again (~150 ms a
 * frame). The strips hold still whenever the trending panel is paused.
 */
class FilmStripPauseTest {

    @Test
    fun `the strips can be held still, and are not an endless transition`() {
        val strips = MainSource.code("homeScreenSectionRules/PanelFilmStrips.kt")
        assertTrue(strips.contains("paused: Boolean = false,"))
        assertTrue(strips.contains("if (paused) return@LaunchedEffect"))
        assertFalse("an infinite transition cannot be paused", strips.contains("rememberInfiniteTransition"))
    }

    @Test
    fun `the trending panel holds them while the sidebar is open`() {
        val trending = MainSource.code("trendingComponents/HomeScreenTrendingCompose.kt")
        assertTrue(trending.contains("paused = isPaused)"))
        val frames = MainSource.code("home/HomeTopFrames.kt")
        assertTrue(frames.contains("isPaused = sidebar.isSidebarFocused || !isHomeInForeground,"))
    }
}
