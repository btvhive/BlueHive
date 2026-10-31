package com.example.bluehive.homeScreenSectionRules

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The trailer and trending panels draw no shadow layer: it was shown only
 * while a panel was Selected, right on top of the gold underglow (owner,
 * 2026-10-31). The frame's gold tint still marks Selected.
 */
class PanelNoShadowTest {

    private val panels = listOf(
        "latestTrailersComponents/HomeScreenTrailerSectionCompose.kt",
        "trendingComponents/HomeScreenTrendingCompose.kt",
    )

    @Test
    fun `neither panel draws a shadow over its frame`() {
        for (path in panels) {
            val code = MainSource.code(path)
            assertFalse(path, code.contains("_frame_shadow"))
            assertFalse(path, Regex("""\bshadow[WHXY]\b""").containsMatchIn(code))
            // The gold tint on the focus overlay is what marks Selected now.
            assertTrue(path, code.contains("PANEL_HINT_GOLD"))
        }
    }

    @Test
    fun `the shadow art is gone from the APK`() {
        val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val left = res.walkTopDown().filter { it.name.startsWith("hrc_frame_shadow.") || it.name.startsWith("trailer_frame_shadow.") }.toList()
        assertTrue(left.toString(), left.isEmpty())
    }
}
