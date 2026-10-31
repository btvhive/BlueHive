package com.example.bluehive.homeScreenSectionRules

import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Continue Watching (owner, 2026-10-31): no shadow layer over the gold
 * underglow while Selected, as on the trailer and trending panels, and the
 * focused row is lifted charcoal in a bright white border.
 */
class ContinueWatchingLookTest {

    private val code = MainSource.code("continueWatchingComponents/ContinueWatchingFrame.kt")

    @Test
    fun `no shadow is drawn over the frame, and its art is gone`() {
        assertFalse(code.contains("_frame_shadow"))
        assertFalse(Regex("""\bshadow[WHXYZ]\b""").containsMatchIn(code))
        assertTrue(code.contains("PANEL_SELECTED_TINT"))
        val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val left = res.walkTopDown().filter { it.name.startsWith("continue_watching_frame_shadow.") }.toList()
        assertTrue(left.toString(), left.isEmpty())
    }

    @Test
    fun `the focused row is lifted charcoal in a bright white border, text left white`() {
        // Option D from mock-ups (v544); the season tile's white before.
        assertTrue(code.contains("private val CW_BG_FOCUSED       = Color(0xFF34363F)"))
        assertTrue(code.contains("private val CW_BORDER_FOCUSED   = Color.White"))
        assertTrue(code.contains("private val CW_TITLE_FOCUSED    = Color.White"))
        assertTrue(code.contains(".background(if (isFocused) CW_BG_FOCUSED else CW_BG_COLOR)"))
        assertTrue(code.contains("width = if (isFocused) CW_BORDER_FOCUSED_W else 1.dp"))
        assertTrue(code.contains("color = if (isFocused) CW_BORDER_FOCUSED else CW_BORDER_UNFOCUSED"))
        // The subtitle keeps its off-white on the dark row.
        assertTrue(code.contains("color      = CW_SUBTITLE_COLOR,"))
        for (gone in listOf("0xFFC9CACF", "0xFFD9D9DE", "0xFF2D58AA", "CW_SUBTITLE_FOCUSED")) assertFalse(gone, code.contains(gone))
    }

    @Test
    fun `the season and episode line is Inter Bold at 6sp under the Passion title`() {
        // The owner's pick (no. 2), 2026-10-31; 1sp smaller since v563.
        assertTrue(code.contains("private val CW_SUBTITLE_SIZE    = 6.sp"))
        val subtitle = code.substringAfter("text       = subtitle,").substringBefore(")")
        assertTrue(subtitle.contains("fontSize   = CW_SUBTITLE_SIZE,"))
        assertTrue(subtitle.contains("fontFamily = AppTypography.interBold,"))
        val title = code.substringAfter("text       = item.media_title ?: \"\",").substringBefore("maxLines")
        assertTrue(title.contains("fontFamily = AppTypography.passionRegular,"))
    }
}
