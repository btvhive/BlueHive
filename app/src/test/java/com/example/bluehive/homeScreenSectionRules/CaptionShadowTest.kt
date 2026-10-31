package com.example.bluehive.homeScreenSectionRules

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The panels' caption (title and subtitle) carries a soft dark shadow (v570,
 * option 1 from the owner's mock-ups; strengthened to match it in v571), so it reads on bright backdrops: in
 * select mode it sits top-right, where the bottom scrim gives it nothing.
 */
class CaptionShadowTest {

    private val caption = MainSource.code("homeScreenSectionRules/PanelHints.kt").substringAfter("fun PanelCaption(")

    @Test
    fun `a black shadow a touch below, tightly blurred, stacked twice`() {
        assertEquals(Color(0xFF000000), CAPTION_SHADOW_COLOR)
        assertEquals(1.dp, CAPTION_SHADOW_DY)
        assertEquals(2.5.dp, CAPTION_SHADOW_BLUR)
        assertEquals(2, CAPTION_SHADOW_PASSES)
    }

    @Test
    fun `both lines carry it`() {
        assertEquals(2, Regex("""shadow = shadow\)""").findAll(caption).count())
        assertEquals(2, Regex("""ShadowedLine\(\s*text\s*=""").findAll(caption).count())
        val line = caption.substringAfter("private fun ShadowedLine(")
        assertTrue(line.contains("repeat(CAPTION_SHADOW_PASSES)"))
        assertTrue(caption.contains("Shadow(CAPTION_SHADOW_COLOR, Offset(0f, CAPTION_SHADOW_DY.toPx()), CAPTION_SHADOW_BLUR.toPx())"))
    }
}
