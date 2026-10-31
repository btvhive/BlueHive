package com.example.bluehive.homeScreenSectionRules

import androidx.compose.ui.graphics.Color
import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three home panels' focus frames turn the side bar's selected blue while
 * entered (v541; gold before), so "inside" reads the same in the menu and the
 * panels (owner, 2026-10-31).
 */
class PanelSelectedTintTest {

    @Test
    fun `the selected tint is the bright end of the side bar's highlight`() {
        assertEquals(Color(0xFF2563EB), PANEL_SELECTED_TINT)
        val sidebar = MainSource.code("sidebarComponents/SidebarDisplay.kt")
        assertTrue(sidebar.contains("Color(0xFF2563EB)"))
    }

    @Test
    fun `all three panels tint their frame with it while Selected`() {
        val panels = mapOf(
            "latestTrailersComponents/HomeScreenTrailerSectionCompose.kt" to "TrailerFrameState.Selected",
            "trendingComponents/HomeScreenTrendingCompose.kt" to "TrendingFrameState.Selected",
            "continueWatchingComponents/ContinueWatchingFrame.kt" to "ContinueWatchingFrameState.Selected",
        )
        for ((path, selected) in panels) {
            val code = MainSource.code(path)
            assertTrue(path, code.contains("is $selected) PANEL_SELECTED_TINT else Color.White"))
            assertFalse(path, code.contains("PANEL_HINT_GOLD"))
        }
    }
}
