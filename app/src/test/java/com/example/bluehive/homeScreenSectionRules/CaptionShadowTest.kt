package com.example.bluehive.homeScreenSectionRules

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.bluehive.testutil.MainSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The panels' caption (title and subtitle) carries a soft dark shadow (v570,
 * option 1 from the owner's mock-ups), so it reads on bright backdrops: in
 * select mode it sits top-right, where the bottom scrim gives it nothing.
 */
class CaptionShadowTest {

    private val caption = MainSource.code("homeScreenSectionRules/PanelHints.kt").substringAfter("fun PanelCaption(")

    @Test
    fun `a near-black shadow a touch below, softly blurred`() {
        assertEquals(Color(0xE6000000), CAPTION_SHADOW_COLOR)
        assertEquals(1.dp, CAPTION_SHADOW_DY)
        assertEquals(4.dp, CAPTION_SHADOW_BLUR)
    }

    @Test
    fun `both lines carry it`() {
        assertEquals(2, Regex("""shadow = shadow\)""").findAll(caption).count())
        assertTrue(caption.contains("Shadow(CAPTION_SHADOW_COLOR, Offset(0f, CAPTION_SHADOW_DY.toPx()), CAPTION_SHADOW_BLUR.toPx())"))
    }
}
