package com.example.bluehive.homeScreenSectionRules

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Continue Watching (owner, 2026-10-31): no shadow layer over the gold
 * underglow while Selected, as on the trailer and trending panels, and the
 * focused row is the season tile's white in a bright white border.
 */
class ContinueWatchingLookTest {

    private val code = MainSource.code("continueWatchingComponents/ContinueWatchingFrame.kt")

    @Test
    fun `no shadow is drawn over the frame, and its art is gone`() {
        assertFalse(code.contains("_frame_shadow"))
        assertFalse(Regex("""\bshadow[WHXYZ]\b""").containsMatchIn(code))
        assertTrue(code.contains("PANEL_HINT_GOLD"))
        val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val left = res.walkTopDown().filter { it.name.startsWith("continue_watching_frame_shadow.") }.toList()
        assertTrue(left.toString(), left.isEmpty())
    }

    @Test
    fun `the focused row is the season tile's white in a bright white border`() {
        // SeasonAccordion's DULL_TILE face and ink (option C, 2026-10-31).
        assertTrue(Regex("""CW_BG_FOCUSED\s*=\s*Brush\.verticalGradient\(\s*0f\s+to Color\(0xFFC9CACF\),\s*0\.55f to Color\(0xFFB1B2B9\),\s*1f\s+to Color\(0xFF9D9EA6\),\s*\)""").containsMatchIn(code))
        assertTrue(code.contains("private val CW_BORDER_FOCUSED   = Color.White"))
        assertTrue(code.contains("private val CW_TITLE_FOCUSED    = Color(0xFF0C0C10)"))
        assertTrue(code.contains("private val CW_SUBTITLE_FOCUSED = Color(0xFF24242C)"))
        assertTrue(code.contains("Modifier.background(CW_BG_FOCUSED)"))
        assertFalse(code.contains("0xFFD9D9DE"))
        assertTrue(code.contains("width = if (isFocused) CW_BORDER_FOCUSED_W else 1.dp"))
        assertTrue(code.contains("color = if (isFocused) CW_BORDER_FOCUSED else CW_BORDER_UNFOCUSED"))
        assertTrue(code.contains("color      = if (isFocused) CW_TITLE_FOCUSED else CW_TITLE_DEFAULT"))
        // The subtitle darkens too; its off-white would vanish on the light row.
        assertTrue(code.contains("color      = if (isFocused) CW_SUBTITLE_FOCUSED else CW_SUBTITLE_COLOR"))
        assertFalse(code.contains("0xFF2D58AA"))
    }
}
